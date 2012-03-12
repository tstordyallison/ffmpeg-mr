package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.Writable;

public class Chunk implements Writable {
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
}