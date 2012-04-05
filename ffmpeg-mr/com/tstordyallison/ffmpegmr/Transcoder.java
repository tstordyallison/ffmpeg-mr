package com.tstordyallison.ffmpegmr;

import java.util.List;

import com.tstordyallison.ffmpegmr.util.NativeUtil;

public class Transcoder
{   
	
	static{
		NativeUtil.loadFFmpegMR();
	}
	
	// For now, we will just have a fixed output of:
	// MKV container
	// H.264 video
	// AAC audio. 
	// Other streams will be ignored.
	
	public Transcoder(long chunkpointNum, long chunkpointDen, List<Long> outputChunkPoints, byte[] data)
	{
		this(chunkpointNum, chunkpointDen, outputChunkPoints, data, 0, 0, 0, 0, 0);
	}
	
	public Transcoder(long chunkpointNum, long chunkpointDen, List<Long> outputChunkPoints, byte[] data, 
					  double videoResScale, double videoCrf, int videoBitrate, int audioBitrate, int videoThreads) {
		int err;
		long[] chunkPointsNative = new long[outputChunkPoints.size()];
		for(int i = 0; i < outputChunkPoints.size(); i++)
			chunkPointsNative[i] = outputChunkPoints.get(i);
			
		if((err = initWithBytes(chunkpointNum, chunkpointDen, chunkPointsNative, data, 
								videoResScale, videoCrf, videoBitrate, audioBitrate, videoThreads)) != 0)
			throw new RuntimeException("Transcoder native init failed with code " + err + ". See stderr for more info.");	
	}

	private native int initWithBytes(long chunkpointNum, long chunkpointDen, long[] chunkPointsNative, byte[] data, 
									 double videoResScale, double videoCrf, int videoBitrate, int audioBitrate, int videoThreads);
	public native DemuxPacket getNextPacket();
	public native byte[] getStreamData();
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