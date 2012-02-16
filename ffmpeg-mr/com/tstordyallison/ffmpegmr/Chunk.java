package com.tstordyallison.ffmpegmr;


public class Chunk {
	private ChunkID chunkID;
	private ChunkData chunkData;
	
	public Chunk(ChunkID chunkID, ChunkData chunkData) {
		this.chunkID = chunkID;
		this.chunkData = chunkData;
		if(this.chunkData != null)
			this.chunkData.retain(chunkID);
	}
	
	public ChunkID getChunkID(){
		return this.chunkID;
	}
	
	public ChunkData getChunkData(){
		return this.chunkData;
	}
	
	public void dealloc() {
		chunkData.dealloc(this.chunkID);
	}

	@Override
	public String toString() {
		return "Chunk ["
				+ (chunkID != null ? "\n\t\t" + chunkID + ", " : "")
				+ (chunkData != null ? "\n\t\t" + chunkData : "") + "\n]";
	}
}