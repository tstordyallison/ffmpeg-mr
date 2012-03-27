package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;

import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class Chunker {
	
	public static int 	 CHUNK_Q_LIMIT = 5;
	
	public static class ChunkerReport {
		private long packetCount = 0;
		private long endTS = 0;
		
		public ChunkerReport(long packetCount, long endTS) {
			this.packetCount = packetCount;
			this.endTS = endTS;
		}
		
		public long getPacketCount() {
			return packetCount;
		}
		public long getEndTS() {
			return endTS;
		}
	}
	
	public static ChunkerReport chunkInputFile(Configuration config, File file, String hadoopUri) throws IOException, InterruptedException, URISyntaxException{
		// Check file.
		if(!file.exists())
			throw new RuntimeException(file + " does not exist.");
		
		return chunkInputFile(config, "file://" + file.getAbsolutePath(), hadoopUri);
	}
	
	public static ChunkerReport chunkInputFile(Configuration config, String inputUri, String hadoopUri) throws IOException, InterruptedException, URISyntaxException{
		Printer.println("Demuxing " + inputUri + "...");
		
		// Chunk queue for processing
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(CHUNK_Q_LIMIT);
		
		// Start the chunker 
		WriterThread writer = new WriterThread(config, chunkQ, hadoopUri, "Hadoop FS Writer Thread"); 
		writer.setUncaughtExceptionHandler(new ThreadCatcher());
		ChunkerThread chunker = new ChunkerThread(config, chunkQ, inputUri, writer.getBlockSize(), "FFmpeg JNI Demuxer");
		chunker.setUncaughtExceptionHandler(new ThreadCatcher());
		
		// Start and wait for completion.
		chunker.start(); writer.start();
		chunker.join(); writer.join();
		
		// Job done!
		Printer.println("Sucessfully Demuxed " + inputUri + ".");
	
		ChunkerReport report = new ChunkerReport(chunker.getPacketCount(), chunker.getEndTS());
		return report;
	}
		
}
