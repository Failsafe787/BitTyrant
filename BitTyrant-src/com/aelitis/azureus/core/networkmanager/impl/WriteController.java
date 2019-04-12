/*
 * Created on Sep 27, 2004
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

package com.aelitis.azureus.core.networkmanager.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.EventWaiter;


/**
 * Processes writes of write-entities and handles the write selector.
 */
public class WriteController {
  
  private volatile ArrayList normal_priority_entities = new ArrayList();  //copied-on-write
  private volatile ArrayList high_priority_entities = new ArrayList();  //copied-on-write
  private final AEMonitor entities_mon = new AEMonitor( "WriteController:EM" );
  private int next_normal_position = 0;
  private int next_high_position = 0;
  
  private static final int IDLE_SLEEP_TIME  = 50;
   
  private EventWaiter 	write_waiter = new EventWaiter();
  
  /**
   * Create a new write controller.
   */
  public WriteController() {
    
    //start write handler processing
    Thread write_processor_thread = new AEThread( "WriteController:WriteProcessor" ) {
      public void runSupport() {
        writeProcessorLoop();
      }
    };
    write_processor_thread.setDaemon( true );
    write_processor_thread.setPriority( Thread.MAX_PRIORITY - 1 );
    write_processor_thread.start();
  }
    
  
  private void writeProcessorLoop() {
    boolean check_high_first = true;
    
    while( true ) {
      try {
        if( check_high_first ) {
          check_high_first = false;
          if( !doHighPriorityWrite() ) {
            if( !doNormalPriorityWrite() ) {
              write_waiter.waitForEvent( IDLE_SLEEP_TIME );
            }
          }
        }
        else {
          check_high_first = true;
          if( !doNormalPriorityWrite() ) {
            if( !doHighPriorityWrite() ) {
            	write_waiter.waitForEvent( IDLE_SLEEP_TIME );
            }
          }
        }
      }
      catch( Throwable t ) {
        Debug.out( "writeProcessorLoop() EXCEPTION: ", t );
      }
    }
  }
  
  
  private boolean doNormalPriorityWrite() {
    RateControlledEntity ready_entity = getNextReadyNormalPriorityEntity();
    if( ready_entity != null && ready_entity.doProcessing( write_waiter ) ) {
      return true;
    }
    return false;
  }
  
  private boolean doHighPriorityWrite() {
    RateControlledEntity ready_entity = getNextReadyHighPriorityEntity();
    if( ready_entity != null && ready_entity.doProcessing( write_waiter ) ) {
      return true;
    }
    return false;
  }
  
 // private HashMap<SinglePeerUploader, Integer> debugWeights = new HashMap<SinglePeerUploader, Integer>();
 // private AtomicInteger lastWeight = new AtomicInteger(1);
  private RateControlledEntity getNextReadyNormalPriorityEntity() {
    ArrayList ref = normal_priority_entities;
    
    //who can write?
    ArrayList<SinglePeerUploader> writable = new ArrayList<SinglePeerUploader>();
    
    double totalWritableWeight = 0.0001;
    //int lastWeight = 1; //debug!
    for(RateControlledEntity rce : (ArrayList<RateControlledEntity>)ref){
    	if(rce.canProcess(write_waiter)){
    		SinglePeerUploader spu = (SinglePeerUploader)rce;
    		writable.add(spu);
    		/*
    		if(!debugWeights.containsKey(spu)){
    			int next = ((int)(Math.random()*100)+1);
    			debugWeights.put(spu, next);
    		}
    		
    		spu.setWeight(debugWeights.get(spu));
    		
    		*/
    		totalWritableWeight += spu.getWeight();
    	}
    }
    // System.out.println("total weight " + totalWritableWeight + " , " + writable.size() + " writable connections out of " + ref.size() + " peers");
    //System.out.println("weights " + debugWeights);
    
    //if(writable.size() < 2)
    //	return null;
    
    double random = Math.random();
    double where = 0.0; //not normalized
    SinglePeerUploader toReturn = null;
    for(SinglePeerUploader spu : writable){
    	if(where/totalWritableWeight <= random){
    		toReturn = spu;
    	}
    	where += spu.getWeight();
    }
    //if(toReturn != null){
   	// 	System.out.println("returning " + toReturn + " it has a weight " + toReturn.getWeight());
    // }
    return toReturn;
    
    /*
    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      next_normal_position = next_normal_position >= size ? 0 : next_normal_position;  //make circular
      RateControlledEntity entity = (RateControlledEntity)ref.get( next_normal_position );
      System.out.println("type of normal priority entity is " + entity.getClass().toString());
      next_normal_position++;
      num_checked++;
      if( entity.canProcess( write_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
    */
  }
  
  
  private RateControlledEntity getNextReadyHighPriorityEntity() {
    ArrayList ref = high_priority_entities;
    
    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      next_high_position = next_high_position >= size ? 0 : next_high_position;  //make circular
      RateControlledEntity entity = (RateControlledEntity)ref.get( next_high_position );
      next_high_position++;
      num_checked++;
      if( entity.canProcess( write_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }
  
  
  
  /**
   * Add the given entity to the controller for write processing.
   * @param entity to process writes for
   */
  public void addWriteEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities.size() + 1 );
        high_new.addAll( high_priority_entities );
        high_new.add( entity );
        high_priority_entities = high_new;
      }
      else {
        //copy-on-write
        ArrayList norm_new = new ArrayList( normal_priority_entities.size() + 1 );
        norm_new.addAll( normal_priority_entities );
        norm_new.add( entity );
        normal_priority_entities = norm_new;
      }
    }
    finally {  entities_mon.exit();  }
  }
  
  
  /**
   * Remove the given entity from the controller.
   * @param entity to remove from write processing
   */
  public void removeWriteEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities );
        high_new.remove( entity );
        high_priority_entities = high_new;
      }
      else {
        //copy-on-write
        ArrayList norm_new = new ArrayList( normal_priority_entities );
        norm_new.remove( entity );
        normal_priority_entities = norm_new;
      }
    }
    finally {  entities_mon.exit();  }
  }
}
