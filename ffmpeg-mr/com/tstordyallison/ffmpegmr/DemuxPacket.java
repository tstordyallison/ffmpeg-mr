package com.tstordyallison.ffmpegmr;

public class DemuxPacket {
	public int streamID;
	public boolean splitPoint; 	// In the context of demuxing, this is a keyframe. In the transcoder this is a split point.
	public long ts;
	public long tb_den;
	public long tb_num;
	public long duration; 		
	public byte[] data;

	@Override
	public String toString() {
		return "[streamID=" + streamID + ", splitPoint="
				+ splitPoint + ", ts=" + ts + ", duration=" + duration + ", "
				+ (data != null ? "data=" + data.length + " bytes" : "") + "]";
	}
}