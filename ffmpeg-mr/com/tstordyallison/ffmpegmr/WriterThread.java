package com.tstordyallison.ffmpegmr;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.SequenceFile.Metadata;
import org.apache.hadoop.io.compress.DefaultCodec;

import com.tstordyallison.ffmpegmr.emr.Logger;

public class WriterThread extends Thread{

	public static int WRITE_BUFFER_SIZE = 4;
	public static boolean FILE_PER_CHUNK = false;
	public static int BLOCK_SIZE = 16777216;
	public static boolean PRINT_WRITE = true;
	
	private boolean draining = false;
	private BlockingQueue<Chunk> chunkQ;
	private Queue<Chunk> bufferQ = new LinkedList<Chunk>();
	
	private String outputUri = "";
	private Configuration conf;
	private FileSystem fs;
	private Path path;
	private SequenceFile.Writer writer = null; 
	private int blockSize = BLOCK_SIZE;
	private Logger logger;
	
	public WriterThread(Configuration conf, BlockingQueue<Chunk> chunkQ, String outputUri, String name, int blockSize) {
		super(name);
		this.conf = conf;
		this.chunkQ = chunkQ;
		this.outputUri = outputUri;
		this.blockSize = blockSize;
		logger = new Logger(conf);
		initFileSystem(conf, chunkQ,outputUri);
	}
	private void initFileSystem(Configuration conf, BlockingQueue<Chunk> chunkQ, String outputUri)
	{	
		// Connect to the ouptut filesystem.
		try {
			int writerBlockSize;
			if(blockSize == 0)
				writerBlockSize = BLOCK_SIZE/4; // 4Mb.
			else
				writerBlockSize = blockSize;
			
			fs = FileSystem.get(URI.create(outputUri), conf);
			path = new Path(outputUri);
			writer = SequenceFile.createWriter(fs, conf, path, ChunkID.class, ChunkData.class, fs.getConf().getInt("io.file.buffer.size", 4096),
		            fs.getDefaultReplication(), writerBlockSize,CompressionType.NONE, new DefaultCodec(), null, new Metadata());
		} catch (IOException e) {
			System.err.println("IO Error connecting to FS:");
			e.printStackTrace();
		} 
	}

	@Override
	public void run() {
		// Process items in the writing queue and output them to the fs.
		try{
			
			while(!draining){
				
				// Get a new chunk off the main Q, and add it to our buffer.
				Chunk newChunk = chunkQ.take();
				bufferQ.add(newChunk);
				
				// If this new chunk is null, then we are draining.
				if(newChunk.getChunkData() == null)
					draining = true;
				
				// If the buffer is big enough, or if we are draining, write out stuff to the FS.
				while(draining || bufferQ.size() > WRITE_BUFFER_SIZE){
					
					// Get our chunk.
					Chunk chunk = bufferQ.remove();
					
					// If this is the null marker, end.
					if(chunk.getChunkData() == null)
					{
						logger.println("Writing of final chunk complete. Writer thread stopping.");
						break;
					}
					
					if(FILE_PER_CHUNK)
						initFileSystem(conf, chunkQ, outputUri + "." + chunk.getChunkID().getStreamID() + "." + chunk.getChunkID().getChunkNumber());
					writer.append(chunk.getChunkID(), chunk.getChunkData());
					if(FILE_PER_CHUNK)
						writer.close();
					if(PRINT_WRITE)
						logger.println("Written: " + chunk.toString());
				}
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
