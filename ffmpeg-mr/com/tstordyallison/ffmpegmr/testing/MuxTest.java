package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mrunit.mapreduce.ReduceDriver;
import org.apache.hadoop.mrunit.types.Pair;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;
import com.tstordyallison.ffmpegmr.hadoop.RemuxReducer;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

/**
 * WARNING: This isn't all that elegant and is for testing only.
 * 
 * All of the chunks will be read into memory, and then the reducer ran on each grouped set of values. 
 * 
 * Make sure the -Xmx is high! (e.g. bigger than the input file at least!).
 * 
 * @author tom
 *
 */

public class MuxTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Printer.ENABLED = true;
		
		if(args.length == 2)
		{
			if(!args[0].startsWith("file://"))
				args[0] = "file://" + args[0];
			
			if(!args[1].startsWith("file://"))
				args[1] = "file://" + args[1];
			
			runReducer(args[0], args[1]);
		}
		else
		{
			runReducer("file:///Users/tom/Code/fyp/example-videos/Test.mp4.mapped.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.output.seq");
			//runReducer("file:///Users/tom/Code/fyp/example-videos/Test.mkv.mapped.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.output.seq");
			//runReducer("file:///Users/tom/Code/fyp/example-videos/Test.avi.mapped.seq", "file:///Users/tom/Code/fyp/example-videos/Test.avi.output.seq");
		}
	}
	
	public static void runReducer(File input, File output) throws InstantiationException, IllegalAccessException, IOException, InterruptedException
	{
		runReducer("file://" + input.getAbsolutePath(), "file://" + output.getAbsolutePath());
	}
	
	public static void runReducer(String inputUri, String outputUri) throws InstantiationException, IllegalAccessException, IOException, InterruptedException
	{
		
		Printer.println("Reducing " + inputUri + "...");
		
		Configuration config = new Configuration();
		Path path = new Path(inputUri);
		SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(config), path, config);
		
		Reducer<LongWritable, Chunk, LongWritable, BytesWritable> reducer = new RemuxReducer();
		ReduceDriver<LongWritable, Chunk, LongWritable, BytesWritable> driver = new ReduceDriver<LongWritable, Chunk, LongWritable, BytesWritable>(reducer);
		driver.setConfiguration(config);
		
		ChunkID key = (ChunkID)reader.getKeyClass().newInstance(); 
		ChunkData value = (ChunkData)reader.getValueClass().newInstance();
	
		// Read in all the chunks and sort them in memory.
		HashMap<Long, List<Chunk>> groupedChunks = new HashMap<Long, List<Chunk>>();
		while (reader.next(key, value)){
			if(groupedChunks.get(key.chunkNumber) != null)
				groupedChunks.get(key.chunkNumber).add(new Chunk(WritableUtils.clone(key, config), WritableUtils.clone(value, config)));
			else
			{
				groupedChunks.put(key.chunkNumber, new ArrayList<Chunk>());
				groupedChunks.get(key.chunkNumber).add(new Chunk(WritableUtils.clone(key, config), WritableUtils.clone(value, config)));
			}
		}
		reader.close();
		
		// Run the reducers.
		List<Long> keys = new ArrayList<Long>(groupedChunks.keySet());
		Collections.sort(keys);
		for(Long inputKey : keys){
			Collections.sort(groupedChunks.get(inputKey));
			
			driver.setInputKey(new LongWritable(inputKey));
			driver.setInputValues(groupedChunks.get(inputKey));
			
			//Printer.println("Running reducer for: " + groupedChunks.get(inputKey).toString());
			
			List<Pair<LongWritable, BytesWritable>> outputs = driver.run();
			for(Pair<LongWritable, BytesWritable> output : outputs)
				Printer.println("Reduce output: ts=" + output.getFirst().get() + ", size=" + FileUtils.humanReadableByteCount(output.getSecond().getLength(), false));
		}
		
		Printer.println("Sucessfully ran reduce test for " + outputUri + ".");
		
	}

}
