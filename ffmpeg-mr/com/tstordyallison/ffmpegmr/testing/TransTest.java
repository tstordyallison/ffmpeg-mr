package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.apache.hadoop.mrunit.types.Pair;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeMapper;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class TransTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Printer.ENABLED = true;
		
		if(args.length == 2)
		{
			if(!args[0].startsWith("file://"))
				args[0] = "file://" + args[0];
			
			if(!args[1].startsWith("file://"))
				args[1] = "file://" + args[1];
			
			runMapper(args[0], args[1]);
		}
		else
		{
			//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.mapped.seq");
			//runMapper("file:///Users/tom/Code/fyp/example-videos/TestChunked.m4v.seq.0.1", "file:///Users/tom/Code/fyp/example-videos/Test.m4v.mapped.seq");
			//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.mapped.seq");
			//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.mapped.seq");
			runMapper("file:///Users/tom/Code/fyp/example-videos/Test.avi.seq", "file:///Users/tom/Code/fyp/example-videos/Test.avi.mapped.seq");
		}
	}
	
	public static void runMapper(File input, File output) throws InstantiationException, IllegalAccessException, IOException, InterruptedException
	{
		runMapper("file://" + input.getAbsolutePath(), "file://" + output.getAbsolutePath());
	}
	
	public static void runMapper(String inputUri, String outputUri) throws InstantiationException, IllegalAccessException, IOException, InterruptedException
	{
		
		Printer.println("Mapping " + inputUri + "...");
		
		Configuration config = new Configuration();
		Path path = new Path(inputUri);
		SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(config), path, config);
		
		TranscodeMapper mapper = new TranscodeMapper();
		MapDriver<ChunkID,ChunkData,LongWritable,Chunk> driver = new MapDriver<ChunkID,ChunkData,LongWritable,Chunk>(mapper);
		driver.setConfiguration(config);
		
		ChunkID key = (ChunkID)reader.getKeyClass().newInstance();
		ChunkData value = (ChunkData)reader.getValueClass().newInstance();
		
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(20);
		WriterThread writer = new WriterThread(chunkQ,  outputUri, "Hadoop FS Writer Thread");
		writer.start();
		 
		while (reader.next(key, value))
		{
			driver.setInput(key, value);
			
			List<Pair<LongWritable,Chunk>> outputs = driver.run();
			for(Pair<LongWritable,Chunk> chunk : outputs)
				chunkQ.put(chunk.getSecond());
		}
		
		chunkQ.put(new Chunk(null, null)); // End the chunker.
		reader.close();
		
		writer.join();
		
		Printer.println("Sucessfully mapped " + outputUri + ".");
		
	}

}
