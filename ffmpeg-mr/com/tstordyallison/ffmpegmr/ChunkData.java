package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hadoop.io.Writable;

import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;

public class ChunkData implements Writable {

	// List of ChunkIDs that this piece of data is used by.
	private Set<ChunkID> chunkIDs = Collections.synchronizedSet(new HashSet<ChunkID>());
	private AtomicBoolean dying = new AtomicBoolean(false);
	
	// Write mode.
	private byte[] header = null;
	private List<DemuxPacket> data = null;
	private long pktssize = -1;
	
	// Read mode.
	private byte[] rawData = null;
	
	public ChunkData()
	{
		this.header = null;
		this.data = null;
	}
	public ChunkData(byte[] header, List<DemuxPacket> packets)
	{
		this.header = header;
		this.data = packets;
	}
	public ChunkData(List<DemuxPacket> packets)
	{
		this.header = null;
		this.data = packets;
	}
	
	public byte[] getData()
	{
		// TODO copy the data from the byte buffer to a new array for completeness.
		if(rawData == null)
			throw new RuntimeException("Data is not available - fix me!");
		
		return rawData;
	}
	
	public long getSize()
	{
		if(pktssize ==  -1)
		{
			if(rawData != null)
				return rawData.length;
			else
				return 0;
		}
		else
			if(header != null)
				return pktssize + header.length;
			else
				return 0;
	}
	
	public void givePacketsSizeHint(long pktssize)
	{
		this.pktssize = pktssize;
	}
	
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeLong(getSize());
		if(header != null)
			out.write(header);
		if(data != null)
		{
			Printer.println("Outputing data chunk: " + this.toString());
			for(DemuxPacket pkt : data)
			{	
				if(pkt.data == null){}
					//System.err.println("Null data in DemuxPacket (" + pkt.toString() + ")");
				else
				{
					byte[] dst = new byte[pkt.data.limit()];
					pkt.data.get(dst); // This is the copy across to java.
					out.write(dst); // This is write to the FS.
				}
			}
		}
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		long size = in.readLong();
		rawData = new byte[(int) size];
		in.readFully(rawData); // Internally this data is all delimited anyway.
	}

	// Alloc/dealloc management.
	public void retain(ChunkID chunkID)
	{
		synchronized (chunkIDs) {
			if(!dying.get())
				chunkIDs.add(chunkID);
			else
				throw new RuntimeException("This ChunkData has already been deallocated.");
		}
	}
	
	public void dealloc(ChunkID chunkID) {
		synchronized (chunkIDs) {
			chunkIDs.remove(chunkID);
			
			if(chunkIDs.isEmpty()){
				dying.set(true);
				
				if(data != null){
					for(DemuxPacket pkt : data)
					{	
						pkt.deallocData();
					}
				}
			}
		}
	}

	@Override
	public String toString() {
		final int maxLen = 10;
		return "ChunkData ["
				+ "\n\t\thashCode=" + super.hashCode()
				+ "\n\t\tsize=" + FileUtils.humanReadableByteCount(this.getSize(), false)
				+ (header != null ? "\n\t\theader="
						+ Arrays.toString(Arrays.copyOf(header,
								Math.min(header.length, maxLen))) + "..., "
						: "")
				+ (data != null ? "\n\t\tdata=" + toString(data, 2) + "..., "
						: "")
				+ (rawData != null ? "\n\t\trawData="
						+ Arrays.toString(Arrays.copyOf(rawData,
								Math.min(rawData.length, maxLen))) + "..." : "")
				+ "\n]";
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
}