package com.tstordyallison.ffmpegmr;

import java.io.InputStream;

public class Demuxer {
	
	static{
		System.loadLibrary("ffmpeg-mr");
	}
	
	public Demuxer(String filename){
		int err;
		if((err = initDemuxWithFile(filename)) != 0)
			throw new RuntimeException("Native init failed with code " + err + ". See stderr for more info.");
	}
	
	public Demuxer(InputStream stream){
		initDemuxWithStream(stream);
	}
	
	private native int initDemuxWithFile(String filename);
	private native int initDemuxWithStream(InputStream stream);
	
	public native byte[] getStreamData(int streamID);
	
	public native DemuxPacket getNextChunkImpl();
	public DemuxPacket getNextChunk()
	{
		return getNextChunkImpl();
	}
	
	public native int getStreamCount();
	public native int close();
	
	protected void finalize() throws Throwable {
	    try {
	    	// In case someone forgets...
	        if(close() == 0)
	        	System.err.println("Demux finalizer caught leak - the demuxer close() method should be called manually.");
	    } finally {
	        super.finalize();
	    }
	}
}
