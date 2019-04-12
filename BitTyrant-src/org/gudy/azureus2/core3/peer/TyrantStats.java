package org.gudy.azureus2.core3.peer;


import org.gudy.azureus2.core3.peer.impl.transport.*;

import com.aelitis.azureus.core.peermanager.unchoker.HistoricalDataPoint;

import java.util.*;

public class TyrantStats
{
	public static String TYRANT_HOMEPAGE_URL = "http://bittyrant.cs.washington.edu";
	public static String TYRANT_GUI_TEXT_STRING = "BitTyrant";
	PEPeer mPeer = null;
	
	double last_observed_download = 0;
	double last_upload_cost = 0;
	
	long last_offered_update_time = 0, last_download_update_time = 0;
	
	long last_choked_time = 0;
	
	String	ipPort = null;
	ArrayList<String> logEntries = new ArrayList<String>();
	
	/* the newest data points are at the end of the list */
	LinkedList<HistoricalDataPoint> history = new LinkedList<HistoricalDataPoint>();
	
	//these are strings for what tyrant did
	public static String DEFAULT_ALLOCATION = "No peer history.  The peer got a default upload allocation.";
	
	
	public TyrantStats( PEPeer inPeer )
	{
		mPeer = inPeer;
		
		ipPort = ((PEPeerTransportProtocol)inPeer).getIp() + ":" + 
			((PEPeerTransportProtocol)inPeer).getPort();
	}
	
	/**
	 * call this whenever you want a log entry to be formed
	 *
	 */
	public void fileLog(long timeStamp)
	{
		StringBuilder sb = new StringBuilder();
		Formatter fmt = new Formatter(sb, Locale.US);
		
		fmt.format("%s %d %d %d %d %d %c %c %c %c %f %f %d",
			// identifying material
			ipPort, 
			timeStamp,
			// peer stats proper
			mPeer.getStats().getTotalDataBytesSent()/1024,
			mPeer.getStats().getTotalDataBytesReceived()/1024,
			mPeer.getStats().getDataSendRate()/1024,
			mPeer.getStats().getDataReceiveRate()/1024,
			// state information
			mPeer.isChokedByMe() ? 'y' : 'n',
			mPeer.isChokingMe() ? 'y' : 'n',
			mPeer.isInterested() ? 'y' : 'n',
			mPeer.isInteresting() ? 'y' : 'n',
			// tyrant unchoker state
			downloadRate(),
			getUploadCost(), 
			lastChoked()
			);
		
		logEntries.add(sb.toString());
	}
	
	public ArrayList<String> getLog() { return logEntries; }
	
	public double active_size()
	{
		long inRate = mPeer.getStats().getEstimatedUploadRateOfPeer();
		
		if( inRate < 11 )
			return 2;
		else if( inRate < 35 )
			return 3;
		else if( inRate < 80 )
			return 4;
		else if( inRate < 200 )
			return 5; 
		else if( inRate < 350 )
			return 6;
		else if( inRate < 600 )
			return 7;
		else if( inRate < 900 )
			return 8;
		else
			return 9;
	}
	
	public double downloadRate()
	{
		return last_observed_download;
	}
	
	public double getUploadCost()
	{
		return last_upload_cost;
	}
	
	public void updateOfferedRate( double inRate )
	{
		last_upload_cost = inRate;
		last_offered_update_time = System.currentTimeMillis();
	}
	
	public void updateDownloadRate()
	{
		last_observed_download = mPeer.getStats().getSmoothDataReceiveRate()/1024.0;
		last_download_update_time = System.currentTimeMillis();
	}
	
	public long getLastDownloadUpdate()
	{
		return last_download_update_time;
	}
	
	public void updateLastChokedTime()
	{
		last_choked_time = System.currentTimeMillis();
	}
	
	public long lastChoked() 
	{
		return last_choked_time;
	}
	
	public double ratio( double market_rate )
	{
		double d1 = downloadRate() == 0 ? (mPeer.getStats().getSmoothDataReceiveRate()/1024.0) / active_size() : downloadRate();
		double u1 = getUploadCost() == 0 ? market_rate : getUploadCost();
		
		return d1/u1;
	}
	
	public boolean hasObservedInfo()
	{
		if( last_observed_download != 0 && last_upload_cost != 0 )
			return true;
		return false;
	}
	
	long lastUploadAllocation = 0;
	public long getLastUploadAllocation(){
		return lastUploadAllocation;
	}
	public void setLastUploadAllocation(long allocation){
		lastUploadAllocation = allocation; //should this be set to some reasonable number if too low?
	}
	
	/**
	 * Updates our sense of history for this peer.  Also, deletes old references
	 * to history data that is older than a minute.
	 * @param now the new data point 
	 */
	public void updateHistory(HistoricalDataPoint now){
		//System.out.println("adding " + now);
		//bound the size of the list by nuking any data older than a minute
		long time = System.currentTimeMillis();
		while(history.size() > 0 && history.peek().getTimestamp() + 60*1000 < time)
			history.removeFirst();
		history.addLast(now);
		//System.out.println(" history is " + history);
	}
	
	public long getDataBytesReceivedInTheLast(long sec){
		if(history.size() == 0)
			return 0;
		//we need to find a data point at the start of the range
		long start = System.currentTimeMillis() - sec*1000;
		for(HistoricalDataPoint point : history){
			if(point.getTimestamp() > start)
				return history.getLast().getDataReceivedFromPeer()-point.getDataReceivedFromPeer();
		}
		
		return 0;
	}
	
	public long getDataBytesSentInTheLast(long sec){
		if(history.size() == 0)
			return 0;
		//we need to find a data point at the start of the range
		long start = System.currentTimeMillis() - sec*1000;
		for(HistoricalDataPoint point : history){
			if(point.getTimestamp() > start)
				return history.getLast().getDataSentToPeer()-point.getDataSentToPeer();
		}
		return 0;
	}
	
	/**
	 * Find an estimate of the data bytes received over the data bytes sent
	 * during the given amount of time.  The estimate only makes sense if there
	 * are history entries in history stats for this object.
	 * @param sec how far back in history to check.
	 */
	public double getBytesReceivedOverBytesSentRatioInTheLast(long sec){
		long sent = getDataBytesSentInTheLast(sec*1000);
		long received = getDataBytesReceivedInTheLast(sec*1000);
		
		if(sent == 0){
			if(received > 0)
				return Double.MAX_VALUE;
			else
				return 1;
		} else {
			return ((double)received)/sent;
		}
	}
	
	private String lastTyrantAction = "";

	/**
	 * 
	 * @return what the last action for this peer was
	 */
	public String getLastTyrantAction() {
		//Useful message for Tomas
		if(mPeer != null && mPeer.isSeed())
			return "Peer is a seed";
		
		
		return lastTyrantAction;
	}

	public void setLastTyrantAction(String lastTyrantAction) {
		this.lastTyrantAction = lastTyrantAction;
	}
}
