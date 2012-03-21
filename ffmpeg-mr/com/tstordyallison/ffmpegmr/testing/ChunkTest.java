package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.ChunkerThread;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.Stopwatch;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class ChunkTest {

	public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		
		Printer.ENABLED = true;
		WriterThread.FILE_PER_CHUNK = false;
		WriterThread.PRINT_WRITE = true;
		WriterThread.BLOCK_SIZE *= 2; // 32Mb.
		Chunker.CHUNK_Q_LIMIT = 15;
		ChunkerThread.FORCE_STREAM = true;
		
		JobConf config = new JobConf();
		config.set("fs.s3.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		config.set("fs.s3n.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3n.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		
		if(args.length == 2)
		{
			if(!args[1].startsWith("file://")){
				args[1] = "file://" + new File(args[1]).getAbsolutePath();
			}
			
			chunkWithTimer(config, args[0], args[1]);
		}
		else
		{
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v", "file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.mp4", "file:///Users/tom/Code/fyp/example-videos/Test.mp4.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.m4v", "file:///Users/tom/Code/fyp/example-videos/Test.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/TestMultiStream.m4v", "file:///Users/tom/Code/fyp/example-videos/TestMultiStream.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.wmv", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq");
			chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test2.avi", "file:///Users/tom/Code/fyp/example-videos/Test2.avi.seq");
			//chunkWithTimer(config, "s3n://ffmpeg-mr/movies/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
		}
	}

	public static long chunkWithTimer(String inputUri, String hadoopUri) throws IOException, InterruptedException, URISyntaxException
	{
		return chunkWithTimer(new Configuration(), inputUri, hadoopUri);
	}
	
	public static long chunkWithTimer(Configuration config, String inputUri, String hadoopUri) throws IOException, InterruptedException, URISyntaxException
	{
		long len = FileSystem.get(new URI(inputUri), config).getFileStatus(new Path(inputUri)).getLen();
		
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
			Chunker.chunkInputFile(config, inputUri, hadoopUri);
		stopwatch.stop();
		
		System.out.print("Time taken for " + FileUtils.humanReadableByteCount(len, false) + ": " + stopwatch.getElapsedTime() + " ms. ");
		
		Double time = stopwatch.getElapsedTime()/1000d;
		System.out.print("\tThroughput avg: " + FileUtils.humanReadableByteCount((long)(len/time), false) + " /s.");
		
		long size = getFileSize(hadoopUri);
		System.out.printf("\tOutput size: " + FileUtils.humanReadableByteCount(size, false) + " (%.2f%% overhead).\n", (((double)size/len)*100)-100);
		
		return (long)(len/time);
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

	@SuppressWarnings("unused")
	private static void testStream(Configuration config, String inputUri) throws IOException, URISyntaxException
	{
		FileSystem fs = FileSystem.get(new URI(inputUri), config);
		long len = fs.getFileStatus(new Path(inputUri)).getLen();
		Printer.println("Len: "+ len);
		FSDataInputStream in = fs.open(new Path(inputUri)); 
		try{
			Printer.println("Pos: " + in.getPos());
			Printer.println("Avl: " + in.available());
			Printer.println("Seeking...");
			in.seek(len);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		Printer.println("Read: " + in.read());
		Printer.println("Pos: " + in.getPos());
		Printer.println("Avl: " + in.available());
		in.seek(0);
		Printer.println("Pos: " + in.getPos());
		Printer.println("Avl: " + in.available());
	}
}
