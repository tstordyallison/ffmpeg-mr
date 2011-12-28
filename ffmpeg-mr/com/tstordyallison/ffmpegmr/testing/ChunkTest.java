package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.util.Stopwatch;

public class ChunkTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// Inside of RAM disk:
		// 		Time taken for 237292012 bytes: 12311 ms. Throughput avg: 19774334 bytes/s 
		// 		Time taken for 237292012 bytes: 12427 ms. Throughput avg: 19774334 bytes/s
		// 		Time taken for 237292012 bytes: 12663 ms. Throughput avg: 19774334 bytes/s
		// So, max throughput is around 150Mbps, or 18.8MB/s. 
		// Not great. It will do for now.
		
		// Removed bug that was stat'ing the file in the main chunk loop! Lol.
		// Also switch to LinkedLists rather than arrays so the remove isnt as bad.
		
		// New results:
		// Inside of RAM disk:
		// 		Time taken for 237292012 bytes: 5524 ms. Throughput avg: 47458402 bytes/s 
		// 		Time taken for 237292012 bytes: 12427 ms. Throughput avg: 19774334 bytes/s
		// 		Time taken for 237292012 bytes: 12663 ms. Throughput avg: 19774334 bytes/s
		// So, max throughput is around 150Mbps, or 18.8MB/s. 
		
		
		chunkWithTimer("/Volumes/FFmpegTest/Test.mp4", "file:///Volumes/FFmpegTest/Test.mp4.seq");	
		//chunkWithTimer("/Users/tom/Documents/FYP/Test.mp4", "file:///Users/tom/Documents/FYP/Test.mp4.seq");
		//chunkWithTimer(new File("/Users/tom/Documents/FYP/Test.mkv"), "file:///Users/tom/Documents/FYP/Test.mkv.seq");
		//chunkWithTimer(new File("/Users/tom/Documents/FYP/Test.wmv"), "file:///Users/tom/Documents/FYP/Test.wmv.seq");
		//chunkWithTimer(new File("/Users/tom/Documents/FYP/Test.avi"), "file:///Users/tom/Documents/FYP/Test.avi.seq");
		
		//Chunker.chunkInputFile(new File("/Volumes/Movies and TV/Movies/Avatar (2009).m4v"), "file:///Users/tom/Documents/FYP/Avatar.seq");
		
	}
	
	
	public static void chunkWithTimer(String path, String hadoopUri) throws IOException
	{
		 chunkWithTimer(new File(path), hadoopUri);
	}
	public static void chunkWithTimer(File file, String hadoopUri) throws IOException
	{
		
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
			Chunker.chunkInputFile(file, hadoopUri);
		stopwatch.stop();
		
		System.out.printf("Time taken for " + file.length() + " bytes: " + stopwatch.getElapsedTime() + " ms. ");
		if(stopwatch.getElapsedTime() > 1000)
			System.out.printf("Throughput avg: " + (file.length()/(stopwatch.getElapsedTime()/1000)) + " bytes/s\n");
	}

}
