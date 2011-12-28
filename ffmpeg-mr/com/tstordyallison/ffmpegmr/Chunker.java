package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.Writable;

import com.tstordyallison.ffmpegmr.Demuxer.DemuxPacket;

public class Chunker {

	// This is the identifier for each input chunk in the system.
	public static class ChunkID implements Writable {
		// TODO: implement a compareTo for the partitioner sorting job.
		
		public int streamID;    // Input file stream ID. 
		public long chunkNumber; // The first dts value in this chunk.
		public long chunkNumberEnd; // The last dts value in this chunk.
		public long chunkCount;
		
		@Override
		public void write(DataOutput out) throws IOException {
			out.writeInt(streamID);
			out.writeLong(chunkNumber);
			out.writeLong(chunkNumberEnd);
		}
		@Override
		public void readFields(DataInput in) throws IOException {
			streamID = in.readInt();
			chunkNumber = in.readLong();
			chunkNumberEnd = in.readLong();
		}
		
	}
	
	// This is the raw chunk of data that will be processed by a mapper.
	public static class ChunkData implements Writable {
		public byte[] data;

		@Override
		public void write(DataOutput out) throws IOException {
			out.write(data);
		}

		@Override
		public void readFields(DataInput in) throws IOException {
			in.readFully(data);
		}
	}
	
	public static void chunkInputFile(File file, String hadoopUri) throws IOException{
		System.out.println("Processing " + file.getName() + "...");
		
		// Check file.
		if(!file.exists())
			throw new RuntimeException(file.toString() + " does not exist.");
		
		// Connect to the HDFS system.
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(URI.create(hadoopUri), conf); 
		Path path = new Path(hadoopUri); // TODO: think about the block size here.
		Long blockSize = fs.getFileStatus(path).getBlockSize();
		
		// Setup the FFmepg demuxer and open the input file (this will load the FFmpeg-mr library).
		Demuxer demuxer = new Demuxer(file.getAbsolutePath());
		
		// Init our data stores for each stream (TODO: make this a custome data structure)
		List<Integer> 				streamHeaderSizes 			= new LinkedList<Integer>();		
		List<List<DemuxPacket>> 	currentChunks				= new LinkedList<List<DemuxPacket>>();
		List<Integer> 				currentChunksSizes 			= new LinkedList<Integer>();				
		List<Integer> 				currentChunkIDCounters		= new LinkedList<Integer>();	
		List<DemuxPacket> 			endMarkers 					= new LinkedList<DemuxPacket>(); 
		
		for(int i = 0; i < demuxer.getStreamCount(); i++)
		{
			streamHeaderSizes.add(demuxer.getStreamData(i).length); // TODO: This could be made better.
			currentChunks.add(new LinkedList<DemuxPacket>());
			currentChunksSizes.add(0);
			currentChunkIDCounters.add(0);
			endMarkers.add(null);
		}
		
		// Main stream splitter, chunker and outputter to HDFS.
		SequenceFile.Writer writer = null; 
		try {
			// Setup the output writer.
			writer = SequenceFile.createWriter(fs, conf, path, ChunkID.class, ChunkData.class, CompressionType.NONE);
			
			// Use the demuxer to get a new data chunk.
			// When we are happy, pass this chunk onto HDFS.
			{
				DemuxPacket currentPacket = demuxer.getNextChunk2();
				while(currentPacket != null)
				{
					// If we add this to the current chunk we are building, will we go over the chunk size?
					if(streamHeaderSizes.get(currentPacket.streamID) + 
					   currentChunksSizes.get(currentPacket.streamID) + 
					   currentPacket.data.length 
							> (blockSize))
					{
						// This would be bad, so lets call it day here.
						ChunkID chunkID = new ChunkID();
						chunkID.streamID = currentPacket.streamID;
						
						int written = emptyChunkBuffer(chunkID, 
								 							demuxer.getStreamData(currentPacket.streamID),
															currentChunks.get(currentPacket.streamID), 
															endMarkers.get(currentPacket.streamID),
															writer);
						
						// Store the left over counter.
						currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) - written);
						
						// Increment the chunk counter.
						currentChunkIDCounters.set(currentPacket.streamID, currentChunkIDCounters.get(currentPacket.streamID) + 1);
						
					}
					// We're not over the limit, so buffer it until we are.
					else
					{
						currentChunks.get(currentPacket.streamID).add(currentPacket);
						currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) + currentPacket.data.length);
					}
					
