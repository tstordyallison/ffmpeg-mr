package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.Stopwatch;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class ChunkTest {

	public static void main(String[] args) throws IOException, InterruptedException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		
		Printer.ENABLED = true;
		WriterThread.FILE_PER_CHUNK = false;
		WriterThread.PRINT_WRITE = true;
		WriterThread.BLOCK_SIZE *= 1; // 32Mb.
		
		if(args.length == 2)
		{
			if(!args[1].startsWith("file://")){
				args[1] = "file://" + new File(args[1]).getAbsolutePath();
			}
			
			chunkWithTimer(args[0], args[1]);
		}
		else
		{
			//chunkWithTimer("/Users/tom/Code/fyp/example-videos/TestLarge.m4v", "file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v.seq");
			//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mp4", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq");
			//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.m4v", "file:///Users/tom/Code/fyp/example-videos/Test.m4v.seq");
			//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
			//chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.wmv", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq");
			chunkWithTimer("/Users/tom/Code/fyp/example-videos/Test.avi", "file:///Users/tom/Code/fyp/example-videos/Test.avi.seq");
		}
	}
	
	public static long chunkWithTimer(File path, File localpath) throws IOException, InterruptedException
	{
		 return chunkWithTimer(path, "file://" + localpath.getAbsolutePath());
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
}
