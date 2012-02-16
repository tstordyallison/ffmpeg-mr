 package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.apache.hadoop.mrunit.types.Pair;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;
import com.tstordyallison.ffmpegmr.TranscodeMapper;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.Printer;

public class TransTest {

	/**
	 * @param args
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		
		Printer.ENABLED = true;
		
		//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq", "file:///Users/tom/Documents/FYP/Test.mp4.mapped.seq");
		//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq", "file:///Users/tom/Documents/FYP/Test.mkv.mapped.seq");
		//runMapper("file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq", "file:///Users/tom/Documents/FYP/Test.wmv.mapped.seq");
		runMapper("file:///Users/tom/Code/fyp/example-videos/Test.avi.seq", "file:///Users/tom/Documents/FYP/Test.avi.mapped.seq");
		
	}
	
	public static void runMapper(String inputUri, String outputUri) throws InstantiationException, IllegalAccessException, IOException, InterruptedException
	{
		
		Printer.println("Mapping " + inputUri + "...");
		
		Configuration config = new Configuration();
		Path path = new Path(inputUri);
		SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(config), path, config);
		
		TranscodeMapper mapper = new TranscodeMapper();
		MapDriver<ChunkID,ChunkData,ChunkID,ChunkData> driver = new MapDriver<ChunkID,ChunkData,ChunkID,ChunkData>(mapper);
		driver.setConfiguration(config);
		
		ChunkID key = (ChunkID)reader.getKeyClass().newInstance();
		ChunkData value = (ChunkData)reader.getValueClass().newInstance();
		
		BlockingQueue<Chunk> chunkQ = new LinkedBlockingQueue<Chunk>(20);
		WriterThread writer = new WriterThread(chunkQ,  outputUri, "Hadoop FS Writer Thread");
		writer.start();
		 
		while (reader.next(key, value))
		{
			driver.setInput(key, value);
			
			List<Pair<ChunkID,ChunkData>> outputs = driver.run();
			for(Pair<ChunkID, ChunkData> chunk : outputs)
				chunkQ.put(new Chunk(chunk.getFirst(), chunk.getSecond()));
		}
		
		chunkQ.put(new Chunk(null, null));
		reader.close();
		
		writer.join();
		
		Printer.println("Sucessfully mapped " + outputUri + ".");
		
	}

}
