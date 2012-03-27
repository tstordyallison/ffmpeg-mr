package com.tstordyallison.ffmpegmr.util;

import org.joda.time.DateTime;

public class Printer {
	
	public static boolean ENABLED = true;
	public static boolean USE_ERR = true;

	public static void println(String text)
	{
		if(ENABLED)
		{
			DateTime dt = new DateTime();
			String dtString = "[" + dt.toString("HH:mm:ss") + "] ";
			if(USE_ERR)
				System.err.println(dtString+text);
			else
				System.out.println(dtString+text);
		}
			
	}
	
	public static void print(String text)
	{	
		DateTime dt = new DateTime();
		String dtString = "[" + dt.toString("HH:mm:ss") + "] ";
		if(ENABLED)
		{
			if(USE_ERR)
				System.err.print(dtString+text);
			else
				System.out.print(dtString+text);
		}
	}
}
