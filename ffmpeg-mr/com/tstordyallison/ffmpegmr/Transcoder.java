package com.tstordyallison.ffmpegmr;

import java.util.List;

public class Transcoder
{   
	
	static{
		System.loadLibrary("ffmpeg-mr");
	}
	
	// For now, we will just have a fixed output of:
	// MKV container
	// H.264 video from stream 0.
	// AAC audio from stream 1. 
	// All other streams will be ignored.
	public Transcoder(List<Long> outputChunkPoints, byte[] data) {
		int err;
		long[] chunkPointsNative = new long[outputChunkPoints.size()];
		for(int i = 0; i < outputChunkPoints.size(); i++)
			chunkPointsNative[i] = outputChunkPoints.get(i);
			
		if((err = initWithBytes(chunkPointsNative, data)) != 0)
			throw new RuntimeException("Transcoder native init failed with code " + err + ". See stderr for more info.");	
	}

	private native int initWithBytes(long[] chunkPointsNative, byte[] data);
	public native DemuxPacket getNextPacket();
	public native int close();
	
	protected void finalize() throws Throwable {
	    try {
	    	// In case someone forgets...
	        if(close() == 0)
	        	System.err.println("Transcoder finalizer caught leak - the Transcoder close() method should be called manually.");
	    } finally {
	        super.finalize();
	    }
	}
}