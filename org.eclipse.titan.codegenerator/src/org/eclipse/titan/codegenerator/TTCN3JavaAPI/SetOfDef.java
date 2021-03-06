/******************************************************************************
 * Copyright (c) 2000-2018 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *   
 *   Keremi, Andras
 *   Eros, Levente
 *   Kovacs, Gabor
 *
 ******************************************************************************/

package org.eclipse.titan.codegenerator.TTCN3JavaAPI;

import java.util.HashSet;
import java.util.Iterator;

public abstract class SetOfDef<T extends TypeDef> extends StructuredTypeDef {
    public HashSet<T> value;
    
    public String toString(){
    	return toString("");
    }
    
    public String toString(String tabs){
		if(anyField) return "?";
		if(omitField) return "omit";
		if(anyOrOmitField) return "*";
    	String retv = "[";
    	Iterator<T> i = value.iterator();
    	while(i.hasNext()){
    		retv += i.next().toString(tabs);
    		if(i.hasNext()) retv += ",";
    	}
    	retv += "]";
    	return retv;
    }
}