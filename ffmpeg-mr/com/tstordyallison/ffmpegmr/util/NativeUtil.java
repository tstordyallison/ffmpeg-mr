package com.tstordyallison.ffmpegmr.util;


public class NativeUtil {

	public static void loadFFmpegMR()
	{
		loadLibraryWithFailureAllowed("mp3lame");
		loadLibraryWithFailureAllowed("faac");
		loadLibraryWithFailureAllowed("x264");
		loadLibraryWithFailureAllowed("avutil");
		loadLibraryWithFailureAllowed("avcodec");
		loadLibraryWithFailureAllowed("avformat");
		loadLibraryWithFailureAllowed("swscale");
		System.loadLibrary("ffmpeg-mr");
	}
	
	private static void loadLibraryWithFailureAllowed(String libname){
		try{
			System.loadLibrary(libname);
		}
		catch(UnsatisfiedLinkError e)
		{
			//System.err.println("WARNING: loadLibrary() call for " + libname + " failed. Dependencies may also fail.");
		}
	}
}
