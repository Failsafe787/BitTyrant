package com.aelitis.azureus.core.peermanager.unchoker;

import java.util.*;

import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;

/**
 * 
 * An unchoker that tries to learn reciprication thresholds of naive peers to
 * maximize download to upload
 * 
 * @author Jarret
 * 
 */
public class JarUnchoker extends GlobalTorrentAwareDownloadingUnchoker {

	long lastTotalAllocation = 0;

	public JarUnchoker() {
	}

	/**
	 * What should we do if we find ourselves in a situation where we've
	 * allocated enough bandwidth to everyone to get reciprication, but we still
	 * have more upload capacity? Set to true to help the swarm by evenly
	 * distributing any leftover bandwidth amungst all unchoked peers.
	 */
	public static boolean EVENLY_ALLOCATE_EXCESS_BANDIWDTH = true;

	// what do we say is the least acceptable amount to give to a peer?
	// this is number of bytes per a 10 second allocation
	public static long ABSOLUTE_MINIMUM_ALLOCATION_PER_PEER = 30000;

	private static ChokesAndUnchokes cache = null;

	private static long lastCalled = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.aelitis.azureus.core.peermanager.unchoker.GlobalTorrentAwareDownloadingUnchoker#calculateChokesAndUnchokes(java.util.ArrayList,
	 *      long)
	 */

	@Override
	public ChokesAndUnchokes calculateChokesAndUnchokes(ArrayList all_peers, long uploadBandwidthToAllocate) {
		// we're caching results because will be called multiple times each 10
		// sec if more than 1 torrent is downloading
		if (cache != null && lastCalled + 5000 > System.currentTimeMillis()) {
			System.out.println("hit cache");
			return cache;
		}

		// long realLimit = uploadBandwidthToAllocate; //keep this limit around
		// because we're going to mess with uploadBandwidthToAllocate
		ArrayList chokes = new ArrayList();
		ArrayList unchokes = new ArrayList();

		System.out.println("allocating " + uploadBandwidthToAllocate + " bytes for the next 10 sec");

		// figure out how much we used last round
		long unusedBandwidthLastRound = 0;
		long allocatedBandwidthLastRound = 0;
		long unusedBandwidthFromFlippedPeers = 0;
		int numUnchoked = 0;
		int numPeersThatFlipped = 0;
		for (PEPeerTransportProtocol pet : (List<PEPeerTransportProtocol>) all_peers) {
			if (!pet.isChokedByMe()) {
				unusedBandwidthLastRound += pet.getNetworkConnection().getBandwidthLimit();
				allocatedBandwidthLastRound += pet.getTyrantStats().getLastUploadAllocation();
				numUnchoked++;
				if (!pet.isInterested()) {
					numPeersThatFlipped++;
					unusedBandwidthFromFlippedPeers += pet.getNetworkConnection().getBandwidthLimit();
				}
			}
		}
		double percent = (double) unusedBandwidthLastRound / allocatedBandwidthLastRound;
		System.out.println("Allocation percent unused last round: " + percent);
		System.out.println(numPeersThatFlipped + " peers out of " + numUnchoked + " peers went from interested to uninterested in the last round");
		System.out.println(unusedBandwidthFromFlippedPeers + " bytes were left allocated to the peers that flipped");

		int fallbackAllocation = 120000; // that's 3k/sec
		// similar to market rate... what to give peers if we don't know

		// put at the front of the list who we think is the best to download
		// from
		Collections.sort((List<PEPeerTransport>) all_peers);

		// do a pass to calculate penalties for people that didn't take all of
		// their bandwidth off the socket
		for (int apit = 0; apit < all_peers.size() && uploadBandwidthToAllocate > 0; apit++) {
			PEPeerTransport pep = (PEPeerTransport) all_peers.get(apit);

			if (UnchokerUtil.isUnchokable(pep, true) && !pep.isChokedByMe()) {
				long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
				if (toAllocate == 0)
					toAllocate = fallbackAllocation; // we didn't have any
				// info

				// recoup bandwidth that peers don't take off their end of the
				// socket
				long penalty = Math.max(0, ((PEPeerTransportProtocol) pep).getNetworkConnection().getBandwidthLimit() / 3);
				toAllocate -= penalty;
				System.out.println(pep + " just lost " + penalty + " bytes because it didn't pull fast enough");

				toAllocate = Math.max(toAllocate, ABSOLUTE_MINIMUM_ALLOCATION_PER_PEER); // avoid
																							// starving

				// make a note of our applied penalties
				pep.getTyrantStats().setLastUploadAllocation(toAllocate);
			}
		}

		// update histories of everyone
		for (PEPeerTransportProtocol pep : (List<PEPeerTransportProtocol>) all_peers) {
			if (pep.getStats() != null) {
				long sent = pep.getStats().getTotalDataBytesSent();
				long received = pep.getStats().getTotalDataBytesReceived();
				pep.getTyrantStats().updateHistory(new HistoricalDataPoint(sent, received));
			}
		}

		// now, we're going to run the same unchoker twice... for snubbed and
		// then unsnubbed peers
		for (boolean allow_snubbed : new Boolean[] { false, true }) {
			// do a pass to retain any peers that are recently sending more to
			// us than we send to them
			Iterator pit = all_peers.iterator();
			while (pit.hasNext() && uploadBandwidthToAllocate > 0) {
				PEPeerTransportProtocol pep = (PEPeerTransportProtocol) pit.next();

				// are they currently giving us more than we give them?
				if (UnchokerUtil.isUnchokable(pep, allow_snubbed) &&
				// pep.getStats() != null &&
						// pep.getStats().getDataReceiveRate() >
						// pep.getStats().getDataSendRate()) {
						pep.getTyrantStats().getDataBytesReceivedInTheLast(60) > pep.getTyrantStats().getDataBytesSentInTheLast(60) && !pep.isSelfishTyrantPeer()) {
					// keep them
					long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
					if (toAllocate == 0)
						toAllocate = fallbackAllocation; // they
															// optimistically
															// unchoked us?

					toAllocate *= .98;
					((PEPeerTransportProtocol) pep).getTyrantStats().setLastUploadAllocation((int) toAllocate);
					uploadBandwidthToAllocate -= toAllocate;
					System.out.println("kept " + pep + " because they were sending us more than we sent them");
					pep.getTyrantStats().setLastTyrantAction("Decreased upload allocation because of recent upload download ratio.");

					// assign and print
					pep.getNetworkConnection().setBandwidth((int) toAllocate);
					System.out.println(pep + " got " + toAllocate + " bytes for the next 10 sec");

					unchokes.add(pep);
					pit.remove();
				}
			}
			// drain iterator
			while (pit.hasNext())
				pit.next();

			// do a pass where we think we're in their active set... but their
			// upload capacity is maxed
			// for example, they're sending at least 1.5k/sec, but no matter how
			// much we upgrade, they can't send more
			pit = all_peers.iterator();
			while (pit.hasNext() && uploadBandwidthToAllocate > 0) {
				PEPeerTransportProtocol pep = (PEPeerTransportProtocol) pit.next();

				// sending to us, but can't send any more?
				if (UnchokerUtil.isUnchokable(pep, allow_snubbed) && pep.getTyrantStats().getDataBytesReceivedInTheLast(60) < pep.getTyrantStats().getDataBytesSentInTheLast(60)
						&& pep.getTyrantStats().getDataBytesReceivedInTheLast(60) > 1500 * 60 && !pep.isSelfishTyrantPeer()) {
					// keep them
					long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
					if (toAllocate == 0)
						toAllocate = fallbackAllocation; // bootstrap

					toAllocate *= .98;
					((PEPeerTransportProtocol) pep).getTyrantStats().setLastUploadAllocation((int) toAllocate);
					uploadBandwidthToAllocate -= toAllocate;
					System.out.println("kept " + pep + " because they were sending us data and their upload might be maxed");
					pep.getTyrantStats().setLastTyrantAction("Decreased upload allocation because we might have found their max upload rate.");

					pep.getNetworkConnection().setBandwidth((int) toAllocate);
					System.out.println(pep + " got " + toAllocate + " bytes for the next 10 sec");

					unchokes.add(pep);
					pit.remove();
				}
			}
			// drain iterator
			while (pit.hasNext())
				pit.next();

			// do a pass to retain any peers that are currently sending more to
			// us than we send to them
			/*
			 * pit = all_peers.iterator(); while (pit.hasNext() &&
			 * uploadBandwidthToAllocate > 0) { PEPeerTransport pep =
			 * (PEPeerTransport) pit.next();
			 *  // are they currently giving us more than we give them? if
			 * (UnchokerUtil.isUnchokable(pep, allow_snubbed) && pep.getStats() !=
			 * null && pep.getStats().getDataReceiveRate() >
			 * pep.getStats().getDataSendRate() && !pep.isSelfishTyrantPeer()) {
			 * //pep.getTyrantStats().getDataBytesReceivedInTheLast(60) >
			 * pep.getTyrantStats().getDataBytesSentInTheLast(60)){ // keep them
			 * long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
			 * toAllocate *= .98; ((PEPeerTransportProtocol)
			 * pep).getTyrantStats().setLastUploadAllocation((int) toAllocate);
			 * uploadBandwidthToAllocate -= toAllocate; System.out.println("kept " +
			 * pep + " because they were sending us more than we sent them");
			 * pep.getTyrantStats().setLastTyrantAction("Decreased upload
			 * allocation because they sent us more than we sent them in the
			 * last 10 sec."); unchokes.add(pep); pit.remove(); } } // drain
			 * iterator while (pit.hasNext()) pit.next();
			 */

			// do a pass for everyone with historically good ratios
			pit = all_peers.iterator();
			while (pit.hasNext() && uploadBandwidthToAllocate > 0) {
				PEPeerTransport pep = (PEPeerTransport) pit.next();

				if (UnchokerUtil.isUnchokable(pep, allow_snubbed) && pep.getStats() != null && pep.getStats().getTotalDataBytesSent() != 0
						&& ((double) pep.getStats().getTotalDataBytesReceived()) / pep.getStats().getTotalDataBytesSent() > 1 && !pep.isSelfishTyrantPeer()) {
					// find how much to give them
					long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
					toAllocate *= 1.10;
					// bootstrap if we don't know any info
					if (toAllocate == 0)
						toAllocate = fallbackAllocation;

					((PEPeerTransportProtocol) pep).getTyrantStats().setLastUploadAllocation((int) toAllocate);
					uploadBandwidthToAllocate -= toAllocate;
					System.out.println("kept " + pep + " because it had a good historic ratio");
					System.out.println(pep + " got " + toAllocate + " bytes for the next 10 sec");
					pep.getTyrantStats().setLastTyrantAction("Increased upload allocation because they weren't recipricating and they have a good historic ratio");
					unchokes.add(pep);
					pit.remove();
				}
			}
			// drain iterator
			while (pit.hasNext())
				pit.next();

			// ramp up allocations for selfish peers
			pit = all_peers.iterator();
			while (pit.hasNext() && uploadBandwidthToAllocate > 0) {
				PEPeerTransportProtocol pep = (PEPeerTransportProtocol) pit.next();

				if (UnchokerUtil.isUnchokable(pep, allow_snubbed) && pep.isSelfishTyrantPeer()) {
					// how much do we owe them? to keep the deficit race going
					long owed;
					long received = pep.getStats().getTotalDataBytesReceived();
					long sent = pep.getStats().getTotalDataBytesSent();

					if (sent < 100000) {
						// bootstrap about two pieces
						owed = 32 * 1024;
					} else {
						long maxWillingToSend = received + received / 4;
						owed = maxWillingToSend - sent;
					}
					
					owed = Math.min(owed, uploadBandwidthToAllocate);
					
					// unchoke and allocate to good peers that participate in
					// the race
					if (owed > 0) {
						((PEPeerTransportProtocol) pep).getTyrantStats().setLastUploadAllocation((int) owed);
						uploadBandwidthToAllocate -= owed;
						pep.getTyrantStats().setLastTyrantAction("Allocated to a selfish peer");
						unchokes.add(pep);

						// allocate and print
						System.out.println(pep + " got " + owed + " bytes for the next 10 sec");
						pep.getNetworkConnection().setBandwidth((int) owed);
					}
					pit.remove();
				}
			}
			// drain iterator
			while (pit.hasNext())
				pit.next();

			// allocate to everyone else, even bad ratios :/
			pit = all_peers.iterator();
			while (pit.hasNext() && uploadBandwidthToAllocate > 0) {
				PEPeerTransportProtocol pep = (PEPeerTransportProtocol) pit.next();
				if (UnchokerUtil.isUnchokable(pep, allow_snubbed)) {
					// find how much to give them
					long toAllocate = pep.getTyrantStats().getLastUploadAllocation();
					toAllocate *= 1.10;
					// bootstrap if we don't know any info
					if (toAllocate == 0)
						toAllocate = fallbackAllocation;

					// toAllocate = Math.min(toAllocate,
					// uploadBandwidthToAllocate);

					uploadBandwidthToAllocate -= toAllocate;
					((PEPeerTransportProtocol) pep).getTyrantStats().setLastUploadAllocation((int) toAllocate);
					System.out.println(pep + " was unchoked because we have excess bandwidth and we're picking peers with bad ratios");
					pep.getTyrantStats().setLastTyrantAction("Bad ratio peer, but increased upload allocation because we already met upload requirements of better peers");
					unchokes.add(pep);
					pit.remove();

					pep.getNetworkConnection().setBandwidth((int) toAllocate);
					System.out.println(pep + " got " + toAllocate + " bytes for the next 10 sec");
				}
			}
			// drain iterator
			while (pit.hasNext())
				pit.next();
		}

		// System.out.println(numUnchokable + " peers that can be unchoked");

		// make chokes
		for (Object o : all_peers) {
			if (!unchokes.contains(o)) {
				chokes.add(o);
				((PEPeer) o).getTyrantStats().setLastTyrantAction("choked");
			}
		}

		// assign bandwidth to unchoked
		/*
		 * for (PEPeerTransportProtocol pet : (List<PEPeerTransportProtocol>)
		 * unchokes) { long lastAllocation =
		 * pet.getTyrantStats().getLastUploadAllocation(); //and don't go over
		 * the limit
		 * 
		 * lastAllocation = Math.min(realLimit, lastAllocation); realLimit -=
		 * lastAllocation;
		 * 
		 * //actually assign it pet.getNetworkConnection().setBandwidth((int)
		 * lastAllocation); System.out.println("assigned " +
		 * pet.getTyrantStats().getLastUploadAllocation() + " bytes to " + pet + "
		 * for the next 10 sec"); }
		 */

		
		/* we don't need to allocate excess with the new weighted random network manager
		 * 
		if (uploadBandwidthToAllocate > 1 && unchokes.size() > 0 && EVENLY_ALLOCATE_EXCESS_BANDIWDTH) {
			// figure out what chunk of the leftover upload capcity to
			// contribute
			long bonus = uploadBandwidthToAllocate / unchokes.size();
			System.out.println("we have excess upload capacity.  Giving" + bonus + " additional bytes to each unchoked peer");
			for (PEPeerTransportProtocol pet : (List<PEPeerTransportProtocol>) unchokes) {
				long lastAllocation = pet.getTyrantStats().getLastUploadAllocation();
				lastAllocation += bonus;
				pet.getTyrantStats().setLastUploadAllocation(lastAllocation);
				pet.getNetworkConnection().setBandwidth((int) lastAllocation);
				pet.getTyrantStats().setLastTyrantAction(
						"Allocated excess bandwidth to this peer because we met every peer's upload requirements; last action: " + pet.getTyrantStats().getLastTyrantAction());
			}
		}
		*/
		// give everyone else bootstrap bandwidth so we can send
		// interested/KA/have messages
		for (PEPeerTransportProtocol pep : (List<PEPeerTransportProtocol>) chokes) {
			if (pep.getNetworkConnection() != null)
				pep.getNetworkConnection().setBandwidth((int) ABSOLUTE_MINIMUM_ALLOCATION_PER_PEER);
		}

		// figure out a weight for each unchoked connection
		long totalUploadAllocated = 0;
		for (PEPeerTransportProtocol pep : (List<PEPeerTransportProtocol>) unchokes) {
			totalUploadAllocated += pep.getTyrantStats().getLastUploadAllocation();
		}
		for (PEPeerTransportProtocol pep : (List<PEPeerTransportProtocol>) unchokes) {
			double connectionWeight = ((double)pep.getTyrantStats().getLastUploadAllocation());
			pep.getNetworkConnection().setWeight(connectionWeight);
			//pep.
			
			System.out.println("weight of " + connectionWeight + " assigned to " + pep.getNetworkConnection());
		}

		lastCalled = System.currentTimeMillis();
		cache = new ChokesAndUnchokes(chokes, unchokes);

		return cache;
	}
}