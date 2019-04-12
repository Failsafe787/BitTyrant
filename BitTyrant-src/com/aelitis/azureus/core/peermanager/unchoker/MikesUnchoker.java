package com.aelitis.azureus.core.peermanager.unchoker;

import java.util.ArrayList;

import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;
import java.util.*;

public class MikesUnchoker extends GlobalTorrentAwareDownloadingUnchoker {

	//varis from piatek's code
	boolean excessMode = false;
	
	public MikesUnchoker(){
		//nop
	}
	
	@Override
	public ChokesAndUnchokes calculateChokesAndUnchokes(ArrayList all_peers, long uploadBandwidthToAllocate) {
		//shadow chokes and unchokes with arraylists that represent global views.  calculateUnchokes will intersect the global views with a per-torrent view to figure out per-torrent chokes and unchokes
		ArrayList chokes = new ArrayList();
		ArrayList unchokes = new ArrayList();
		
		//bail out early if there's no work
		if( all_peers.size() == 0 )
				return new ChokesAndUnchokes(chokes, unchokes);
			
			
			// find global bandwidth that we have to distribute
			int upload_cap = getUploadCap();
			
			System.out.println("upload cap is " + upload_cap + " kb/s");
			
			// update the market rate estimate
			int total_failures = 0, total_candidates = 0;
			double upload_sum = 0;
			for( PEPeerTransport p : (List<PEPeerTransport>)all_peers )
			{
				if( p.isChokingMe() && p.isChokedByMe() == false )
					total_failures++;
				
				if( p.isChokedByMe() == false )
				{
					total_candidates++;
					upload_sum += ((PEPeerTransportProtocol)p).getTyrantStats().getUploadCost();
				}
			}
			
			if( total_candidates > 2 )
				market_rate = upload_sum / (double)total_candidates;
			else
				market_rate = 3; //TODO why does this default to 3?
			
			System.out.println("failures: " + total_failures + " / candidates: " + total_candidates );
			
			// determine if we are in a peer constrained world -- i.e. we have way more capacity than these peers can contend for
			double offeredTotal = 0;
			for( PEPeerTransport p : (List<PEPeerTransport>)all_peers )
			{
				if( p.isChokedByMe() == false )
					offeredTotal += p.getTyrantStats().getUploadCost();
			}
			//TODO if we're peer constrained, we could just give away the excess bandwidth
			
			if( excessMode )
			{
				System.out.println("We are in excess mode. offered total: " + offeredTotal + " / upload_cap: " + upload_cap );		
				
			}
				
			if( market_rate < 1 )
				market_rate = 1;
			
			System.out.println("market rate (post update): " + market_rate);
			
			/**
			 * sort peers by their download / upload ratios. 
			 * 
			 * ratio
			 * 	> 1 -> altruism favoring us
			 *  ~ 1 -> balanced
			 *  < 1 -> altruism favoring remote peer
			 */
			
			PEPeer [] peerArr = new PEPeer[all_peers.size()];
			int pItr =0;
			for( Object o : all_peers )
			{
				peerArr[pItr] = (PEPeer)all_peers.get(pItr);
				pItr++;
			} // for some reason PEPeer[] casting throws a classcast exception...
			Arrays.sort(peerArr, new Comparator<PEPeer>() { 
				public int compare( PEPeer o1, PEPeer o2 )
				{
					double diff = o1.getTyrantStats().ratio(market_rate) - o2.getTyrantStats().ratio(market_rate);
					// the signs here are flipped so we can have descending order
					if( diff > 0 )
						return -1;
					if( diff < 0 )
						return 1;
					return 0;
				}
			});
			
			/**
			 * First pass - who has good ratios?
			 */
			double uploadSum = 0; //the amount of bandwidth we've commited globally
			
			for( int index=0; index<peerArr.length; index++ )
			{
				double offered_rate = peerArr[index].getTyrantStats().getUploadCost();
				if( offered_rate == 0 )
					offered_rate = market_rate;
				
				// skip connections with current info that we expect to be altruistic. we'll throw these into the optimistic pool later
				if( peerArr[index].getTyrantStats().hasObservedInfo() && peerArr[index].getTyrantStats().ratio(market_rate) < 1 )
					continue;
				
				// can we accomodate this peer? is it unchokable?
				if( uploadSum + offered_rate < upload_cap && peerArr[index].isInteresting() && UnchokerUtil.isUnchokable(((PEPeerTransport)peerArr[index]), false) )
				{
					unchokes.add(peerArr[index]);
					uploadSum += offered_rate;
					peerArr[index].getTyrantStats().updateOfferedRate(offered_rate);
					
					peerArr[index].setOptimisticUnchoke(false);
				}
			}
			
			System.out.println("first pass uploadSum: " + uploadSum);
			
			/**
			 * Second pass - who are we interested in? this also incorporates the sort by ratio, and we expect to add most peers here 
			 * since we are presumably high capacity.
			 */
			for( int index=0; index<peerArr.length; index++ )
			{
				// already unchoked
				if( unchokes.contains(peerArr[index]) )
					continue;
				
				double offered_rate = peerArr[index].getTyrantStats().getUploadCost();
				if( offered_rate == 0 )
					offered_rate = market_rate;
				
				if( uploadSum + offered_rate < upload_cap && peerArr[index].isInteresting() && UnchokerUtil.isUnchokable(((PEPeerTransport)peerArr[index]), false) )
				{
					unchokes.add(peerArr[index]);
					uploadSum += offered_rate;
					peerArr[index].getTyrantStats().updateOfferedRate(offered_rate);
					
					peerArr[index].setOptimisticUnchoke(true);
				}
			}

			System.out.println("second pass uploadSum: " + uploadSum);
			
			/** 
			 * Final pass - whose information is least current?
			 */
			
			// first we need to sort by the last time our observed download from this guy was received. since we aren't interested in these people, we
			// don't expect to necessarily receive reciprocation for our contribution
			Arrays.sort(peerArr, new Comparator<PEPeer>() { 
				public int compare( PEPeer o1, PEPeer o2 )
				{
					// ascending order --- oldest at the front of the array
					return (int)(o1.getTyrantStats().getLastDownloadUpdate() - o2.getTyrantStats().getLastDownloadUpdate());
				}
			});
			
			for( int index=0; index<peerArr.length; index++ )
			{
				// already unchoked
				if( unchokes.contains(peerArr[index]) )
					continue;
				
				double offered_rate = peerArr[index].getTyrantStats().getUploadCost();
				if( offered_rate == 0 )
					offered_rate = market_rate;
				
				// now allow snubbed, too.
				if( uploadSum + offered_rate < upload_cap && UnchokerUtil.isUnchokable(((PEPeerTransport)peerArr[index]), true) )
				{
					unchokes.add(peerArr[index]);
					uploadSum += offered_rate;
					
					// we aren't necessarily interested here, so don't update the time or offered rate of this guy. the best we can do here is 
					// update the download rate...
					//peerArr[index].getTyrantStats().updateOfferedRate(offered_rate);
					
					peerArr[index].setOptimisticUnchoke(true);
				}
			}
			

			System.out.println("final pass uploadSum: " + uploadSum);
			System.out.println("final pass unchoked peer count: " + unchokes.size());
			
			excessMode = true;
			/**
			 * Now everyone who is not unchoked is choked.
			 */
			for( PEPeerTransportProtocol p : (List<PEPeerTransportProtocol>)all_peers )
			{
				if( unchokes.contains(p) == false )
				{
					chokes.add(p);
					//clear out the bandwidth limit
					// note: Since this peer won't be sending any data payload, it's sitting in a multi peer uploader that ignores whatever limit we set here!
					//if(p.getNetworkConnection() != null){
					//	p.getNetworkConnection().setBandwidth(500);
					//}
					
					// if we COULD have added this but didn't, we are not in excess mode.
					if( UnchokerUtil.isUnchokable(p, true) )
						excessMode = false;
				}
			}
			
			
			return new ChokesAndUnchokes(chokes, unchokes);
	}
	

}
