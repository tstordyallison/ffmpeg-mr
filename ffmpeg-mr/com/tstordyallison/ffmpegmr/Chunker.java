package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;

import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class Chunker {
	
	public static int CHUNK_Q_LIMIT = 4;
	
	public static class ChunkerReport {
		private long[] packetCounts;
		private long endTS = 0;
		
		public ChunkerReport(long[] packetCounts, long endTS) {
			this.packetCounts = packetCounts;
			this.endTS = endTS;
		}
		
		public long getPacketCount() {
			long total = 0;
			for(long count : getPacketCounts())
				total += count;
			return total;
		}
		public long[] getPacketCounts() {
			return packetCounts;
		}
		public long getEndTS() {
			return endTS;
		}
	}
	
	public static ChunkerReport chunkInputFile(Configuration config, File file, String hadoopUri, int blockSize) throws IOException, InterruptedException, URISyntaxException{
		// Check file.
		if(!file.exists())
			throw new RuntimeException(file + " does not exist.");
		
		return chunkInputFile(config, "file://" + file.getAbsolutePath(), hadoopUri, blockSize);
	}
	
	public static ChunkerReport chunkInputFile(Configuration config, String inputUri, String hadoopUri, int blockSize) throws IOException, InterruptedException, URISyntaxException{
		Logger logger = new Logger(config);
		logger.println("Demuxing " + inputUri + "...");
		
		// Chunk queue for processing
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(CHUNK_Q_LIMIT);
		
		// Start the chunker 
		WriterThread writer = new WriterThread(config, chunkQ, hadoopUri, "Hadoop FS Writer Thread", blockSize); 
		writer.setUncaughtExceptionHandler(new ThreadCatcher());
		ChunkerThread chunker = new ChunkerThread(config, chunkQ, inputUri, blockSize, "FFmpeg JNI Demuxer");
		chunker.setUncaughtExceptionHandler(new ThreadCatcher());
		
		// Start and wait for completion.
		chunker.start(); writer.start();
		chunker.join(); writer.join();
		
		// Job done!
		logger.println("Sucessfully Demuxed " + inputUri + ".");
		logger.flush();
		
		return new ChunkerReport(chunker.getPacketCounts(), chunker.getEndTS());
	}
		
}
