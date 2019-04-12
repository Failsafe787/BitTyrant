package com.aelitis.azureus.core.peermanager.unchoker;

/**
 * Encapsulates data about the state of upload/download counters at some
 * point in history.  Useful for calculating actions to take based on recent
 * history of a peer.
 * @author Jarret
 *
 */
public class HistoricalDataPoint {
	long timestamp;
	long dataSentToPeer;
	long dataReceivedFromPeer;
	/**
	 * Make a data point using the given upload and download counts
	 * @param dataSentToPeer the total amount of data (payload) that has been sent to the peer
	 * @param dataReceivedFromPeer the total amount of data that has been received from the peer
	 */
	public HistoricalDataPoint(long dataSentToPeer, long dataReceivedFromPeer) {
		this.timestamp = System.currentTimeMillis();
		this.dataSentToPeer = dataSentToPeer;
		this.dataReceivedFromPeer = dataReceivedFromPeer;
	}
	public long getDataReceivedFromPeer() {
		return dataReceivedFromPeer;
	}
	public long getDataSentToPeer() {
		return dataSentToPeer;
	}
	public long getTimestamp() {
		return timestamp;
	}
	
	public String toString(){
		return "when " + timestamp + " rec: " + dataReceivedFromPeer + " sent: " + dataSentToPeer;
	}
}
