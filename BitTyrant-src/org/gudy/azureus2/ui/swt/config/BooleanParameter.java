/*
 * Created on 9 juil. 2003
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
package org.gudy.azureus2.ui.swt.config;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.ui.swt.config.generic.GenericBooleanParameter;

/**
 * @author Olivier
 * 
 */
public class BooleanParameter extends Parameter{
  protected GenericBooleanParameter	delegate;
  
  public BooleanParameter(Composite composite, final String name) {
	  delegate = new GenericBooleanParameter( config_adapter, composite,name,COConfigurationManager.getBooleanParameter(name),null,null);
  }

  public BooleanParameter(Composite composite, final String name, String textKey) {
	  delegate = new GenericBooleanParameter( config_adapter, composite, name, COConfigurationManager.getBooleanParameter(name),
         textKey, null);
  }

  public BooleanParameter(Composite composite, final String name, boolean defaultValue, String textKey) {
	  delegate = new GenericBooleanParameter( config_adapter, composite,name,defaultValue,textKey,null);
  }

  public BooleanParameter(Composite composite, final String name, boolean defaultValue) {
	  delegate = new GenericBooleanParameter( config_adapter, composite,name,defaultValue,null,null);
  }
  
  public 
  BooleanParameter(
  		Composite composite, 
		final String _name, 
        boolean _defaultValue,
        String textKey,
        IAdditionalActionPerformer actionPerformer) 
  {
	  delegate = new GenericBooleanParameter( config_adapter, composite, _name, _defaultValue, textKey, actionPerformer );
  }

  public void setLayoutData(Object layoutData) {
   delegate.setLayoutData( layoutData );
  }
  
  public void setAdditionalActionPerformer(IAdditionalActionPerformer actionPerformer) {
	 delegate.setAdditionalActionPerformer( actionPerformer );
  }
 
  public Control getControl() {
    return delegate.getControl();
  }
  
  public String getName() {
  	return delegate.getName();
  }
  
  public void setName(String newName) {
  	delegate.setName( newName );
  }

  public boolean
  isSelected()
  {
  	return(delegate.isSelected());
  }
  
  public void
  setSelected(
  	boolean	selected )
  {
  	delegate.setSelected( selected );
  }
}
