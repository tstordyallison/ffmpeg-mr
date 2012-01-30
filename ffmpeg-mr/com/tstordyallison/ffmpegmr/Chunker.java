package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


import com.tstordyallison.ffmpegmr.util.Printer;

public class Chunker {
	
	public static int 	 CHUNK_Q_LIMIT = 20;
	public static double CHUNK_SIZE_FACTOR = 1;
	
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
