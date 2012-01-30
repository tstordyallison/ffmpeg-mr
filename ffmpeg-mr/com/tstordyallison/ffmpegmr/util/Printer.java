package com.tstordyallison.ffmpegmr.util;

public class Printer {
	
	public static boolean ENABLED = false;

	public static void println(String text)
	{
		if(ENABLED)
			System.out.println(text);
	}
	
	public static void print(String text)
	{	
		if(ENABLED)
			System.out.print(text);
	}
}
