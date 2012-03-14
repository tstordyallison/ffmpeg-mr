package com.tstordyallison.ffmpegmr;

public class DemuxPacket {
	public int streamID;
	public boolean splitPoint; 	// In the context of demuxing, this is a keyframe. In the transcoder this is a split point.
	public long ts;
	public long tb_den;
	public long tb_num;
	public long duration; 		
	public byte[] data;

	public long getMillisecondsTs()
	{
		return (long)((ts*tb_num)/((double)tb_den/1000));
	}
	
	public long getMillisecondsDuration()
	{
		return (long)((duration*tb_num)/((double)tb_den/1000));
	}
	
	@Override
	public String toString() {
		return "[streamID=" + streamID + ", splitPoint="
				+ splitPoint + ", ts=" + getMillisecondsTs() + ", duration=" + getMillisecondsDuration() + ", "
				+ (data != null ? "data=" + data.length + " bytes" : "") + "]";
	}
}