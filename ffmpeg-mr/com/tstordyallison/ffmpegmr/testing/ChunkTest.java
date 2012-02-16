package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.DemuxPacket;
import com.tstordyallison.ffmpegmr.Demuxer;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.Stopwatch;

public class ChunkTest {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		
		Chunker.CHUNK_Q_LIMIT = 10;
		Chunker.CHUNK_SIZE_FACTOR = 0.5; 
		Printer.ENABLED = true;
		
		// Test code:
		//long avg = 0;
		//int loopcount = 5;
		//for(int i = 1; i <= loopcount; i++)
		//{
			//System.out.println("\n-------------\nTest run " + i + ":\n-------------");
			//avg += chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
			//avg += chunkWithTimer("/Volumes/FFmpegTest/Test.mp4", "file:///Volumes/FFmpegTest/Test.mp4.seq");
			//avg += chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.avi", "file:///Users/tom/Code/fyp/example-videos/Test.avi.seq");
		//}
		//System.out.printf("\n-----\nThroughput avg overall for " + FileUtils.humanReadableByteCount((long)((double)avg/(loopcount)), false) + "/s\n-----\n\n");
		
		// -----
		// Local conversion tests.
		// -----
		//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mp4", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq");
		//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
		//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.wmv", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq");
		chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.avi", "file:///Users/tom/Code/fyp/example-videos/Test.avi.seq");
		//System.gc();
		
		// -----
		// S3 Upload tests.
		// -----
		//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mp4", "Test.mp4.seq");
		//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.avi", "Test.avi.seq");
		//chunkWithTimer("/Volumes/Movies and TV/TV Shows/Stargate SG1/Season 1/Stargate.SG1-s01e13.The.Nox.m4v", "Stargate.SG1-s01e13.The.Nox.m4v.seq");
		//listFiles("/");
	}
	
	public static long chunkWithTimer(String path, String hadoopUri) throws IOException, InterruptedException
	{
		 return chunkWithTimer(new File(path), hadoopUri);
	}
	public static long chunkWithTimer(File file, String hadoopUri) throws IOException, InterruptedException
	{
		
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
			Chunker.chunkInputFile(file, hadoopUri);
		stopwatch.stop();
		
		System.out.print("Time taken for " + FileUtils.humanReadableByteCount(file.length(), false) + ": " + stopwatch.getElapsedTime() + " ms. ");
		
		Double time = stopwatch.getElapsedTime()/1000d;
		System.out.print("\tThroughput avg: " + FileUtils.humanReadableByteCount((long)(file.length()/time), false) + " /s.");
		
		long size = getFileSize(hadoopUri);
		System.out.printf("\tOutput size: " + FileUtils.humanReadableByteCount(size, false) + " (%.2f%% overhead).\n", (((double)size/file.length())*100)-100);
		
		return (long)(file.length()/time);
	}
	public static void listFiles(String hadoopUri) throws IOException
	{
		Configuration conf = new Configuration();
		conf.set("fs.default.name", "s3://01MDAYB509VJ53B2EK02:zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv@ffmpeg-mr/");
		FileSystem fs = FileSystem.get(URI.create(hadoopUri), conf);
		Path path = new Path(hadoopUri);
		FileStatus[] files = fs.listStatus(path);
		
		for(int i=0; i<files.length; i++)
		{
			System.out.println(fileStatusToString(files[i]));
		}
		
		fs.close();
	}
	public static long getFileSize(String hadoopUri) throws IOException
	{
		Configuration conf = new Configuration();
		conf.set("fs.default.name", "s3://01MDAYB509VJ53B2EK02:zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv@ffmpeg-mr/");
		FileSystem fs = FileSystem.get(URI.create(hadoopUri), conf);
		Path path = new Path(hadoopUri);
		long size = fs.getFileStatus(path).getLen();
		fs.close();
		return size;
	}
	public static String fileStatusToString(FileStatus fileStatus)
	{
		return fileStatus.getPath() + ", " + fileStatus.getLen();
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
