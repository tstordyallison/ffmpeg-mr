package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.Writable;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

public class ChunkID implements Writable, Comparable<ChunkID> {
	
	private long streamDuration = Long.MAX_VALUE;
	private long chunkNumber = -1;
	private int streamID = -1; // THIS IS READ BY NATIVE CODE IN THE REMUXER - CAREFUL!
	private long startTS = 0; 	
	private long endTS = -1; 
	private long tbNum = 0;
	private long tbDen = 1;
	private List<Long> outputChunkPoints = new ArrayList<Long>(); // Stores the extra points at which this chunk will split on encode
	
	public boolean written = false;
	
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
		
		this.written = true; // This prevents modification.
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

	public long getChunkNumber() {
		return chunkNumber;
	}
	public void setChunkNumber(long chunkNumber) {
		writtenCheck();
		this.chunkNumber = chunkNumber;
	}
	public int getStreamID() {
		return streamID;
	}
	public void setStreamID(int streamID) {
		writtenCheck();
		this.streamID = streamID;
	}
	public long getStartTS() {
		return startTS;
	}
	public void setStartTS(long startTS) {
		writtenCheck();
		this.startTS = startTS;
	}
	public long getEndTS() {
		return endTS;
	}
	public void setEndTS(long endTS) {
		writtenCheck();
		this.endTS = endTS;
	}
	public long getTbNum() {
		return tbNum;
	}
	public void setTbNum(long tbNum) {
		writtenCheck();
		this.tbNum = tbNum;
	}
	public long getTbDen() {
		return tbDen;
	}
	public void setTbDen(long tbDen) {
		writtenCheck();
		this.tbDen = tbDen;
	}
	public List<Long> getOutputChunkPoints() {
		if(written)
			return Collections.unmodifiableList(outputChunkPoints);
		else
			return outputChunkPoints;
	}
	public void setOutputChunkPoints(List<Long> outputChunkPoints) {
		writtenCheck();
		this.outputChunkPoints = outputChunkPoints;
	}
	public long getStreamDuration() {
		return streamDuration;
	}
	public void setStreamDuration(long streamDuration) {
		writtenCheck();
		this.streamDuration = streamDuration;
	}
	
	public boolean isModifiable()
	{
		return !written;
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
	
	@Override
	public int compareTo(ChunkID o) {
		return this.toString().compareTo(o.toString());
	}
	
	public static long toMs(long value, long tbNum, long tbDen){
		return (long)(((value)*tbNum)/((double)tbDen/1000));
	}
	
	private void writtenCheck()
	{
		if(written)
			throw new RuntimeException("This chunkID has been written and is no longer mutable.");
	}
}