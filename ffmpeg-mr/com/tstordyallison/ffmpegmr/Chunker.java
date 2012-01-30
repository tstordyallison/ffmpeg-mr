package com.tstordyallison.ffmpegmr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hadoop.io.Writable;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.tstordyallison.ffmpegmr.Demuxer.DemuxPacket;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;

public class Chunker {
	
	public static int 	 CHUNK_Q_LIMIT = 20;
	public static double CHUNK_SIZE_FACTOR = 1;
	
	// This is just a class for storing both of the ID and data together in the processing q.
	public static class Chunk {
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
	
	// This is the identifier for each input chunk in the system.
	protected static class ChunkID implements Writable, Comparable<ChunkID> {
		// TODO: Sort this mess out. This started off as a struct, but is now an object.  
		
		public int streamID;    	// Input file stream ID. 
		public long startTS; 		// The first ts value in this chunk.
		public long endTS; 			// The last ts value in this chunk + duration of the last pkt.
		public long chunkNumber; 	// Numerical counter for debugging.
		public List<Long> outputChunkPoints = new ArrayList<Long>(); // Stores the extra points at which this chunk will split on encode.
		
		@Override
		public void write(DataOutput out) throws IOException {
			out.writeInt(streamID);
			out.writeLong(startTS);
			out.writeLong(endTS);
			out.writeLong(chunkNumber);
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
			List<String> chunkPoints = Arrays.asList(in.readUTF().split(","));
			for(String point : chunkPoints)
				outputChunkPoints.add(Long.parseLong(point));
		}
		
		@Override
		public String toString() {
			return "ChunkID [\n\t\tstreamID=\t" + streamID + ", " 
					+ "\n\t\tstartTS=\t" + startTS + " (" + PeriodFormat.getDefault().print(new Period(startTS/1000)) + "), "
					+ "\n\t\tendTS=\t\t" + endTS + " (" + PeriodFormat.getDefault().print(new Period(endTS/1000)) + "), "
					+ "\n\t\tduration=\t" + (endTS-startTS) + " (" + PeriodFormat.getDefault().print(new Period((endTS-startTS)/1000)) + "), "
					+ "\n\t\tchunkPoints=\t" + toString(outputChunkPoints, Integer.MAX_VALUE)
					+ "\n\t\tchunkNumber=\t" + chunkNumber + "\n]";
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

	}
	
	// This is the raw chunk of data that will be processed by a mapper.
	public static class ChunkData implements Writable {

		// List of ChunkIDs that this piece of data is used by.
		private Set<ChunkID> chunkIDs = Collections.synchronizedSet(new HashSet<ChunkID>());
		private AtomicBoolean dying = new AtomicBoolean(false);
		
		// Write mode.
		private byte[] header = null;
		private List<DemuxPacket> data = null;
		private long pktssize = -1;
		
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
		
		public long getSize()
		{
			if(pktssize ==  -1)
				return rawData.length;
			else
				return pktssize + header.length;
		}
		
		public void givePacketsSizeHint(long pktssize)
		{
			this.pktssize = pktssize;
		}
		
		@Override
		public void write(DataOutput out) throws IOException {
			out.write(header);
			if(data != null)
			{
				for(DemuxPacket pkt : data)
				{	
					if(pkt.data == null)
						System.err.println("Null data in DemuxPacket (" + pkt.toString() + ")");
					byte[] dst = new byte[pkt.data.limit()];
					pkt.data.get(dst); // This is the copy across to java.
					out.write(dst); // This is write to the FS.
				}
			}
		}

		@Override
		public void readFields(DataInput in) throws IOException {
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
	
	
	public static void chunkInputFile(File file, String hadoopUri) throws IOException, InterruptedException{
		Printer.println("Processing " + file.getName() + "...");
		
		// Check file.
		if(!file.exists())
			throw new RuntimeException(file.toString() + " does not exist.");
		
		// Chunk queue for processing
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(CHUNK_Q_LIMIT);
		
		// Start the chunker 
		WriterThread writer = new WriterThread(chunkQ, hadoopUri, "Hadoop FS Writer Thread");
		ChunkerThread chunker = new ChunkerThread(chunkQ, file, (int)(writer.getBlockSize()*CHUNK_SIZE_FACTOR), "FFmpeg JNI Demuxer");
		
		// Start and wait for completion.
		chunker.start(); writer.start();
		chunker.join(); writer.join();
		
		// Job done!
		Printer.println("Sucessfully processed " + file.getName() + ".");
	
	}
		
}
