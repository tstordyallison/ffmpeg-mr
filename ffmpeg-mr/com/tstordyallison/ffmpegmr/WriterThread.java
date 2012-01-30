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

import com.tstordyallison.ffmpegmr.Chunker.Chunk;
import com.tstordyallison.ffmpegmr.Chunker.ChunkData;
import com.tstordyallison.ffmpegmr.Chunker.ChunkID;
import com.tstordyallison.ffmpegmr.util.Printer;

public class WriterThread extends Thread {

	private BlockingQueue<Chunk> chunkQ;
	
	private String outputUri = "";
	private Configuration conf;
	private FileSystem fs;
	private Path path;
	private SequenceFile.Writer writer = null; 
	private long blockSize;
	
	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri) {
		super();
		initFileSystem(chunkQ,outputUri);
	}

	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri, String name) {
		super(name);
		initFileSystem(chunkQ,outputUri);
	}

	public WriterThread(BlockingQueue<Chunk> chunkQ, String outputUri, ThreadGroup group, String name) {
		super(group, name);
		initFileSystem(chunkQ,outputUri);
	}
	
	public void initFileSystem(BlockingQueue<Chunk> chunkQ, String outputUri)
	{
		this.chunkQ = chunkQ;
		this.outputUri = outputUri;
		
		// Connect to the ouptut filesystem.
		try {
			conf = new Configuration();
			conf.set("fs.default.name", "s3://01MDAYB509VJ53B2EK02:zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv@ffmpeg-mr/");
			fs = FileSystem.get(URI.create(this.outputUri), conf);
			path = new Path(this.outputUri);
			setBlockSize(fs.getDefaultBlockSize());
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
				writer.append(chunk.getChunkID(), chunk.getChunkData());
				Printer.println("Written: " + chunk.toString());
				chunk.dealloc();
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

	private void setBlockSize(long blockSize) {
		this.blockSize = blockSize;
	}

	public long getBlockSize() {
		return blockSize;
	}
}
