package com.tstordyallison.ffmpegmr;

import java.io.InputStream;

import com.tstordyallison.ffmpegmr.util.NativeUtil;

public class Demuxer {
	
	// From avutil.h:
//	enum AVMediaType {
//	    AVMEDIA_TYPE_UNKNOWN = -1,  ///< Usually treated as AVMEDIA_TYPE_DATA
//	    AVMEDIA_TYPE_VIDEO,
//	    AVMEDIA_TYPE_AUDIO,
//	    AVMEDIA_TYPE_DATA,          ///< Opaque data information usually continuous
//	    AVMEDIA_TYPE_SUBTITLE,
//	    AVMEDIA_TYPE_ATTACHMENT,    ///< Opaque data information usually sparse
//	    AVMEDIA_TYPE_NB
//	};
	
	private static final int AVMEDIA_TYPE_UNKNOWN = -1;
	private static final int AVMEDIA_TYPE_VIDEO = 0;
	private static final int AVMEDIA_TYPE_AUDIO = 1;
	private static final int AVMEDIA_TYPE_DATA = 2;
	private static final int AVMEDIA_TYPE_SUBTITLE = 3;
	private static final int AVMEDIA_TYPE_ATTACHMENT = 4;
	private static final int AVMEDIA_TYPE_NB = 5;
	
	public enum AVMediaType {
	    UNKNOWN,
	    VIDEO,
	    AUDIO,
	    DATA,
	    SUBTITLE,
	    ATTACHMENT,
	    NB};
	
	static{
		NativeUtil.loadFFmpegMR();
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
	
	public native int getStreamCount();
	public native byte[] getStreamData(int streamID);
	public native DemuxPacket getNextChunkImpl();
	public DemuxPacket getNextChunk()
	{
		return getNextChunkImpl();
	}
	public AVMediaType getStreamMediaType(int streamID)
	{
		switch (getStreamMediaTypeRaw(streamID)) {
			case AVMEDIA_TYPE_UNKNOWN:
				return AVMediaType.UNKNOWN;
			case AVMEDIA_TYPE_VIDEO:
				return AVMediaType.VIDEO;
			case AVMEDIA_TYPE_AUDIO:
				return AVMediaType.AUDIO;
			case AVMEDIA_TYPE_DATA:
				return AVMediaType.DATA;
			case AVMEDIA_TYPE_SUBTITLE:
				return AVMediaType.SUBTITLE;
			case AVMEDIA_TYPE_ATTACHMENT:
				return AVMediaType.ATTACHMENT;
			default:
				return AVMediaType.UNKNOWN;
			}
	}
	private native int getStreamMediaTypeRaw(int streamID);
	public native long getDurationMs();
	
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
