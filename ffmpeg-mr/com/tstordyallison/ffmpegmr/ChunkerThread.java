package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.util.FileUtils;

public class ChunkerThread extends Thread {
	
	public class ChunkBuffers {
		
		private ChunkID					endTSChunkID				= new ChunkID();
		private List<Long>				packetCount					= new ArrayList<Long>(demuxer.getStreamCount());
		private List<ChunkID>			chunkHistory				= new ArrayList<ChunkID>(100);
		private List<Boolean>           streamFirstChunk			= new ArrayList<Boolean>(demuxer.getStreamCount()); 
		private List<ByteBuffer> 		streamHeaders 				= new ArrayList<ByteBuffer>(demuxer.getStreamCount()); 		
		private List<List<DemuxPacket>> currentChunks				= new ArrayList<List<DemuxPacket>>(demuxer.getStreamCount()); 
		private List<Integer> 			currentChunksSizes 			= new ArrayList<Integer>(demuxer.getStreamCount()); 			
		private List<Integer> 			endMarkers 					= new ArrayList<Integer>(demuxer.getStreamCount()); 
		private Set<Long> 				chunkPoints 				= new HashSet<Long>();
		
		public ChunkBuffers()
		{
			for(int i = 0; i < demuxer.getStreamCount(); i++)
			{
				packetCount.add(0L);
				streamFirstChunk.add(true);
				streamHeaders.add(ByteBuffer.wrap(demuxer.getStreamData(i)));
				currentChunks.add(new LinkedList<DemuxPacket>()); // Linked list makes quite a difference.
				currentChunksSizes.add(0);
				endMarkers.add(-1);
			}
		}

		public void add(DemuxPacket currentPacket) {
			packetCount.set(currentPacket.streamID, packetCount.get(currentPacket.streamID) + 1);
			currentChunks.get(currentPacket.streamID).add(currentPacket);
			currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) + currentPacket.data.length);
			
