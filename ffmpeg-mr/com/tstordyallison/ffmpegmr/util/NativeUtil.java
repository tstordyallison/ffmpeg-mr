package com.tstordyallison.ffmpegmr.util;


public class NativeUtil {

	public static void loadFFmpegMR()
	{
        if(!System.getProperty("os.arch").contains("64"))
			throw new RuntimeException("Only 64bit Linux platforms are currently supported on the TaskTrackers.");
		
		loadLibraryWithFailureAllowed("mp3lame");
		loadLibraryWithFailureAllowed("faac");
		loadLibraryWithFailureAllowed("x264");
		loadLibraryWithFailureAllowed("avutil");
		loadLibraryWithFailureAllowed("avcodec");
		loadLibraryWithFailureAllowed("avformat");
		System.loadLibrary("ffmpeg-mr");
	}
	
	private static void loadLibraryWithFailureAllowed(String libname){
		try{
			System.loadLibrary(libname);
		}
		catch(UnsatisfiedLinkError e)
		{
			Printer.println("WARNING: loadLibrary() call for " + libname + " failed. Dependencies may also fail.");
		}
	}
}
