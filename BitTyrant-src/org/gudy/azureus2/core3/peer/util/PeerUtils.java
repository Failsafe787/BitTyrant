/*
 * Created on Mar 20, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 * 
 */
package org.gudy.azureus2.core3.peer.util;

import java.util.HashSet;
import java.util.Set;

import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.config.impl.TransferSpeedValidator;

/**
 * Various peer connection utility methods.
 * 
 * @author Azureus staff
 * @author Jarret - jar@cs.washington.edu
 */
public class PeerUtils {

	private static final String CONFIG_MAX_CONN_PER_TORRENT = "Max.Peer.Connections.Per.Torrent";

	public static final String CONFIG_MAX_CONN_TOTAL = "Max.Peer.Connections.Total";

	public static int MAX_CONNECTIONS_PER_TORRENT;

	public static int MAX_CONNECTIONS_TOTAL;

	public static int AUTO_MAX_CONNECTIONS_PER_TORRENT = 0;

	public static int AUTO_MAX_CONNECTIONS_TOTAL;

	static {

		COConfigurationManager.addParameterListener(CONFIG_MAX_CONN_PER_TORRENT, new ParameterListener() {
			public void parameterChanged(String parameterName) {
				MAX_CONNECTIONS_PER_TORRENT = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_PER_TORRENT);
			}
		});

		MAX_CONNECTIONS_PER_TORRENT = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_PER_TORRENT);

		COConfigurationManager.addParameterListener(CONFIG_MAX_CONN_TOTAL, new ParameterListener() {
			public void parameterChanged(String parameterName) {
				MAX_CONNECTIONS_TOTAL = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_TOTAL);
			}
		});

		MAX_CONNECTIONS_TOTAL = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_TOTAL);

		COConfigurationManager.addAndFireParameterListener("Max Upload Speed KBs", new ParameterListener() {
			public void parameterChanged(String pname) {
				int calculatedMaxPeers = COConfigurationManager.getIntParameter(TransferSpeedValidator.UPLOAD_CONFIGKEY);
				calculatedMaxPeers *= 10;
				calculatedMaxPeers = Math.min(calculatedMaxPeers, 2000); // two
																			// thousand
																			// peers
																			// max
				calculatedMaxPeers = Math.max(100, calculatedMaxPeers); // don't
																		// go
																		// too
																		// low
				AUTO_MAX_CONNECTIONS_TOTAL = calculatedMaxPeers;

				// System.out.println("changed auto max peers because of u/l cap
				// change: max peers " +AUTO_MAX_CONNECTIONS_TOTAL);
			}
		});
	}

	/**
	 * Get the number of new peer connections allowed for the given data item,
	 * within the configured per-torrent and global connection limits.
	 * 
	 * @return max number of new connections allowed, or -1 if there is no limit
	 */
	public static int numNewConnectionsAllowed(PeerIdentityDataID data_id, int specific_max) {
		//the logic behind those listeners got borked.
		MAX_CONNECTIONS_TOTAL = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_TOTAL);
		MAX_CONNECTIONS_PER_TORRENT = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_PER_TORRENT);

		int curConnPerTorrent = PeerIdentityManager.getIdentityCount(data_id);
		int curConnTotal = PeerIdentityManager.getTotalIdentityCount();

		// specific max here will default to the global per-torrent default if
		// not explicitly set
		// so we don't need to consider CONFIG_MAX_CONN_PER_TORRENT seperately

		int PER_TORRENT_LIMIT = specific_max;

		boolean autoMaxConnectionsEnabled = COConfigurationManager.getBooleanParameter("Enable.AutoMaxConnections", true);
		if (autoMaxConnectionsEnabled) {
			// override config's per torrent and global maxes
			PER_TORRENT_LIMIT = 0;
			MAX_CONNECTIONS_TOTAL = AUTO_MAX_CONNECTIONS_TOTAL;
		}

		// System.out.println("Calculated " + calculatedMaxPeers + " possible
		// max peers");
		int perTorrentAllowed = -1; // default unlimited
		if (PER_TORRENT_LIMIT != 0) { // if limited
			int allowed = PER_TORRENT_LIMIT - curConnPerTorrent;
			if (allowed < 0)
				allowed = 0;
			perTorrentAllowed = allowed;
		}

		int totalAllowed = -1; // default unlimited
		if (MAX_CONNECTIONS_TOTAL != 0) { // if limited
			int allowed = MAX_CONNECTIONS_TOTAL - curConnTotal;
			if (allowed < 0)
				allowed = 0;
			totalAllowed = allowed;
		}

		int allowed = -1; // default unlimited
		if (perTorrentAllowed > -1 && totalAllowed > -1) { // if both limited
			allowed = Math.min(perTorrentAllowed, totalAllowed);
		} else if (perTorrentAllowed == -1 || totalAllowed == -1) { // if either
																	// unlimited
			allowed = Math.max(perTorrentAllowed, totalAllowed);
		}
		// System.out.println("allowing " + allowed + " connections");
		/*
		String consoleMessage = "";
		if (autoMaxConnectionsEnabled) {
			consoleMessage = "Using auto max connections, ";
		} else {
			consoleMessage = "Not using auto max connections, ";
		}

		consoleMessage += "allowing up to " + MAX_CONNECTIONS_TOTAL;
		consoleMessage += " (but only " + perTorrentAllowed + " per torrent). ";
		consoleMessage += Integer.toString(curConnTotal);
		consoleMessage += " current connections.";
		System.out.println(consoleMessage);
		System.out.println("allowing " + allowed + " new connections");
		*/
		return allowed;
	}

	private static Set ignore_peer_ports = new HashSet();

	static {
		COConfigurationManager.addParameterListener("Ignore.peer.ports", new ParameterListener() {
			public void parameterChanged(String parameterName) {
				readIgnorePeerPorts();
			}
		});

		readIgnorePeerPorts();
	}

	private static void readIgnorePeerPorts() {
		// XXX Optimize me for ranges!!
		String str = COConfigurationManager.getStringParameter("Ignore.peer.ports").trim();

		ignore_peer_ports.clear();

		if (str.length() > 0) {

			String[] ports = str.split("\\;");
			if (ports != null && ports.length > 0) {
				for (int i = 0; i < ports.length; i++) {
					String port = ports[i];
					int spreadPos = port.indexOf('-');
					if (spreadPos > 0 && spreadPos < port.length() - 1) {
						try {
							int iMin = Integer.parseInt(port.substring(0, spreadPos).trim());
							int iMax = Integer.parseInt(port.substring(spreadPos + 1).trim());

							for (int j = iMin; j <= iMax; j++) {
								ignore_peer_ports.add("" + j);
							}
						} catch (Exception e) {
						}
					} else {
						ignore_peer_ports.add(port.trim());
					}
				}
			}
		}
	}

	public static boolean ignorePeerPort(int port) {
		return (ignore_peer_ports.contains("" + port));
	}
}
