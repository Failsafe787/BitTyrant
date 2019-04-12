/**
 * 
 */
package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.transport.PEPeerTransportProtocol;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;

/**
 * 
 * A view of Up to down ratio from recent history
 * @author Jarret
 * 
 */
public class RecentUpDownRatioItem extends CoreTableColumn implements TableCellRefreshListener {

	public RecentUpDownRatioItem() {
		// code from download speed item
		super("RecentUpDownRatio", ALIGN_TRAIL, POSITION_LAST, 65, TableManager.TABLE_TORRENT_PEERS);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void refresh(TableCell cell) {
		PEPeerTransportProtocol peer = (PEPeerTransportProtocol) cell.getDataSource();

		// c&p from UpDownRatioItem... with small edits

		// PEPeer peer = (PEPeer)cell.getDataSource();
		float value = 0;
		long lDivisor = 0;
		long lDivident = 0;
		if (peer != null) {
			// lDivisor = peer.getStats().getTotalDataBytesReceived()
			// -peer.getStats().getTotalBytesDiscarded();
			lDivisor = peer.getTyrantStats().getDataBytesReceivedInTheLast(45);
			// lDivident = peer.getStats().getTotalDataBytesSent();
			lDivident = peer.getTyrantStats().getDataBytesSentInTheLast(45);

			// skip if divisor is small (most likely handshake) or 0
			// (DivisionByZero)
			if (lDivisor > 128) {
				value = lDivident / (float) lDivisor;
				if (value == 0)
					value = -1;
			} else if (lDivident > 0)
				value = Constants.INFINITY_AS_INT;
		}

		if (!cell.setSortValue((long) (value * 1000)) && cell.isValid())
			return;

		String s;
		if (lDivisor <= 0)
			s = "";
		else if (value == Constants.INFINITY_AS_INT)
			s = Constants.INFINITY_STRING + ":1";
		else if (value == -1)
			s = "1:" + Constants.INFINITY_STRING;
		else
			s = DisplayFormatters.formatDecimal(value, 2) + ":1";

		cell.setText(s);
	}

}
