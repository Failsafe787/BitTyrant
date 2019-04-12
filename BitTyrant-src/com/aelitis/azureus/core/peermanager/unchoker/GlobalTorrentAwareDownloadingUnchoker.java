/**
 * An unchoker that is globally aware of the performance of other peers
 * in other swarms.  Allocates bandwidth in an attempt to maximize global performance.
 * @author Jarret jar@cs.washington.edu
 */
package com.aelitis.azureus.core.peermanager.unchoker;

import java.util.*;

import javax.swing.JOptionPane;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.TyrantStats;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;

public abstract class GlobalTorrentAwareDownloadingUnchoker implements Unchoker {
	// all peers in the application, and a timestamp of when the peer was last
	// seen
	// this info is just inferred as individual torrents pass their peers
	// through this unchoker
	//TODO get peers from the peer manager?
	private static Map<PEPeerTransport, Long> globalPeers = Collections
			.synchronizedMap(new HashMap());

	// int globalMaxUnchokes = 10; //should be replaced with estimate of dl
	// speed

	protected double market_rate = 7;

	public static final double UPLOAD_RATE_INCREMENT = 0.2;

	private ArrayList chokes = new ArrayList();

	private ArrayList unchokes = new ArrayList();
	static {
		// make a thread that knows about global peers, and removes old ones so
		// they can GC
		Thread remover = new Thread() {
			long maxInactivityBeforeRemoval = 30 * 1000; // 30sec before

			// expiring dead
			// peers

			public void run() {
				while (true) {
					// System.out.println("global peer remover running");
					if (globalPeers.size() > 0) {
						// check if any old ones need to be dropped
						Set<PEPeerTransport> k = globalPeers.keySet();
						synchronized (globalPeers) {
							long now = System.currentTimeMillis();
							for (Iterator<PEPeerTransport> pit = k.iterator(); pit
									.hasNext();) {
								PEPeerTransport current = pit.next();
								if (now - globalPeers.get(current) > maxInactivityBeforeRemoval) {
									pit.remove();
									if (Logger.isEnabled())
										Logger.log(new LogEvent(this, logging,
												"gpr: removed inactive peer: "
														+ current));

								}
							}
						}
					}

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		remover.setDaemon(true);
		remover.start();
	}

	public GlobalTorrentAwareDownloadingUnchoker() {
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, logging,
					"using global torrent unchoker"));
		System.out.println("construcuted gtadu");
	}

	/**
	 * Utility method to find global upload limit in kilobytes per second
	 */
	protected int getUploadCap() {
		int upload_cap = (int) COConfigurationManager
				.getIntParameter("Max Upload Speed KBs");

		if (upload_cap == 0)
			upload_cap = 40;

		return upload_cap;
		// return 11;
	}

	private int getUploadCapForTheNextTenSecondsInBytes() {
		return getUploadCap() * 10 * 1024;
	}

	public void calculateUnchokes(int max_to_unchoke, ArrayList all_peers,
			boolean force_refresh) {
		// remember that these peers are still active
		updateGlobalMap(all_peers);

		// whats our bandwidth to play with?
		int bandwidthToAllocate = getUploadCapForTheNextTenSecondsInBytes();

		// this is mike's original implementation

		//
		// force refresh means dump existing optimistic unchokes. this happens
		// in the default azureus tracker
		// every 30 seconds whereas this function is called every 10 (for
		// cycling among TFT unchokes). we ignore this, since
		// we don't do optimistic unchokes in the traditional sense.
		//

		if (this instanceof MikesUnchoker) {
			// update all the legacy stats and costs for just the peers given in
			// this call
			for (PEPeerTransport p : (List<PEPeerTransport>) all_peers) {
				if (p.isChokingMe())
					p.getTyrantStats().updateLastChokedTime();

				if (p.isChokingMe() == false && p.isInteresting())
					p.getTyrantStats().updateDownloadRate();

				// last rate wasn't sufficient to achieve reciprocation
				if (p.isChokedByMe() == false && p.isChokingMe() == true
						&& p.isInteresting())
					p.getTyrantStats().updateOfferedRate(
							p.getTyrantStats().getUploadCost()
									* (1 + UPLOAD_RATE_INCREMENT));

				// last rate was higher than the average and we've been unchoked
				// by this guy for a while....
				if (p.isChokingMe() == false
						&& p.isChokedByMe() == false
						&& p.getTyrantStats().lastChoked() + 30 * 1000 < System
								.currentTimeMillis())
					p.getTyrantStats().updateOfferedRate(
							p.getTyrantStats().getUploadCost() * (0.9));
			}
		}

		// promptly ignore max_to_unchoke. we'll just keep unchoke connections
		// that are highly rated until we reach our upload_cap
		ChokesAndUnchokes global = calculateChokesAndUnchokes(new ArrayList(
				globalPeers.keySet()), bandwidthToAllocate);
		// translate global view of unchokes to this torrent
		for (PEPeerTransportProtocol p : global.unchokes) {
			if (all_peers.contains(p))
				unchokes.add(p);
		}
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, logging, global.unchokes.size()
					+ " peers were unchoked globally, " + unchokes.size()
					+ " peers unchoked in this torrent"));

