package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.BlockingQueue;

import com.tstordyallison.ffmpegmr.util.Printer;

public class ChunkerThread extends Thread {
	
	public class ChunkBuffers {
		
		private List<ByteBuffer> 		streamHeaders 				= new ArrayList<ByteBuffer>(demuxer.getStreamCount()); 		
		private List<List<DemuxPacket>> currentChunks				= new ArrayList<List<DemuxPacket>>(demuxer.getStreamCount()); 
		private List<Integer> 			currentChunksSizes 			= new ArrayList<Integer>(demuxer.getStreamCount()); 			
		private List<Integer> 			currentChunkIDCounters		= new ArrayList<Integer>(demuxer.getStreamCount()); 
		private List<Integer> 			endMarkers 					= new ArrayList<Integer>(demuxer.getStreamCount()); 
		private List<Long> 				chunkPoints 				= new LinkedList<Long>(); // Make me a better data structure.
		
		public ChunkBuffers()
		{
			for(int i = 0; i < demuxer.getStreamCount(); i++)
			{
				streamHeaders.add(ByteBuffer.wrap(demuxer.getStreamData(i)));
				currentChunks.add(new LinkedList<DemuxPacket>()); // Linked list makes quite a difference.
				currentChunksSizes.add(0);
				currentChunkIDCounters.add(0);
				endMarkers.add(-1);
			}
		}

		public void add(DemuxPacket currentPacket) {
			currentChunks.get(currentPacket.streamID).add(currentPacket);
			currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) + currentPacket.data.limit());
			
