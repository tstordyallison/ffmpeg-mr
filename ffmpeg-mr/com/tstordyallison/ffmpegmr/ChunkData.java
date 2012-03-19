package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.io.Writable;

import com.tstordyallison.ffmpegmr.util.FileUtils;

public class ChunkData implements Writable {

	private byte[] rawData = null; // The raw binary data that this chunk stores.
	public int packet_count; // Number of packets stored in this chunk.
	
	public ChunkData()
	{
		this.rawData = null;
		this.packet_count = -1;
	}
	public ChunkData(List<DemuxPacket> packets)
	{
		this(null, packets);
	}
	public ChunkData(byte[] header, List<DemuxPacket> packets)
	{
		// Count up the size of the demux packets.
		int size = 0;
		for(DemuxPacket pkt : packets)
		{
			size += pkt.data.length;
		}
		
		// Add the header if we have one.
		if(header != null)
			size += header.length;
		
		// Build the rawData array.
		rawData = new byte[size];
		
		int cursor = 0;
		
		// Copy the header
		if(header != null)
		{
			System.arraycopy(header, 0, rawData, cursor, header.length);
			cursor += header.length;
		}
		
		// Copy each of the packets.
		for(DemuxPacket pkt : packets)
		{
			System.arraycopy(pkt.data, 0, rawData, cursor, pkt.data.length);
			cursor += pkt.data.length;
		}
		
		this.packet_count = packets.size();
	}
	
	public byte[] getData()
	{
		return rawData;
	}
	
	public long getSize()
	{
		return rawData.length;
	}
	
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(packet_count);
		out.writeInt(rawData.length);
		out.write(rawData);
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		this.packet_count = in.readInt();
		int size = in.readInt();
		rawData = new byte[size];
		in.readFully(rawData); // Internally this data is all delimited using TPL anyway.
	}
	
	@Override
	public String toString() {
		final int maxLen = 10;
		return "ChunkData ["
				+ "\n\t\thashCode=" + super.hashCode()
				+ "\n\t\tsize=" + FileUtils.humanReadableByteCount(this.getSize(), false)
				+ (rawData != null ? "\n\t\trawData="
						+ Arrays.toString(Arrays.copyOf(rawData,
								Math.min(rawData.length, maxLen))) + "..." : "")
				+ "\n]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + packet_count;
		result = prime * result + Arrays.hashCode(rawData);
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ChunkData))
			return false;
		ChunkData other = (ChunkData) obj;
		if (packet_count != other.packet_count)
			return false;
		if (!Arrays.equals(rawData, other.rawData))
			return false;
		return true;
	}
}