					// Mark this if it is a split point.
					if(currentPacket.splitPoint)
						endMarkers.set(currentPacket.streamID, currentPacket);
					
					// Get the next packet.
					currentPacket = demuxer.getNextChunk2();
				}
			}
			
			// Empty the buffers as we are at the end.
			for(int i = 0; i < demuxer.getStreamCount(); i++)
			{
				ChunkID chunkID = new ChunkID();
				chunkID.streamID = i;
				
				if(demuxer.getStreamData(i).length > 0)
					emptyChunkBuffer(chunkID, 
									 demuxer.getStreamData(i),
									 currentChunks.get(i), 
									 null,
									 writer);

			}
			
			System.out.println("Sucessfully processed " + file.getName() + ".");
		} 
		finally {
			if(writer != null)
				IOUtils.closeStream(writer);
			if(demuxer != null)
				demuxer.close();
		}
	}
	
	/*
	 * Empty the current buffer until the end marker (not including), and flush out the data to the writer.
	 * 
	 * Returns the number of chunk bytes that were written. TODO: make this nicer API wise.
	 */
	private static int emptyChunkBuffer(ChunkID 			chunkID,
										byte[] 				header,
										List<DemuxPacket> 	chunk, 
										DemuxPacket 		endMarker, 
										SequenceFile.Writer writer) throws IOException
	{
		// Count up the exact size of our data buffer, add the chunks to actual chunk, and remove them from the old one.
		List<DemuxPacket> actualChunk = new LinkedList<Demuxer.DemuxPacket>();
		
		int actualChunkSize = 0;
		Iterator<DemuxPacket> it = chunk.iterator();
		DemuxPacket currentPacket = null;
		while(it.hasNext())
		{
			currentPacket = it.next();
			
			if(currentPacket == endMarker)	
				break;
			
			actualChunk.add(currentPacket);
			actualChunkSize += currentPacket.data.length;
			it.remove();
		}
		
		// Set the start and end values for the the chunk id.
		if(actualChunk.size() > 0){
			chunkID.chunkNumber = actualChunk.get(0).dts;
			chunkID.chunkNumberEnd = actualChunk.get(actualChunk.size()-1).dts;
		}
		
		// Allocate the byte array (TODO: find a less memory intensive way to this. This is nasty. The GC will hate me. C?)
		ChunkData chunkData = new ChunkData();
		chunkData.data = new byte[header.length + actualChunkSize];
		int outputOffset = 0;
		
		// Copy in the header
		System.arraycopy(header, 0, chunkData.data, outputOffset, header.length);
		outputOffset += header.length;
		
		// Copy in the data
		for(DemuxPacket packet : actualChunk)
		{
//			System.out.println("\tChunk data copy: packet size=" + packet.data.length + 
//										",\t range=" + outputOffset + " to " + (outputOffset+packet.data.length) + 
//										",\t remaining=" + (chunkData.data.length - (outputOffset+packet.data.length)));
			System.arraycopy(packet.data, 0, chunkData.data, outputOffset, packet.data.length);
			outputOffset += packet.data.length;
		}
		
		// Write this!
		writer.append(chunkID, chunkData);
		System.out.println("Chunk (" + chunkID.chunkNumber + "->" + chunkID.chunkNumberEnd + "), stream " + chunkID.streamID + " written to FS.");
		
		// Mark this as a sync point in the sequence file.
		writer.sync();
		
		return actualChunkSize;
	}
}
