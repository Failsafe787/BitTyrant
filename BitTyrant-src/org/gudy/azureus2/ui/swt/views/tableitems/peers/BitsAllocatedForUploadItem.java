/**
 * Visual element that shows per-connection bandwidth available
 * @author Jarret jar@cs.washington.edu
 */
package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

/**
 * 
 * Table element that shows how many bits this connection is allowed to upload
 * 
 * @author Jarret
 * 
 */
public class BitsAllocatedForUploadItem extends CoreTableColumn implements TableCellRefreshListener {

	public BitsAllocatedForUploadItem() {
		// code from download speed item
		super("BitsAllocatedForUpload", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void refresh(TableCell cell) {
		PEPeerTransportProtocol peer = (PEPeerTransportProtocol) cell.getDataSource();

		// dump the network connection's data into the gui.

		if (peer.isChokedByMe())
			cell.setText("");
		else if (peer.getNetworkConnection() != null)
			cell.setText(DisplayFormatters.formatByteCountToKiBEtc(peer.getTyrantStats().getLastUploadAllocation()));
			//cell.setText(DisplayFormatters.formatByteCountToKiBEtc(peer.getNetworkConnection().getBandwidthLimit()));
		else
			cell.setText("0");
	}
}
