package com.tstordyallison.ffmpegmr;

import com.tstordyallison.ffmpegmr.util.NativeUtil;

public class Remuxer {
	
	static{
		NativeUtil.loadFFmpegMR();
	}
	
	public Remuxer(Iterable<Chunk> chunks){
		int err;
		if((err = initWithChunks(chunks)) != 0)
			throw new RuntimeException("Native init failed with code " + err + ". See stderr for more info.");
	}
	
	private native int initWithChunks(Iterable<Chunk> chunks);
	
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
