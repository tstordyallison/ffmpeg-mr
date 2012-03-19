package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.Writable;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

public class ChunkID implements Writable, Comparable<ChunkID> {
	// TODO: Sort this mess out. This started off as a struct, but is now an object.  
	
	public long chunkNumber;
	public int streamID;
	public long startTS; 	
	public long endTS; 
	public long tbNum;
	public long tbDen;
	public List<Long> outputChunkPoints = new ArrayList<Long>(); // Stores the extra points at which this chunk will split on encode
	public long streamDuration;
	
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(streamID);
		out.writeLong(chunkNumber);
		out.writeLong(startTS);
		out.writeLong(endTS);
		out.writeLong(tbNum);
		out.writeLong(tbDen);
		out.writeLong(streamDuration);
		StringBuilder sb = new StringBuilder();
		for(Long point : outputChunkPoints){
			sb.append(point);
			sb.append(",");
		}
		out.writeUTF(sb.toString());
	}
	@Override
	public void readFields(DataInput in) throws IOException {
		streamID = in.readInt();
		chunkNumber = in.readLong();
		startTS = in.readLong();
		endTS = in.readLong();
		tbNum = in.readLong();
		tbDen = in.readLong();
		streamDuration = in.readLong();
		outputChunkPoints =  new ArrayList<Long>();
		String input = in.readUTF();
		if(!input.isEmpty())
		{
			List<String> chunkPoints = Arrays.asList(input.split(","));
				for(String point : chunkPoints)
					outputChunkPoints.add(Long.parseLong(point));
		}
	}
	
	@Override
	public String toString() {
		try{
			return "ChunkID [" 
				+ "\n\t\tstreamID =\t " + streamID + ", " 
				+ "\n\t\tchunkNumber =\t " + chunkNumber + ", " 
				+ "\n\t\tstartTS =\t " + this.getMillisecondsStartTs() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsStartTs())) + "), "
				+ "\n\t\tendTS =\t\t~" + this.getMillisecondsEndTs() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsEndTs())) + "), "
				+ "\n\t\tduration =\t~" + this.getMillisecondsDuration() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsDuration())) + "), "
				+ (outputChunkPoints.size() > 0 ? "\n\t\tchunkPoints =\t " + toStringChunkPoint() : "")
				+ "\n]";
		}
		catch(RuntimeException e)
		{
			e.printStackTrace();
			return "";
		}
	}
	
	@Override
	public int compareTo(ChunkID o) {
		return this.toString().compareTo(o.toString());
	}
	
	private String toStringChunkPoint() {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<Long> iterator = outputChunkPoints.iterator(); iterator.hasNext(); i++) {
			if (i > 0)
				builder.append(", ");
			long value = iterator.next();
			builder.append(value);
			builder.append(" (" + toMs(value, tbNum, tbDen) + "ms)");
		}
		builder.append("]");
		return builder.toString();
	}

	public long getMillisecondsStartTs()
	{
		return toMs(startTS, tbNum, tbDen);
	}
	public long getMillisecondsEndTs()
	{
		return toMs(endTS, tbNum, tbDen);
	}
	public long getMillisecondsDuration()
	{
		return toMs(endTS-startTS, tbNum, tbDen);
	}
	
	public static long toMs(long value, long tbNum, long tbDen){
		return (long)(((value)*tbNum)/((double)tbDen/1000));
	}
	
}