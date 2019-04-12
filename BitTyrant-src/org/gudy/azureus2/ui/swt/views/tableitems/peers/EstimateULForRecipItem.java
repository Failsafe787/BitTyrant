/**
 * Visual display of the upload required for a peer to recipricate bits
 * @author Jarret jar@cs.washington.edu
 */
package org.gudy.azureus2.ui.swt.views.tableitems.peers;


import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

/**
 * @author Jarret
 *
 */
public class EstimateULForRecipItem extends CoreTableColumn implements
		TableCellRefreshListener {

	  public EstimateULForRecipItem() {
		  //code from download speed item
	    super("ULForRecip", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
	    setRefreshInterval(INTERVAL_LIVE);
	  }
	/* (non-Javadoc)
	 * @see org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener#refresh(org.gudy.azureus2.plugins.ui.tables.TableCell)
	 */
	public void refresh(TableCell cell) {

	    PEPeer peer = (PEPeer)cell.getDataSource();
	    
	    // TODO find the ul needed
	    
	    //the second param could just be dropped... and use a different format util
	    cell.setText(DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec((long)peer.getTyrantStats().getUploadCost(),0));
	}
}
