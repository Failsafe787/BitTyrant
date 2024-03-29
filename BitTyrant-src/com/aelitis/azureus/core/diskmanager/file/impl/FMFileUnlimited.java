/*
 * File    : FMFileUnlimited.java
 * Created : 12-Feb-2004
 * By      : parg
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.aelitis.azureus.core.diskmanager.file.impl;

/**
 * @author parg
 *
 */

import java.io.File;

import org.gudy.azureus2.core3.util.DirectByteBuffer;

import com.aelitis.azureus.core.diskmanager.file.*;

public class 
FMFileUnlimited
	extends FMFileImpl
{
	protected
	FMFileUnlimited(
		FMFileOwner			_owner,
		FMFileManagerImpl	_manager,
		File				_file,
		int					_type )
	
		throws FMFileManagerException
	{
		super( _owner, _manager, _file, _type );
	}
	
	
	public void
	setAccessMode(
		int		mode )
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();
		
			if ( mode == getAccessMode() && isOpen()){
				
				return;
			}
			
			setAccessModeSupport( mode );
			
			if ( isOpen()){
				
				closeSupport( false );
			}
			
			openSupport( "FMFileUnlimited:setAccessMode" );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public long
	getLength()
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();
		
			ensureOpen( "FMFileUnlimited:getLength" );
		
			return( getLengthSupport());
			
		}finally{
			
			this_mon.exit();
		}
	}

	public void
	setLength(
		long		length )
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:setLength" );
		
			setLengthSupport( length );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	read(
		DirectByteBuffer	buffer,
		long		offset )
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:read" );
		
			readSupport( buffer, offset );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	
	public void
	write(
		DirectByteBuffer	buffer,
		long		position )
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:write" );
		
			writeSupport( buffer, position );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:write" );
		
			writeSupport( buffers, position );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	close()
	
		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			closeSupport( true );
			
		}finally{
			
			this_mon.exit();
		}
	}
}
