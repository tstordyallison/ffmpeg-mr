package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class FullTest {

	public static void main(String[] args)  throws IOException, InterruptedException, InstantiationException, IllegalAccessException, URISyntaxException{
		
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Printer.ENABLED = true;
		
		if(args.length == 3)
		{
			ChunkTest.chunkWithTimer(args[0], args[1]);
			TransTest.runMapper(new File(args[1]), new File(args[2]));
		}
		else
		{
			
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
