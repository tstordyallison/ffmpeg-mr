package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.Demuxer;
import com.tstordyallison.ffmpegmr.Demuxer.DemuxPacket;
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
		// 		Time taken for 237292012 bytes: 6592 ms. Throughput avg: 35996967 bytes/s
		// 		Time taken for 237292012 bytes: 6614 ms. Throughput avg: 35877231 bytes/s
		// 		Time taken for 237292012 bytes: 6824 ms. Throughput avg: 34773155 bytes/s
		// So, max throughput is now around 270Mbps or 33MB/s.
		
		
		// Changed everything to direct memory buffers. Much less copying over the boundary now.
		// CPU is still a bottle neck though. Multi threading might be the only way to go.
		
		// New new results:
		//		Time taken for 237292012 bytes: 6935 ms. Throughput avg: 34216584 bytes/s
		//		Time taken for 237292012 bytes: 5034 ms. Throughput avg: 47137864 bytes/s
		//		Time taken for 237292012 bytes: 5146 ms. Throughput avg: 46111933 bytes/s
		//		Time taken for 237292012 bytes: 5422 ms. Throughput avg: 43764664 bytes/s
		//		Time taken for 237292012 bytes: 5548 ms. Throughput avg: 42770730 bytes/s
		//		Time taken for 237292012 bytes: 5245 ms. Throughput avg: 45241565 bytes/s
		//		Time taken for 237292012 bytes: 4837 ms. Throughput avg: 49057682 bytes/s
		//		Time taken for 237292012 bytes: 5101 ms. Throughput avg: 46518724 bytes/s
		//		Time taken for 237292012 bytes: 4818 ms. Throughput avg: 49251144 bytes/s
		//		Time taken for 237292012 bytes: 5176 ms. Throughput avg: 45844670 bytes/s
		//		Throughput avg overall: 44991556 bytes/s
		// So, max throughput is now around 343Mbps or 43MB/s.
		
		// (Another later test with 20 runs got around 450Mbps or 57MB/s.)
		
		// Still could be better, but hey ho.
		
		
		// Test code:
		long avg = 0;
		//for(int i = 0; i < 5000; i++)
		//{
			//avg += chunkWithTimer("/Users/tom/Documents/FYP/Test.mp4", "file:///Users/tom/Documents/FYP/Test.mp4.seq");
			avg += chunkWithTimer("/Volumes/FFmpegTest/Test.mp4", "file:///Volumes/FFmpegTest/Test.mp4.seq");
			//isolationTest("/Volumes/FFmpegTest/Test.mp4");
			//System.out.println("Test run "+ i);
		//}
		//System.out.printf("Throughput avg overall: " + (long)((double)avg/10) + " bytes/s\n");
		
		//chunkWithTimer("/Volumes/FFmpegTest/Test.mp4", "s3://01MDAYB509VJ53B2EK02:zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv@ffmpeg-mr/Test.mp4.seq");
		//chunkWithTimer("/Users/tom/Documents/FYP/Test.mp4", "file:///Users/tom/Documents/FYP/Test.mp4.seq");
		//chunkWithTimer("/Users/tom/Documents/FYP/Test.mkv", "file:///Users/tom/Documents/FYP/Test.mkv.seq");
		//chunkWithTimer("/Users/tom/Documents/FYP/Test.wmv", "file:///Users/tom/Documents/FYP/Test.wmv.seq");
		//chunkWithTimer("/Users/tom/Documents/FYP/Test.avi", "file:///Users/tom/Documents/FYP/Test.avi.seq");
		//chunkWithTimer("/Volumes/Movies and TV/Movies/Avatar (2009).m4v", "file:///Users/tom/Documents/FYP/Avatar.seq");
	}
	
	
	public static long chunkWithTimer(String path, String hadoopUri) throws IOException
	{
		 return chunkWithTimer(new File(path), hadoopUri);
	}
	public static long chunkWithTimer(File file, String hadoopUri) throws IOException
	{
		
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
			Chunker.chunkInputFile(file, hadoopUri);
		stopwatch.stop();
		
		System.out.printf("Time taken for " + file.length() + " bytes: " + stopwatch.getElapsedTime() + " ms. ");
		
		Double time = stopwatch.getElapsedTime()/1000d;
		System.out.printf("Throughput avg: " + (long)(file.length()/time) + " bytes/s\n");
		
		return (long)(file.length()/time);
	}

	public static void isolationTest(String filename)
	{
		Demuxer demuxer = new Demuxer(filename);
		DemuxPacket pkt;
		while((pkt = demuxer.getNextChunk()) != null)
			pkt.deallocData();
		demuxer.close();
	}
}
