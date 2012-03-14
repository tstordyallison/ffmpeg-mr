package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class FullTest {

	public static void main(String[] args)  throws IOException, InterruptedException, InstantiationException, IllegalAccessException{
		
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Printer.ENABLED = true;
		Chunker.CHUNK_SIZE_FACTOR = 0.5;
		
		if(args.length == 3)
		{
			ChunkTest.chunkWithTimer(new File(args[0]), new File(args[1]));
			TransTest.runMapper(new File(args[1]), new File(args[2]));
		}
		else
		{
		
			if(new File("examples/Test.wmv").exists())
			{
				ChunkTest.chunkWithTimer(new File("examples/Test.wmv"), new File("examples/Test.wmv.seq"));
				TransTest.runMapper(new File("examples/Test.wmv.seq"), new File("examples/Test.wmv.mapped.seq"));
			}
			
	//		ChunkTest.chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mp4", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq");
	//		TransTest.runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.mapped.seq");
	//		
	//		
	//		ChunkTest.chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
	//		TransTest.runMapper("file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.mapped.seq");
	//		
	//		
	//		ChunkTest.chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.wmv", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq");
	//		TransTest.runMapper("file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.mapped.seq");
			
			//ChunkTest.chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.avi", "file:///Users/tom/Code/fyp/example-videos/Test.avi.seq");
			//TransTest.runMapper("file:///Users/tom/Code/fyp/example-videos/Test.avi.seq", "file:///Users/tom/Code/fyp/example-videos/Test.avi.mapped.seq");
		}
	}
}
