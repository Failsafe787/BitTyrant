package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

public class LastTyrantActionItem extends CoreTableColumn implements TableCellRefreshListener {

	public LastTyrantActionItem() {
		// code from download speed item
		super("LastTyrantAction", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void refresh(TableCell cell) {
		PEPeerTransportProtocol peer = (PEPeerTransportProtocol) cell.getDataSource();

		// dump the network connection's data into the gui.

		if(peer != null && peer.getTyrantStats() != null)
			cell.setText(peer.getTyrantStats().getLastTyrantAction());
		else
			cell.setText("");
		//if (peer.getNetworkConnection() != null)
		//	cell.setText(DisplayFormatters.formatByteCountToKiBEtc(peer.getNetworkConnection().getBandwidthLimit()));
		//else
		//	cell.setText("0");
	}
}
