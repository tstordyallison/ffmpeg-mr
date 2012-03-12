package com.tstordyallison.ffmpegmr;

public class DemuxPacket {
	public int streamID;
	public boolean splitPoint; 	// In the context of demuxing, this is a keyframe. In the transcoder this is a split point.
	public long ts; 			// In microseconds.
	public long duration; 		// In microseconds - 0 if unknown.
	public byte[] data;
	
//	public ByteBuffer data;
//	public native int deallocData(); // Please for the love of god call me if I'm allocd!
//	protected void finalize() throws Throwable {
//	    try {
//	    	// In case someone forgets... 
//	        if(deallocData() == 0)
//	        	System.err.println("Leaked:" + toString());
//	    } finally {
//	        super.finalize();
//	    }
//	}

	@Override
	public String toString() {
		return "[streamID=" + streamID + ", splitPoint="
				+ splitPoint + ", ts=" + ts + ", duration=" + duration + ", "
				+ (data != null ? "data=" + data.length + " bytes" : "") + "]";
	}
}