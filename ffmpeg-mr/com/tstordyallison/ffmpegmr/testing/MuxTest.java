package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
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
			runReducer("file:///Users/tom/Code/fyp/example-videos/Test.wmv.mapped.seq", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.output.seq");
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
	
		List<Chunk> chunks = new ArrayList<Chunk>();
		while (reader.next(key, value))
			chunks.add(new Chunk(key, value));
		
		driver.setInputKey(new LongWritable(key.startTS));
		driver.setInputValues(chunks);
		
		List<Pair<LongWritable, BytesWritable>> outputs = driver.run();
		for(Pair<LongWritable, BytesWritable> output : outputs)
			Printer.println("Reduce output: ts=" + output.getFirst().get() + ", size=" + FileUtils.humanReadableByteCount(output.getSecond().getLength(), false));
		reader.close();
	
		
		Printer.println("Sucessfully ran reduce test for " + outputUri + ".");
		
	}

}
