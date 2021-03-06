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

/* Main Controller */
package org.eclipse.titan.codegenerator.TTCN3JavaAPI;

import java.io.BufferedReader;
import java.io.FileReader;

public class MC{

	//these will be retrieved from the config file later.
	private static int SERVERPORTNUM = 5557; 
	private static String MTCIP = "127.0.0.1"; //IP of machine that has to run the MTC
	private static int HCNUM = 1; //later from config file
	private static boolean DEBUGMODE = false;
	private static String TC = ""; //TC to be executed
	
	private static MCType mc;
	
	public static void main(String[] args){
		String tc = TC;
		
		try{
			BufferedReader in = new BufferedReader(new FileReader("src\\org\\eclipse\\titan\\codegenerator\\TTCN3JavaAPI\\cfg.cfg"));
			String line;
			while((line = in.readLine()) != null)
			{
			    tc=line;
			}
			in.close();
		}catch(Exception e){e.printStackTrace();}
		
		mc = new MCType(DEBUGMODE);
		mc.startmc(SERVERPORTNUM, MTCIP, tc, HCNUM);
		TTCN3Logger.writeLog("mc", "EXECUTOR", "Main Controller stopped", false);
	}

}
