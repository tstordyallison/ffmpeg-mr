package com.tstordyallison.ffmpegmr;

import java.io.InputStream;

public class Demuxer {
	
	static{
		System.loadLibrary("ffmpeg-mr");
	}
	
	public static class DemuxPacket {
		public int streamID;
		public boolean splitPoint;
		public long dts;
		public byte[] data;
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
	public native DemuxPacket getNextChunk();
	public DemuxPacket getNextChunk2()
	{
		return getNextChunk();
	}
	public native int getStreamCount();
	public native int close();
}
