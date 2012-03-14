package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

public class ChunkID implements Writable, WritableComparable<ChunkID>, Comparable<ChunkID> {
	// TODO: Sort this mess out. This started off as a struct, but is now an object.  
	
	public int streamID;    	// Input file stream ID. 
	public long startTS; 		// The first ts value in this chunk.
	public long endTS; 			// The last ts value in this chunk + duration of the last pkt.
	public long chunkNumber; 	// Numerical counter for debugging.
	public long tbNum;
	public long tbDen;
	public List<Long> outputChunkPoints = new ArrayList<Long>(); // Stores the extra points at which this chunk will split on encode
	
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(streamID);
		out.writeLong(startTS);
		out.writeLong(endTS);
		out.writeLong(chunkNumber);
		out.writeLong(tbNum);
		out.writeLong(tbDen);
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
		startTS = in.readLong();
		endTS = in.readLong();
		chunkNumber = in.readLong();
		tbNum = in.readLong();
		tbDen = in.readLong();
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
			return "ChunkID [\n\t\tstreamID =\t" + streamID + ", " 
				+ "\n\t\tstartTS =\t" + this.getMillisecondsStartTs() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsStartTs())) + "), "
				+ "\n\t\tendTS =\t\t" + this.getMillisecondsEndTs() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsEndTs())) + "), "
				+ "\n\t\tduration =\t" + this.getMillisecondsDuration() + "ms (" + PeriodFormat.getDefault().print(new Period(this.getMillisecondsDuration())) + "), "
				//+ "\n\t\ttb=\t\t" + String.format("%d/%d (%2.2f)", tbNum, tbDen, (double)tbDen/tbNum)
				+ "\n\t\tchunkPoints =\t" + toString(outputChunkPoints, Integer.MAX_VALUE)
				+ "\n\t\tchunkNumber =\t" + chunkNumber + "\n]";
		}
		catch(RuntimeException e)
		{
			e.printStackTrace();
			return "";
		}
	}
	
	@Override
	public int compareTo(ChunkID o) {
		// TODO: test compareTo for the partitioner sorting job.
		return (int)(this.startTS - o.startTS);
	}
	
	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator
				.hasNext() && i < maxLen; i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}

	public long getMillisecondsStartTs()
	{
		return (long)((startTS*tbNum)/((double)tbDen/1000));
	}
	
	public long getMillisecondsEndTs()
	{
		return (long)((endTS*tbNum)/((double)tbDen/1000));
	}
	
	public long getMillisecondsDuration()
	{
		return (long)(((endTS-startTS)*tbNum)/((double)tbDen/1000));
	}
	
}