			// Mark this if it is a split point.
			if(currentPacket.splitPoint)
				endMarkers.set(currentPacket.streamID, currentChunks.get(currentPacket.streamID).size()-1);
	
		}

		public long getBufferSize(int streamID) {
			return streamHeaders.get(streamID).limit() + currentChunksSizes.get(streamID);
		}

		public Chunk drainChunk(int streamID)
		{
			return drainChunk(streamID, true);
		}
		
		public Chunk drainChunk(int streamID, boolean monotonicityCheck) {
			
			// Get stream state.
			List<DemuxPacket> chunkBuffer = currentChunks.get(streamID);
			if(chunkBuffer.size() <= 0)
				return null;
			
			// Check the end markers.
			int endMarker = endMarkers.get(streamID); // This is exclusive of the end marker, e.g. the end marker is the start of the next chunk.
			
			if(endMarker == -1) // No where to split yet!
				return null;
			else if(endMarker == 0) // Only one frame!
				return null;
			else if(endMarker > chunkBuffer.size()-1) // Too far, we'll just take them all.
				endMarker = chunkBuffer.size(); 
			
			// Build a new chunk ID.
			ChunkID chunkID = new ChunkID();
			chunkID.setStreamID(streamID);
			chunkID.setStreamDuration(streamDuration);
			chunkID.setStartTS(chunkBuffer.get(0).ts);
			chunkID.setTbDen(chunkBuffer.get(0).tb_den);
			chunkID.setTbNum(chunkBuffer.get(0).tb_num);
			chunkID.setStreamType(demuxer.getStreamMediaType(streamID));
			if(endMarker == chunkBuffer.size()){
				// This is just an estimate. The last packet in the GOP will probably not be the last PTS.
				DemuxPacket lastPacket = chunkBuffer.get(endMarker-1);
				chunkID.setEndTS(lastPacket.ts + lastPacket.duration); 
			}
			else
				chunkID.setEndTS(chunkBuffer.get(endMarker).ts);
			
			// Figure out the chunkNumber (this is used for tracking the chunks around the MR).
			if(streamFirstChunk.get(streamID)){
				chunkID.setChunkNumber(0); // This fixes the corner case where the first startTS is not 0 (e.g audio post-roll).
				streamFirstChunk.set(streamID, false);
			}
			else
				chunkID.setChunkNumber(chunkID.getMillisecondsStartTs()); // This should always be correct as it will be a key frame.
			
			// Find all of the output chunk points that apply to this chunk.
			for(Long point : chunkPoints)
			{
				if(chunkID.getStartTS() < point && point < chunkID.getEndTS())
					chunkID.getOutputChunkPoints().add(point);
			}
			
			// Add this to our chunk points going forward.
			if(endMarker != chunkBuffer.size())
				chunkPoints.add(chunkID.getEndTS());
			
			// Sort the chunk points.
			Collections.sort(chunkID.getOutputChunkPoints());
			
			// ------------------------------------- Monotonicity Fixes -----------------------------------------------
			
			// Go through all of the previously output chunks (we might be able to make this a tighter bound, but for now
			// we're being thorough), and see if our end ts lies in their output ts range (the case where our start lies 
			// in the range will have had this same code run when it was output where it's end = our start), and if it does
			// we attempt to modify its chunk point list before it is written to the disk. If it has already been written, 
			// we will throw and exception and fail, as not having this time stamp in place would break the remuxer.
			
			// TODO: try and make this a smaller search (it is very unlikely that chunks a long time ago will actually need checked). 
			if(monotonicityCheck)
				for(ChunkID testChunk : chunkHistory)
				{
					if(testChunk.getStartTS() < chunkID.getEndTS() && chunkID.getEndTS() < testChunk.getEndTS()){
						if(!testChunk.getOutputChunkPoints().contains(chunkID.getEndTS())){	
							if(testChunk.isModifiable()){
								// We need to add this chunk point!
								testChunk.getOutputChunkPoints().add(chunkID.getEndTS());
								Collections.sort(testChunk.getOutputChunkPoints());
							}
							else{
								logger.println("Failure chunk:" + testChunk.toString());
								logger.println("Current chunk:" + chunkID.toString());
								throw new RuntimeException("A previously allocated chunk could not be modified to correct its chunk point list.");
							}
						}
					}
				}
	
			// ------------------------------------- Monotonicity Checks ------------------------------------------------
			
			// Check the monotonicity on the timestamps to ensure we havent missed out any chunk points.
			if(		monotonicityCheck &&  
					endTSChunkID.getMillisecondsEndTs() > chunkID.getMillisecondsEndTs() && 
					!endTSChunkID.getOutputChunkPoints().contains(chunkID.getEndTS())){
				
				// This means that we have already sent a chunk off that has a greater TS that this one.
				// We are now in a state where we could fail to remux all of the streams properly in the 
				// reducer. 

				logger.println("Failure chunk:" + endTSChunkID.toString());
				logger.println("Current chunk:" + chunkID.toString());
				logger.println(endTSChunkID.getMillisecondsEndTs() + " > " + chunkID.getMillisecondsEndTs());
				throw new RuntimeException("This file has TS monoticity errors and cannot be chunked.");
			}
			
			// Store this as a valid end for now.
			if(endTSChunkID.getEndTS() < chunkID.getEndTS()){
				endTSChunkID = chunkID;
			}
				
			// ---------------------------------------------------------------------------------------------------------
			
			// Build the chunk data.
			ByteBuffer header = streamHeaders.get(streamID);
			ChunkData chunkData = new ChunkData(header.array(), new LinkedList<DemuxPacket>(chunkBuffer.subList(0, endMarker)));
			
			// Calculate what is left, dealloc the demux packets, and remove them from the buffer.
			int actualChunkSize = 0;
			ListIterator<DemuxPacket> it = chunkBuffer.subList(0, endMarker).listIterator();
			while(it.hasNext())
			{
				DemuxPacket currPacket = it.next();
				actualChunkSize += currPacket.data.length;
				it.remove();
			}
			
			// Store the left over counter and increment the chunk counter, invalidate the end marker.
			currentChunksSizes.set(streamID, currentChunksSizes.get(streamID) - actualChunkSize);
			endMarkers.set(streamID, -1);
			
			// Take history of this chunk having been drained.
			chunkHistory.add(chunkID);
			
			// Return the new chunk (this also calls retain on the data so we dealloc correctly).
			return new Chunk(chunkID, chunkData);
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
					+ (endMarkers != null ? "\n\t\tendMarkers="
							+ endMarkers.subList(0,
									Math.min(endMarkers.size(), maxLen)) : "")
					+ "\n]";
		}
		
	}

	public static double AUDIO_CHUNK_SIZE_FACTOR = 1;
	public static double VIDEO_CHUNK_SIZE_FACTOR = 1;
	public static boolean FORCE_STREAM = false;
	
	private BlockingQueue<Chunk> chunkQ;
	private FSDataInputStream in;
	private long[] blockSizes;
	
	private Demuxer demuxer;
	private ChunkBuffers chunkBuffers;
	
	private long streamDuration;

	private Logger logger;
	
	public ChunkerThread(Configuration config, BlockingQueue<Chunk> chunkQ, String inputUri, long blockSize, String name) throws IOException, URISyntaxException {
		super(name);
		logger = new Logger(config);
		initDemuxer(config, chunkQ, inputUri, blockSize);
	}
	
	public void initDemuxer(Configuration config, BlockingQueue<Chunk> chunkQ, String inputUri, long blockSize) throws IOException, URISyntaxException
	{	
		this.chunkQ = chunkQ;
		
		if(FORCE_STREAM || !inputUri.startsWith("file://")){
			logger.println("Reading using Hadoop FS.");
			
			// Open up the filesystem for reading.
			if(config == null)
				config = new Configuration();
			FileSystem fs = FileSystem.get(new URI(inputUri), config);
			Path file = new Path(inputUri);
			this.in = fs.open(file);
			
			// Open the demuxer.
			this.demuxer = new Demuxer(in, fs.getFileStatus(file).getLen());
		}
		else
		{
			logger.println("Reading using native file IO.");
			this.demuxer = new Demuxer(new File(inputUri.substring(7)));
		}
		
		this.chunkBuffers = new ChunkBuffers();
		this.streamDuration = this.demuxer.getDurationMs();
		
		if(this.streamDuration > 0)
			logger.println("File duration estimate: " + PeriodFormat.getDefault().print(new Period(this.streamDuration)));
		
		blockSizes = new long[this.demuxer.getStreamCount()];
		for(int i = 0; i < this.demuxer.getStreamCount(); i++)
		{
			switch (this.demuxer.getStreamMediaType(i)) {
			case AUDIO:
				if(blockSize > 0)
					blockSizes[i] = (long)(AUDIO_CHUNK_SIZE_FACTOR * blockSize);
				else
					blockSizes[i] = (long)(16777216);
				break;
			case VIDEO:
				blockSizes[i] = (long)(VIDEO_CHUNK_SIZE_FACTOR * blockSize);
				break;
			default:
				blockSizes[i] = Long.MAX_VALUE; // This is an invalid stream - we shouldnt get any packets for it.
				break;
			}
		}
	}

	@Override
	public void run() {
		// Get new chunks from FFmpeg.
		try{
			boolean inChunkTooSmallState = false; // Only get the warning once!
			DemuxPacket currentPacket = demuxer.getNextChunk();
			while(currentPacket != null)
			{
				// Add this packet to the ChunkBuffer.
				chunkBuffers.add(currentPacket);
				
				// Check to see if we are now over our limit.
				if(chunkBuffers.getBufferSize(currentPacket.streamID) > blockSizes[currentPacket.streamID])
				{
					Chunk chunk = chunkBuffers.drainChunk(currentPacket.streamID);
					
					// If this is null, we couldnt drain a valid chunk, so we have to carry on instead.
					if(chunk != null){
						chunkQ.put(chunk); // This will block until the queue has space.
						inChunkTooSmallState = false;
					}
					else
						if(blockSizes[currentPacket.streamID] > 0 && !inChunkTooSmallState){
							logger.println("WARNING: Demuxer unable to drain chunk smaller than "  + FileUtils.humanReadableByteCount(blockSizes[currentPacket.streamID], false) + ". Try a larger chunk size.");
							inChunkTooSmallState = true;
						}
				}
				
				// Get the next packet.
				currentPacket = demuxer.getNextChunk();
			}
			
			// Now empty any final chunks that are less than the block size.
			for(int i = 0; i < demuxer.getStreamCount(); i++)
			{
				chunkBuffers.setMaxEndMarker(i); // This allows us to drain everything, and ignores the split point constraint.
				Chunk chunk = chunkBuffers.drainChunk(i, false);
				if(chunk != null)
				{
					chunkQ.put(chunk); // This will block until the queue has space.
				}
			}
		
			chunkQ.put(new Chunk(null, null));
			
			//Printer.println("Buffers:\n" + chunkBuffers.toString());
			
		} catch (InterruptedException e) {
			System.err.println("Thread was interupped while waiting:");
			e.printStackTrace();
		}
		finally {
			if(demuxer != null)
				demuxer.close();
			try {
				if(in != null)
					in.close();
			} catch (IOException e) {
			}
		}
		logger.println("Demuxing complete. Thread ending.");
	}

	public long[] getPacketCounts() {
		long[] packetCount = new long[chunkBuffers.packetCount.size()];
		for(int i = 0; i < chunkBuffers.packetCount.size(); i++)
			packetCount[i] = chunkBuffers.packetCount.get(i);
		return packetCount;
	}
	
	public long getEndTS() {
		return chunkBuffers.endTSChunkID.getMillisecondsEndTs();
	}	
}
