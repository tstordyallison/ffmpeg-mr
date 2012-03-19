package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.Writable;

public class Chunk implements Writable, Comparable<Chunk> {
	private ChunkID chunkID;
	private ChunkData chunkData;
	
	public Chunk()
	{
		this.chunkID = null;
		this.chunkData = null;
	}
	
	public Chunk(ChunkID chunkID, ChunkData chunkData) {
		this.chunkID = chunkID;
		this.chunkData = chunkData;
	}
	
	public ChunkID getChunkID(){
		return this.chunkID;
	}
	
	public ChunkData getChunkData(){
		return this.chunkData;
	}

	@Override
	public String toString() {
		return "Chunk ["
				+ (chunkID != null ? "\n\t\t" + chunkID + ", " : "")
				+ (chunkData != null ? "\n\t\t" + chunkData : "") + "\n]";
	}

	@Override
	public void write(DataOutput out) throws IOException {
		chunkID.write(out);
		chunkData.write(out);
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		chunkID = new ChunkID();
		chunkID.readFields(in);
		chunkData = new ChunkData();
		chunkData.readFields(in);
	}
	
	@Override
	public int compareTo(Chunk o) {
		if(chunkID != null)
		{
			return chunkID.compareTo(o.chunkID);
		}
		else
			return 0;
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((chunkData == null) ? 0 : chunkData.hashCode());
		result = prime * result + ((chunkID == null) ? 0 : chunkID.hashCode());
		return result;
	}

	@Override
	
    public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Chunk))
			return false;
		Chunk other = (Chunk) obj;
		if (chunkData == null) {
			if (other.chunkData != null)
				return false;
		} else if (!chunkData.equals(other.chunkData))
			return false;
		if (chunkID == null) {
			if (other.chunkID != null)
				return false;
		} else if (!chunkID.equals(other.chunkID))
			return false;
		return true;
	}

	
}