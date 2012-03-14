package com.tstordyallison.ffmpegmr;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.BlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;

import com.tstordyallison.ffmpegmr.util.Printer;

public class WriterThread extends Thread{

	public static boolean FILE_PER_CHUNK = false;
	public static int BLOCK_SIZE = 16777216;
	public static boolean PRINT_WRITE = false;
	
	private BlockingQueue<Chunk> chunkQ;	
	private String outputUri = "";
	private Configuration conf;
	private FileSystem fs;
	private Path path;
	private SequenceFile.Writer writer = null; 
	private int blockSize = BLOCK_SIZE;
	
	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri) {
		super();
		this.chunkQ = chunkQ;
		this.outputUri = outputUri;
		initFileSystem(chunkQ,outputUri);
	}

	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri, String name) {
		super(name);
		this.chunkQ = chunkQ;
		this.outputUri = outputUri;
		initFileSystem(chunkQ,outputUri);
	}

	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri, ThreadGroup group, String name) {
		super(group, name);
		this.chunkQ = chunkQ;
		this.outputUri = outputUri;
		initFileSystem(chunkQ,outputUri);
	}
	
	public void initFileSystem(BlockingQueue<Chunk> chunkQ, String outputUri)
	{	
		// Connect to the ouptut filesystem.
		try {
			conf = new Configuration();
			conf.setInt("dfs.block.size", blockSize);
			fs = FileSystem.get(URI.create(outputUri), conf);
			path = new Path(outputUri);
			writer = SequenceFile.createWriter(fs, conf, path, ChunkID.class, ChunkData.class, CompressionType.NONE);
		} catch (IOException e) {
			System.err.println("IO Error connecting to FS:");
			e.printStackTrace();
		} 
	}

	@Override
	public void run() {
		// Process items in the writing queue and output them to the fs.
		try{
			
			while(true){
				// Get a chunk and write it out!
				Chunk chunk = chunkQ.take();
				if(chunk.getChunkData() == null)
				{
					Printer.println("Writing of final chunk complete. Writer thread stopping.");
					break;
				}
				if(FILE_PER_CHUNK)
					initFileSystem(chunkQ, outputUri + "." + chunk.getChunkID().streamID + "." + chunk.getChunkID().chunkNumber);
				writer.append(chunk.getChunkID(), chunk.getChunkData());
				if(FILE_PER_CHUNK)
					writer.close();
				if(PRINT_WRITE)
					Printer.println("Written: " + chunk.toString());
			}
			
		} catch (InterruptedException e) {
		} catch (IOException e) {
			System.err.println("IO error:");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(writer != null)
				IOUtils.closeStream(writer);
		}
	}

	public int getBlockSize() {
		return blockSize;
	}
}
