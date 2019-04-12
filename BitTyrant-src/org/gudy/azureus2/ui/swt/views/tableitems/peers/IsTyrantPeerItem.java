/**
 * Visual display of our assumption that the other peer is selfish
 * @author Jarret jar@cs.washington.edu
 */
package org.gudy.azureus2.ui.swt.views.tableitems.peers;


import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

/**
 * @author Jarret
 *
 */
public class IsTyrantPeerItem extends CoreTableColumn implements
		TableCellRefreshListener {

	  public IsTyrantPeerItem() {
		  //code from download speed item
	    super("IsTyrantPeer", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
	    setRefreshInterval(INTERVAL_LIVE);
	  }
	/* (non-Javadoc)
	 * @see org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener#refresh(org.gudy.azureus2.plugins.ui.tables.TableCell)
	 */
	public void refresh(TableCell cell) {

	    PEPeer peer = (PEPeer)cell.getDataSource();
	    
	    //Pretty print the tyrant peer flag
	    if(peer.isSelfishTyrantPeer())
	    	cell.setText("y");
	    else 
	    	cell.setText("n");
	}
}