		// translate global view of chokes
		for (PEPeerTransportProtocol p : global.chokes) {
			if (all_peers.contains(p))
				chokes.add(p);
		}
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, logging, global.chokes.size()
					+ " peers were choked globally, " + chokes.size()
					+ " peers choked in this torrent"));

		//allocate upload bandwidth
		//special case for mike's legacty stuff
		if (this instanceof MikesUnchoker) {
			//
			// Now make connection weights reflect offered rates for unchoked
			// peers
			//
			for (PEPeerTransportProtocol p : (List<PEPeerTransportProtocol>) unchokes) {
				// scale up by number of unchoked peers to make a newly unchoked
				// peer with weight 1 sensible.
				double rate = p.getTyrantStats().getUploadCost() == 0 ? market_rate
						: p.getTyrantStats().getUploadCost();

				// set that connection specific cap
				rate *= (1024 * 10); // kilobytes -> bytes, and we need to
				// allocate for the next 10 sec
				rate = Math.max(1, rate); // avoid negative rates

				// allocate
				// note: unchoked peers are rate limited by a SinglePeerUploader
				p.getNetworkConnection().setBandwidth((int) rate);
				if (p.isSeed())
					p.getNetworkConnection().setBandwidth(Integer.MAX_VALUE);
				System.out.println("unchoked peer " + p + " got " + (int) rate
						+ " bytes for the next 10 sec");
			}
		} else {
			// everyone else should have set lastuploadallocation
			for(PEPeerTransportProtocol p : (List<PEPeerTransportProtocol>)unchokes){
				if(p.getNetworkConnection() != null)
					p.getNetworkConnection().setBandwidth((int) p.getTyrantStats().getLastUploadAllocation());
			}
		}

		// because of the choked peer -> unchoked upgrade from a multi peer
		// uploader to a single peer uploader,
		// we need to bootstrap each choked peer a small amount of bandwidth so
		// we can send an interested message
		// otherwise, requests to get pieces are never sent and we don't
		// download anything. At all. Not even from seeds.
		// At most, this over-subscribes us by 1k per choked peer. As soon as
		// the peer is unchoked, the 1k actually comes
		// from the global pool, so oversubscription is bounded by 1k*number of
		// unchokes in the next 10 sec
		for (PEPeerTransportProtocol p : (List<PEPeerTransportProtocol>) chokes) {
			if (p.getNetworkConnection() != null)
				p.getNetworkConnection().setBandwidth(5000);
		}


	}

	/**
	 * Implementation specific method that determines application wide chokes and unchokes.
	 * Breaking down the application wide chokes and unchokes to each individual torrent is handled for you. 
	 * 
	 * Each peer in the unchokes list will be limited to peer.getTyrantStats().getLastUploadAllocation() bytes of upload in the next 10 seconds.
	 * Implementations MUST set the upload allocation of each peer.  
	 * Implementations are responsible for respecting the limit of uploadBandwidthToAllocate.
	 * 
	 * This method may be called at any time and there is no guarantee of the length of time between calls.
	 * Specifically, do not count on there being 10 seconds between each call of calculateChokesAndUnchokes
	 * 
	 * @param all_peers a list of all peers in all active torrents.  Can be cast to PEPeer or PEPeerProtocol
	 * @param uploadBandwidthToAllocate the limit of upload bytes in the next 10 seconds
	 * @return which peers to choke and unchoke throughout the application
	 */
	public abstract ChokesAndUnchokes calculateChokesAndUnchokes(
			ArrayList all_peers, long uploadBandwidthToAllocate);

	public ArrayList getImmediateUnchokes(int max_to_unchoke,
			ArrayList all_peers) {
		// keep these peers active
		updateGlobalMap(all_peers);

		/**
		 * This function is called every second to compensate for
		 * disconnections, etc.
		 */
		/*
		 * How much of a performance hit is it really to just ditch this method?
		 * 
		 * ArrayList<PEPeer> to_unchoke = new ArrayList<PEPeer>();
		 * 
		 * double upload_cap = (double)getUploadCap(); // get the total sum of
		 * offered bandwidth to all peers double uploadSum = 0.0; for( PEPeer p :
		 * (ArrayList<PEPeer>)all_peers ) if( p.isChokedByMe() == false )
		 * uploadSum += p.getTyrantStats().getUploadCost(); // now add some
		 * random peers ArrayList<PEPeer> shuffled = (ArrayList<PEPeer>)all_peers.clone();
		 * Collections.shuffle(shuffled);
		 * 
		 * for( PEPeer p : shuffled ) { double offered_rate =
		 * p.getTyrantStats().getUploadCost(); if( offered_rate == 0 )
		 * offered_rate = market_rate;
		 * 
		 * if( uploadSum + offered_rate < upload_cap &&
		 * UnchokerUtil.isUnchokable((PEPeerTransport)p, true) ) {
		 * to_unchoke.add(p); } }
		 * 
		 * if (Logger.isEnabled()) Logger.log(new LogEvent(this, logging,
		 * "global torrent unchoker immed unchokes: " + to_unchoke)); return
		 * to_unchoke; //return super.getImmediateUnchokes(max_to_unchoke,
		 * all_peers);
		 * 
		 */
		return new ArrayList();
	}

	private void updateGlobalMap(ArrayList all_peers) {
		// dump all peers into the global list
		for (int i = 0; i < all_peers.size(); i++) {
			PEPeerTransport peer = (PEPeerTransport) all_peers.get(i);
			globalPeers.put(peer, System.currentTimeMillis());
		}
	}

	public ArrayList getChokes() {
		// meh about this style
		ArrayList toReturn = chokes;
		chokes = new ArrayList();
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, logging,
					"global torrent unchoker chokes: " + toReturn));
		return toReturn;
	}

	public ArrayList getUnchokes() {
		ArrayList toReturn = unchokes;
		unchokes = new ArrayList();
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, logging,
					"global torrent unchoker unchokes: " + toReturn));
		return toReturn;
	}

	class ChokesAndUnchokes {
		public ArrayList<PEPeerTransportProtocol> chokes;

		public ArrayList<PEPeerTransportProtocol> unchokes;

		public ChokesAndUnchokes(ArrayList<PEPeerTransportProtocol> chokes,
				ArrayList<PEPeerTransportProtocol> unchokes) {
			this.chokes = chokes;
			this.unchokes = unchokes;
		}
	}
}
