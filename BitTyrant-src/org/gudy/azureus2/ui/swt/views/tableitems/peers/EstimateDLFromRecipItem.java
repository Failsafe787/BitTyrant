package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

/**
 * Visual display of the estimated amount of bits that will be 
 * sent from this peer if we upload the estimated amount for reciprication
 * @author Jarret - jar@cs.washington.edu
 */
public class EstimateDLFromRecipItem extends CoreTableColumn implements TableCellRefreshListener{


	  public EstimateDLFromRecipItem() {
		  //code from download speed item
	    super("DLFromRecip", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
	    setRefreshInterval(INTERVAL_LIVE);
	  }
	
	public void refresh(TableCell cell) {
	    PEPeer peer = (PEPeer)cell.getDataSource();
	    
	    //just use the average that they already have.  This class could be useful if we do a different calculation later, but for now it's the same as the standard download column box.
	    
	    //the second param could just be dropped... and use a different format util
	    cell.setText(DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(peer.getStats().getDataReceiveRate(),0));
	}
}
