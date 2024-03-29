/*
 * Created on 22 Jun 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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

package com.aelitis.azureus.core.networkmanager.impl.udp;

import java.util.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.ConnectionEndpoint;
import com.aelitis.azureus.core.networkmanager.impl.IncomingConnectionManager;
import com.aelitis.azureus.core.networkmanager.impl.ProtocolDecoder;
import com.aelitis.azureus.core.networkmanager.impl.TransportCryptoManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;

public class
UDPConnectionManager
	implements NetworkGlueListener
{
	private static final LogIDs LOGID = LogIDs.NET;
	private static final boolean 	LOOPBACK	= false;
	private static final boolean	FORCE_LOG	= false;
	
	private static boolean	LOG = false;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
				"Logging Enable UDP Transport",
				new ParameterListener()
				{
					public void 
					parameterChanged(
						String name )
					{
						LOG = FORCE_LOG || COConfigurationManager.getBooleanParameter( name );
					}
				});
	}
	
	public static final int	TIMER_TICK_MILLIS				= 25;
	public static final int	THREAD_LINGER_ON_IDLE_PERIOD	= 30*1000;
	public static final int	DEAD_KEY_RETENTION_PERIOD		= 30*1000;
	
	public static final int STATS_TIME	= 60*1000;
	public static final int	STATS_TICKS	= STATS_TIME / TIMER_TICK_MILLIS;
	
	private final Map	connection_sets 	= new HashMap();
	private final Map	recently_dead_keys	= new HashMap();
	
	private int next_connection_id;

	
	private IncomingConnectionManager	incoming_manager = IncomingConnectionManager.getSingleton();

	private NetworkGlue	network_glue;
	
	private UDPSelector		selector;
	private ProtocolTimer	protocol_timer;
	private long			idle_start;
	
	private static final int			BLOOM_RECREATE				= 30*1000;
	private static final int			BLOOM_INCREASE				= 1000;
	private BloomFilter					incoming_bloom				= BloomFilterFactory.createAddRemove4Bit(BLOOM_INCREASE);
	private long						incoming_bloom_create_time	= SystemTime.getCurrentTime();
	private long						last_incoming;
	

	private int rate_limit_discard_packets;
	private int	rate_limit_discard_bytes;
	private int setup_discard_packets;
	private int	setup_discard_bytes;
	
	
	protected
	UDPConnectionManager()
	{		
		if ( LOOPBACK ){
			
			network_glue = new NetworkGlueLoopBack( this );
			
		}else{
			
			network_glue = new NetworkGlueUDP( this );
		}
	}
	
	protected UDPSelector
	checkThreadCreation()
	{
			// called while holding the "connections" monitor
		
		if ( selector == null ){
			
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "UDPConnectionManager: activating" ));
			}
			
			selector = new UDPSelector(this );
			
			protocol_timer = new ProtocolTimer();
		}
		
		return( selector );
	}
	
	protected void
	checkThreadDeath(
		boolean		connections_running )
	{
			// called while holding the "connections" monitor

		if ( connections_running ){
			
			idle_start = 0;
			
		}else{
			
			long	now = SystemTime.getCurrentTime();
			
			if ( idle_start == 0 ){
				
				idle_start = now;
				
			}else if ( idle_start > now ){
				
				idle_start = now;
				
			}else if ( now - idle_start > THREAD_LINGER_ON_IDLE_PERIOD ){
				
				if (Logger.isEnabled()){
					Logger.log(new LogEvent(LOGID, "UDPConnectionManager: deactivating" ));
				}
				
				selector.destroy();
				
				selector = null;
				
				protocol_timer.destroy();
				
				protocol_timer = null;
			}
		}
	}
	
	protected void
	poll()
	{
		synchronized( connection_sets ){

			Iterator	it = connection_sets.values().iterator();
			
			while( it.hasNext()){
				
				((UDPConnectionSet)it.next()).poll();
			}
		}
	}
	
	public void
	remove(
		UDPConnectionSet	set,
		UDPConnection		connection )
	{
		synchronized( connection_sets ){

			if ( set.remove( connection )){

				String	key = set.getKey();

				if ( set.hasFailed()){
					
					if ( connection_sets.remove( key ) != null ){
						
						set.removed();
						
						recently_dead_keys.put( key, new Long( SystemTime.getCurrentTime()));
						
						if (Logger.isEnabled()){
							
							Logger.log(new LogEvent(LOGID, "Connection set " + key + " failed"));
						}
					}
				}
			}	                          
		}                    
	}
	
	public void
	failed(
		UDPConnectionSet	set )
	{
		synchronized( connection_sets ){

			String	key = set.getKey();
					
			if ( connection_sets.remove( key ) != null ){
					
				set.removed();
				
				recently_dead_keys.put( key, new Long( SystemTime.getCurrentTime()));
				
				if (Logger.isEnabled()){
					
					Logger.log(new LogEvent(LOGID, "Connection set " + key + " failed"));
				}
			}                      
		}                    
	}
	
	protected UDPConnection
	registerOutgoing(
		UDPTransportHelper		helper )
	
		throws IOException
	{
		int	local_port = UDPNetworkManager.getSingleton().getUDPListeningPortNumber();
		
		InetSocketAddress	address = helper.getAddress();
		
		String	key = local_port + ":" + address.getAddress().getHostAddress() + ":" + address.getPort();
		
		synchronized( connection_sets ){
			
			UDPSelector	current_selector	= checkThreadCreation();
			
			UDPConnectionSet	connection_set = (UDPConnectionSet)connection_sets.get( key );
			
			if ( connection_set == null ){
				
				timeoutDeadKeys();
				
				connection_set = new UDPConnectionSet( this, key, current_selector, local_port, address );
				
				if (Logger.isEnabled()){
					
					Logger.log(new LogEvent(LOGID, "Created new set - " + connection_set.getName() + ", outgoing"));
				}
				
				connection_sets.put( key, connection_set );
			}
			
			UDPConnection	connection = new UDPConnection( connection_set, allocationConnectionID(), helper );
			
			connection_set.add( connection );
			
			return( connection  );
		}
	}
	
	public void
	receive(
		int					local_port,
		InetSocketAddress	remote_address,
		byte[]				data,
		int					data_length )
	{
		String	key = local_port + ":" + remote_address.getAddress().getHostAddress() + ":" + remote_address.getPort();
		
		UDPConnectionSet	connection_set;
		
		synchronized( connection_sets ){
			
			UDPSelector	current_selector	= checkThreadCreation();
			
			connection_set = (UDPConnectionSet)connection_sets.get( key );
			
			if ( connection_set == null ){
				
				timeoutDeadKeys();
				
					// check that this at least looks like an initial crypto packet
				
				if (	data_length >= UDPNetworkManager.MIN_INCOMING_INITIAL_PACKET_SIZE &&
						data_length <= UDPNetworkManager.MAX_INCOMING_INITIAL_PACKET_SIZE ){
				
					if ( !rateLimitIncoming( remote_address )){
						
						rate_limit_discard_packets++;
						rate_limit_discard_bytes	+= data_length;
						
						return;
					}
					
					connection_set = new UDPConnectionSet( this, key, current_selector, local_port, remote_address );
						
					if (Logger.isEnabled()){
						
						Logger.log(new LogEvent(LOGID, "Created new set - " + connection_set.getName() + ", incoming"));
					}
					
					connection_sets.put( key, connection_set );
					
				}else{
					
					if ( recently_dead_keys.get( key ) == null ){
	
						// we can get quite a lot of these if things get out of sync
						
						// Debug.out( "Incoming UDP packet mismatch for connection establishment: " + key );
					}
					
					setup_discard_packets++;
					setup_discard_bytes	+= data_length;

					return;
				}
			}
		}
		
		try{
			//System.out.println( "recv:" + ByteFormatter.encodeString( data, 0, data_length>64?64:data_length ) + (data_length>64?"...":""));
			
			connection_set.receive( data, data_length );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			connection_set.failed( e );
		}
	}
	
	protected synchronized boolean
	rateLimitIncoming(
		InetSocketAddress	s_address )
	{
		byte[]	address = s_address.getAddress().getAddress();
		
		int	hit_count = incoming_bloom.add( address );
		
		long	now = SystemTime.getCurrentTime();

			// allow up to 10% bloom filter utilisation
		
		if ( incoming_bloom.getSize() / incoming_bloom.getEntryCount() < 10 ){
			
			incoming_bloom = BloomFilterFactory.createAddRemove4Bit(incoming_bloom.getSize() + BLOOM_INCREASE );
			
			incoming_bloom_create_time	= now;
			
     		Logger.log(	new LogEvent(LOGID, "UDP connnection bloom: size increased to " + incoming_bloom.getSize()));

		}else if ( now < incoming_bloom_create_time || now - incoming_bloom_create_time > BLOOM_RECREATE ){
			
			incoming_bloom = BloomFilterFactory.createAddRemove4Bit(incoming_bloom.getSize());
			
			incoming_bloom_create_time	= now;
		}
			
		if ( hit_count >= 15 ){
			
     		Logger.log(	new LogEvent(LOGID, "UDP incoming: too many recent connection attempts from " + s_address ));
     		
			return( false );
		}
		
		long	since_last = now - last_incoming;
		
		long	delay = 100 - since_last;
		
			// limit to 10 a second
		
		if ( delay > 0 && delay < 100 ){
			
			try{
				Thread.sleep( delay );
				
			}catch( Throwable e ){
			}
		}
		
		last_incoming = now;	
		
		return( true );
	}
	
	public int
	send(
		int					local_port,
		InetSocketAddress	remote_address,
		byte[]				data )
	
		throws IOException
	{
		return( network_glue.send( local_port, remote_address, data ));
	}
	
	protected void
	accept(
		final int				local_port,
		final InetSocketAddress	remote_address,
		final UDPConnection		connection )
	{
		final UDPTransportHelper	helper = new UDPTransportHelper( this, remote_address, connection );

		try{
			connection.setTransport( helper );
			
			TransportCryptoManager.getSingleton().manageCrypto( 
				helper, 
				null, 
				true, 
				null,
				new TransportCryptoManager.HandshakeListener() 
				{
					public void 
					handshakeSuccess( 
						ProtocolDecoder	decoder,
						ByteBuffer		remaining_initial_data ) 
					{
						TransportHelperFilter	filter = decoder.getFilter();
						
						ConnectionEndpoint	co_ep = new ConnectionEndpoint( remote_address);
	
						ProtocolEndpointUDP	pe_udp = new ProtocolEndpointUDP( co_ep, remote_address );
	
						UDPTransport transport = new UDPTransport( pe_udp, filter );
								
						helper.setTransport( transport );
						
						incoming_manager.addConnection( local_port, filter, transport );
	        		}
	
					public void 
					handshakeFailure( 
	            		Throwable failure_msg ) 
					{
						if (Logger.isEnabled()){
							Logger.log(new LogEvent(LOGID, "incoming crypto handshake failure: " + Debug.getNestedExceptionMessage( failure_msg )));
						}
	 
						connection.close( "handshake failure: " + Debug.getNestedExceptionMessage(failure_msg));
					}
	            
					public void
					gotSecret(
						byte[]				session_secret )
					{
						helper.getConnection().setSecret( session_secret );
					}
					
					public int
					getMaximumPlainHeaderLength()
					{
						return( incoming_manager.getMaxMinMatchBufferSize());
					}
	    		
					public int
					matchPlainHeader(
							ByteBuffer			buffer )
					{
						Object[]	match_data = incoming_manager.checkForMatch( helper, local_port, buffer, true );
	    			
						if ( match_data == null ){
	    				
							return( TransportCryptoManager.HandshakeListener.MATCH_NONE );
	    				
						}else{
							
								// no fallback for UDP
	    				
							return( TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_NO_AUTO_FALLBACK );
	    				}
	    			}
	        	});
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			helper.close( Debug.getNestedExceptionMessage(e));
		}
	}
	
	protected synchronized int
	allocationConnectionID()
	{
		int	id = next_connection_id++;
		
		if ( id < 0 ){
			
			id					= 0;
			next_connection_id	= 1;
		}
		
		return( id );
	}
	
	protected void
	timeoutDeadKeys()
	{
		Iterator	it = recently_dead_keys.values().iterator();
		
		long	now = SystemTime.getCurrentTime();
		
		while( it.hasNext()){
			
			long	dead_time = ((Long)it.next()).longValue();
		
			if ( dead_time > now || now - dead_time > DEAD_KEY_RETENTION_PERIOD ){
								
				it.remove();
			}
		}
	}
	
	protected class
	ProtocolTimer
	{
		private volatile boolean	destroyed;
		
		protected 
		ProtocolTimer()
		{
			new AEThread( "UDPConnectionManager:timer", true )
			{
				private int	tick_count;
				
				public void
				runSupport()
				{
					Thread.currentThread().setPriority( Thread.NORM_PRIORITY + 1 );
					
					while( !destroyed ){
						
						try{
							Thread.sleep( TIMER_TICK_MILLIS );
							
						}catch( Throwable e ){
							
						}
							
						tick_count++;
						
						if ( tick_count % STATS_TICKS == 0 ){
							
							logStats();
						}
						
						List	failed_sets = null;
						
						synchronized( connection_sets ){
								
							int	cs_size = connection_sets.size();
							
							checkThreadDeath( cs_size > 0 );
								
							if ( cs_size > 0 ){
								
								Iterator	it = connection_sets.values().iterator();
								
								while( it.hasNext()){
									
									UDPConnectionSet	set = (UDPConnectionSet)it.next();
									
									try{
										set.timerTick();
										
										if ( set.idleLimitExceeded()){
																					
											if (Logger.isEnabled()){
												
												Logger.log(new LogEvent(LOGID, "Idle limit exceeded for " + set.getName() + ", removing" ));
											}
											
											recently_dead_keys.put( set.getKey(), new Long( SystemTime.getCurrentTime()));
											
											it.remove();
											
											set.removed();
										}
									}catch( Throwable e ){
										
										if ( failed_sets == null ){
											
											failed_sets = new ArrayList();
										}
										
										failed_sets.add( new Object[]{ set, e });
									}
								}
							}
						}
						
						if ( failed_sets != null ){
							
							for (int i=0;i<failed_sets.size();i++){
								
								Object[]	entry = (Object[])failed_sets.get(i);
								
								((UDPConnectionSet)entry[0]).failed((Throwable)entry[1]);
							}
						}
					}
					
					logStats();
				}
			}.start();
		}
		
		protected void
		destroy()
		{
			destroyed	= true;
		}
	}
	
	protected void 
	logStats()
	{
		if (Logger.isEnabled()){
			
			long[]	nw_stats = network_glue.getStats();
		
			String	str = "UDPConnection stats: sent=" + nw_stats[0] + "/" + nw_stats[1] + ",received=" + nw_stats[2] + "/" + nw_stats[3];
			
			str += ", setup discards=" + setup_discard_packets + "/" + setup_discard_bytes;
			str += ", rate discards=" + rate_limit_discard_packets + "/" + rate_limit_discard_bytes;
			
			Logger.log(new LogEvent(LOGID, str ));
		}
	}
	
	protected boolean
	trace()
	{
		return( LOG );
	}
	
	protected void
	trace(
		String				str )
	{
		if ( LOG ){
			
			if ( FORCE_LOG ){
				
				System.out.println( str );
			}
			
			if (Logger.isEnabled()){
				
				Logger.log(new LogEvent(LOGID, str ));
			}
		}
	}
}
