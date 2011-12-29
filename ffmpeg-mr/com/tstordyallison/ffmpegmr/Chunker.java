package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
	
	// TODO: This sounds a bit crazy to even be writing but: Maybe this could be multi threaded?
	// The data could be pulled over from FFmpeg quicker, and then processed in a seperate thread.
	// Concurrency queues to manage everything.
	// Worth a go.
	
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
		// Write mode.
		private byte[] header = null;
		private List<DemuxPacket> data = null;
		
		// Read mode.
		private byte[] rawData = null;
		
		public ChunkData(byte[] header, List<DemuxPacket> packets)
		{
			this.header = header;
			this.data = packets;
		}
		
		public byte[] getData()
		{
			// TODO copy the data from the byte buffer to a new array for completeness.
			if(rawData == null)
				throw new RuntimeException("Data is not available - fix me!");
			
			return rawData;
		}
		
		@Override
		public void write(DataOutput out) throws IOException {
			out.write(header);
			for(DemuxPacket pkt : data)
			{	
				byte[] dst = new byte[pkt.data.limit()];
				pkt.data.get(dst); // This is the copy across to java.
				out.write(dst); // This is write to the FS.
			}
		}

		@Override
		public void readFields(DataInput in) throws IOException {
			in.readFully(rawData); // Internally this data is all delimited anyway.
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
		Long blockSize = fs.getDefaultBlockSize();
		
		// Setup the FFmepg demuxer and open the input file (this will load the FFmpeg-mr library).
		Demuxer demuxer = new Demuxer(file.getAbsolutePath());
		int streamCount = demuxer.getStreamCount();
		
		// Init our data stores for each stream (TODO: make this a custome data structure)
		List<Integer> 				streamHeaderSizes 			= new ArrayList<Integer>(streamCount); 		
		List<List<DemuxPacket>> 	currentChunks				= new ArrayList<List<DemuxPacket>>(streamCount); 
		List<Integer> 				currentChunksSizes 			= new ArrayList<Integer>(streamCount); 			
		List<Integer> 				currentChunkIDCounters		= new ArrayList<Integer>(streamCount); 
		List<Integer> 				endMarkers 					= new ArrayList<Integer>(streamCount); 
		
		for(int i = 0; i < streamCount; i++)
		{
			streamHeaderSizes.add(demuxer.getStreamData(i).length); // TODO: This could be made better.
			currentChunks.add(new LinkedList<DemuxPacket>()); // Linked list makes quite a difference.
			currentChunksSizes.add(0);
			currentChunkIDCounters.add(0);
			endMarkers.add(-1);
		}
		
		// Main stream splitter, chunker and outputter to HDFS.
		SequenceFile.Writer writer = null; 
		try {
			// Setup the output writer.
			writer = SequenceFile.createWriter(fs, conf, path, ChunkID.class, ChunkData.class, CompressionType.NONE);
			
			// Use the demuxer to get a new data chunk.
			// When we are happy, pass this chunk onto HDFS.
			{
				DemuxPacket currentPacket = demuxer.getNextChunk();
				while(currentPacket != null)
				{
					// If we add this to the current chunk we are building, will we go over the chunk size?
					if(streamHeaderSizes.get(currentPacket.streamID) + 
					   currentChunksSizes.get(currentPacket.streamID) + 
					   currentPacket.data.capacity()
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
						
						// Store the left over counter and increment the chunk counter.
						currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) - written);
						currentChunkIDCounters.set(currentPacket.streamID, currentChunkIDCounters.get(currentPacket.streamID) + 1);
						
					}
					// We're not over the limit, so buffer it until we are.
					else
					{
						currentChunks.get(currentPacket.streamID).add(currentPacket);
						currentChunksSizes.set(currentPacket.streamID, currentChunksSizes.get(currentPacket.streamID) + currentPacket.data.limit());
						
						// Mark this if it is a split point.
						if(currentPacket.splitPoint)
							endMarkers.set(currentPacket.streamID, currentChunks.get(currentPacket.streamID).size()-1);
					}
					
					// Get the next packet.
					currentPacket = demuxer.getNextChunk();
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
									 currentChunks.size()-1,
									 writer);

			}
			
			
			
			System.out.println("Sucessfully processed " + file.getName() + ".");
		} 
		catch(Exception e)
		{
			e.printStackTrace();
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
										List<DemuxPacket> 	chunkBuffer, 
										int			 		endMarker, 
										SequenceFile.Writer writer) throws IOException
	{
		// Build the data chunk for output.
		chunkID.chunkNumber = chunkBuffer.get(0).dts;
		chunkID.chunkNumberEnd = chunkBuffer.get(endMarker-1).dts;
		ChunkData chunkData = new ChunkData(header, new LinkedList<DemuxPacket>(chunkBuffer.subList(0, endMarker)));
		writer.append(chunkID, chunkData);
		System.out.println("Chunk (" + chunkID.chunkNumber + "->" + chunkID.chunkNumberEnd + "), stream " + chunkID.streamID + " written to FS.");
		
		// Mark this as a sync point in the sequence file.
		writer.sync();
		
		// Calculate what is left, dealloc the demux packets, and remove them from the buffer.
		int actualChunkSize = 0;
		Iterator<DemuxPacket> it = chunkBuffer.iterator();
		DemuxPacket currPacket = null;
		int currIdx = 0;
		while(it.hasNext())
		{
			if(currIdx != endMarker)
			{
				currPacket = it.next();
				actualChunkSize += currPacket.data.limit();
				currPacket.deallocData();
				it.remove();
				currIdx += 1;
			}
			else
			{
				break;
			}
			
		}
		
		return actualChunkSize;
	}
}