			// Mark this if it is a split point.
			if(currentPacket.splitPoint)
				endMarkers.set(currentPacket.streamID, currentChunks.get(currentPacket.streamID).size()-1);
		}

		public long getBufferSize(int streamID) {
			return streamHeaders.get(streamID).limit() + 
			   currentChunksSizes.get(streamID);
		}

		public Chunk drainChunk(int streamID) {
			
			// Get stream state.
			List<DemuxPacket> chunkBuffer = currentChunks.get(streamID);
			if(chunkBuffer.size() <= 0)
				return null;
			
			// Get the header.
			ByteBuffer header = streamHeaders.get(streamID);
			
			// Check the end markers, TODO: add logic for end markers that are in the middle of GOPs.
			int endMarker = endMarkers.get(streamID); // This is exclusive of the end marker, e.g. the end marker is the start of the next chunk.
			if(endMarker == 0) // Only one frame!
				throw new RuntimeException("We can't drain just yet!");
			else if(endMarker > chunkBuffer.size()-1) 
				endMarker = chunkBuffer.size(); // Too far, we'll just take them all.
				
			// Build a new chunk ID.
			ChunkID chunkID = new ChunkID();
			chunkID.chunkNumber = currentChunkIDCounters.get(streamID);
			chunkID.streamID = streamID;
			chunkID.startTS = chunkBuffer.get(0).ts;
			DemuxPacket lastPacket = chunkBuffer.get(endMarker-1);
			chunkID.endTS = lastPacket.ts + lastPacket.duration;
			
			// Find all of the output chunk points that apply to this chunk.
			for(Long point : chunkPoints)
			{
				if(chunkID.startTS < point && point < chunkID.endTS)
					chunkID.outputChunkPoints.add(point);
			}
			
			// Build the chunk data.
			ChunkData chunkData = new ChunkData(header.array(), new LinkedList<DemuxPacket>(chunkBuffer.subList(0, endMarker)));
			
			// Calculate what is left, dealloc the demux packets, and remove them from the buffer.
			int actualChunkSize = 0;
			ListIterator<DemuxPacket> it = chunkBuffer.subList(0, endMarker).listIterator();
			while(it.hasNext())
			{
				DemuxPacket currPacket = it.next();
				actualChunkSize += currPacket.data.limit();
				it.remove();
			}
			
			// Give the data its size hint.
			chunkData.givePacketsSizeHint(actualChunkSize);
			
			// Store the left over counter and increment the chunk counter, invalidate the end marker.
			currentChunksSizes.set(streamID, currentChunksSizes.get(streamID) - actualChunkSize);
			currentChunkIDCounters.set(streamID, currentChunkIDCounters.get(streamID) + 1);
			endMarkers.set(streamID, -1);
			
			// Add this to our chunkPoints.
			chunkPoints.add(chunkID.endTS);
			
			// Return the new chunk (this also calls retain on the data so we dealloc correctly).
			return new Chunk(chunkID, chunkData);
		}
		
		public void deallocBuffers()
		{
			// TODO dealloc buffers - this is if we need to stop suddenly.
		}

		public void setMaxEndMarker(int streamID){
			endMarkers.set(streamID, Integer.MAX_VALUE);
		}
		
		@Override
		public String toString() {
			final int maxLen = 20;
			return "ChunkBuffers ["
					+ (streamHeaders != null ? "\n\t\tstreamHeaders="
							+ streamHeaders.subList(0,
									Math.min(streamHeaders.size(), maxLen))
							+ ", " : "")
					+ (currentChunks != null ? "\n\t\tcurrentChunks="
							+ currentChunks.subList(0,
									Math.min(currentChunks.size(), maxLen))
							+ ", " : "")
					+ (currentChunksSizes != null ? "\n\t\tcurrentChunksSizes="
							+ currentChunksSizes
									.subList(0, Math.min(
											currentChunksSizes.size(), maxLen))
							+ ", " : "")
					+ (currentChunkIDCounters != null ? "\n\t\tcurrentChunkIDCounters="
							+ currentChunkIDCounters.subList(0, Math.min(
									currentChunkIDCounters.size(), maxLen))
							+ ", "
							: "")
					+ (endMarkers != null ? "\n\t\tendMarkers="
							+ endMarkers.subList(0,
									Math.min(endMarkers.size(), maxLen)) : "")
					+ (chunkPoints != null ? "\n\t\tchunkPoints="
							+ chunkPoints.subList(0,
									Math.min(chunkPoints.size(), 20)) : "")
					+ "\n]";
		}
		
	}

	private BlockingQueue<Chunk> chunkQ;
	private File file;
	private long blockSize;
	
	private Demuxer demuxer;
	private ChunkBuffers chunkBuffers;

	public ChunkerThread(BlockingQueue<Chunk> chunkQ, File file, long blockSize) {
		initDemuxer(chunkQ, file, blockSize);
	}

	public ChunkerThread(BlockingQueue<Chunk> chunkQ, File file, long blockSize, String name) {
		super(name);
		initDemuxer(chunkQ, file, blockSize);
	}

	public ChunkerThread(BlockingQueue<Chunk> chunkQ, File file, long blockSize, ThreadGroup group, String name) {
		super(group, name);
		initDemuxer(chunkQ, file, blockSize);
	}
	
	public void initDemuxer(BlockingQueue<Chunk> chunkQ, File file, long blockSize)
	{
		this.chunkQ = chunkQ;
		this.file = file;
		this.blockSize = blockSize;
		
		demuxer = new Demuxer(this.file.getAbsolutePath());
		chunkBuffers = new ChunkBuffers();
	}

	@Override
	public void run() {
		// Get new chunks from FFmpeg.
		try{
			DemuxPacket currentPacket = demuxer.getNextChunk();
			while(currentPacket != null)
			{
				// Add this packet to the ChunkBuffer.
				chunkBuffers.add(currentPacket);
				
				// Check to see if we are now over our limit.
				if(chunkBuffers.getBufferSize(currentPacket.streamID) > blockSize)
				{
					Chunk chunk = chunkBuffers.drainChunk(currentPacket.streamID);
					chunkQ.put(chunk); // This will block until the queue has space.
				}
				
				// Get the next packet.
				currentPacket = demuxer.getNextChunk();
			}
			
			// Now empty any final chunks that are less than the block size.
			for(int i = 0; i < demuxer.getStreamCount(); i++)
			{
				chunkBuffers.setMaxEndMarker(i); // This allows us to drain everything, and ignores the split point constraint.
				Chunk chunk = chunkBuffers.drainChunk(i);
				if(chunk != null)
				{
					chunkQ.put(chunk); // This will block until the queue has space.
				}
			}
		
			chunkQ.put(new Chunk(null, null));
			
			//Printer.println("Buffers:\n" + chunkBuffers.toString());
			
		} catch (InterruptedException e) {
			if(chunkBuffers != null)
				chunkBuffers.deallocBuffers();
			System.err.println("Thread was interupped while waiting:");
			e.printStackTrace();
		}
		finally {
			if(demuxer != null)
				demuxer.close();
			Printer.println("Demuxing complete. Thread ending.");
		}
		
	}
	
}
