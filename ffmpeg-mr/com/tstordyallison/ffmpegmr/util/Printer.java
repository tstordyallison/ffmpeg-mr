package com.tstordyallison.ffmpegmr.util;

public class Printer {
	
	public static boolean ENABLED = false;
	public static boolean USE_ERR = false;

	public static void println(String text)
	{
		if(ENABLED)
		{
			if(USE_ERR)
				System.err.println(text);
			else
				System.out.println(text);
		}
			
	}
	
	public static void print(String text)
	{	
		if(ENABLED)
		{
			if(USE_ERR)
				System.err.print(text);
			else
				System.out.print(text);
		}
	}
}
