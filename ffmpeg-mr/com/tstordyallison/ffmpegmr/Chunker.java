package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class Chunker {
	
	public static int 	 CHUNK_Q_LIMIT = 4;
	public static double CHUNK_SIZE_FACTOR = 1;
	
	public static class ChunkerReport {
		private long packetCount = 0;
		private long endTS = 0;
		private long timeBaseDen = 0;
		private long timeBaseNum = 0;
		
		public ChunkerReport(long packetCount, long endTS, long timeBaseDen,  long timeBaseNum) {
			this.packetCount = packetCount;
			this.endTS = endTS;
			this.timeBaseDen = timeBaseDen;
			this.timeBaseNum = timeBaseNum;
		}
		
		public long getPacketCount() {
			return packetCount;
		}
		public long getEndTS() {
			return endTS;
		}
		public long getTimeBaseDen() {
			return timeBaseDen;
		}
		public long getTimeBaseNum() {
			return timeBaseNum;
		}
	}
	
	public static ChunkerReport chunkInputFile(File file, String hadoopUri) throws IOException, InterruptedException{
		Printer.println("Demuxing " + file.getName() + "...");
		
		// Check file.
		if(!file.exists())
			throw new RuntimeException(file.toString() + " does not exist.");
		
		// Chunk queue for processing
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(CHUNK_Q_LIMIT);
		
		// Start the chunker 
		WriterThread writer = new WriterThread(chunkQ, hadoopUri, "Hadoop FS Writer Thread"); 
		writer.setUncaughtExceptionHandler(new ThreadCatcher());
		ChunkerThread chunker = new ChunkerThread(chunkQ, file, (int)(writer.getBlockSize()*CHUNK_SIZE_FACTOR), "FFmpeg JNI Demuxer");
		chunker.setUncaughtExceptionHandler(new ThreadCatcher());
		
		// Start and wait for completion.
		chunker.start(); writer.start();
		chunker.join(); writer.join();
		
		// Job done!
		Printer.println("Sucessfully Demuxed " + file.getName() + ".");
	
		ChunkerReport report = new ChunkerReport(chunker.getPacketCount(), chunker.getEndTS(), chunker.getEndTSDen(), chunker.getEndTSNum());
		return report;
	}
		
}
