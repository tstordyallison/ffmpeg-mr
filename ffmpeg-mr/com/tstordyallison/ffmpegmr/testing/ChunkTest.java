package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.Stopwatch;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class ChunkTest {

	public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		
		WriterThread.FILE_PER_CHUNK = false;
		WriterThread.BLOCK_SIZE *= 1; // 32Mb.
		
		if(args.length == 2)
		{
			if(!args[1].startsWith("file://")){
				args[1] = "file://" + new File(args[1]).getAbsolutePath();
			}
			
			chunkWithTimer(TranscodeJob.getConfig(), args[0], args[1]);
		}
		else
		{
			//processFolder(new File("/Users/tom/Code/fyp/example-videos/TestCandidates/"));
			//processFolder(new File("/Users/tom/Code/fyp/example-videos/TestCandidates/"));
	
			//chunkWithTimer(TranscodeJob.getConfig(), "file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v", "file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v", "file:///Users/tom/Code/fyp/example-videos/TestLarge.m4v.seq");
			chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/TestCandidates/Test4.avi", "file:///Users/tom/Code/fyp/example-videos/TestCandidates/Test4.avi.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.m4v", "file:///Users/tom/Code/fyp/example-videos/Test.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/TestMultiStream.m4v", "file:///Users/tom/Code/fyp/example-videos/TestMultiStream.m4v.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.mkv", "file:///Users/tom/Code/fyp/example-videos/Test.mkv.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test.wmv", "file:///Users/tom/Code/fyp/example-videos/Test.wmv.seq");
			//chunkWithTimer("file:///Users/tom/Code/fyp/example-videos/Test2.avi", "file:///Users/tom/Code/fyp/example-videos/Test2.avi.seq");
			//chunkWithTimer(TranscodeJob.getConfig(), "s3://ffmpeg-mr/movies/Stargate%20Atlantis%20Season%203/Stargate%20Atlantis%20S03E01%20-%20No%20Mans%20Land.avi", "file:///Users/tom/Code/fyp/example-videos/TestSG.avi.seq");
		}
	}

	public static void processFolder(File folder) throws IOException, InterruptedException, URISyntaxException
	{
		if(folder.isDirectory())
			for(File file: folder.listFiles())
			{
				if(file.isFile() & !file.getName().startsWith(".") && !file.getName().endsWith(".seq"))
						chunkWithTimer("file://" + file.getAbsolutePath(), "file://" + file.getAbsolutePath() + ".seq");
			}
	}
	
	public static long chunkWithTimer(String inputUri, String hadoopUri) throws IOException, InterruptedException, URISyntaxException
	{
		return chunkWithTimer(new Configuration(), inputUri, hadoopUri);
	}
	public static long chunkWithTimer(Configuration config, String inputUri, String hadoopUri) throws IOException, InterruptedException, URISyntaxException
	{
		long len = FileSystem.get(new URI(inputUri), config).getFileStatus(new Path(URLDecoder.decode(inputUri, "UTF-8"))).getLen();
		
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
			Chunker.chunkInputFile(config, inputUri, hadoopUri, (int)(WriterThread.BLOCK_SIZE)/2);
		stopwatch.stop();
		
		System.out.print("Time taken for " + FileUtils.humanReadableByteCount(len, false) + ": " + stopwatch.getElapsedTime() + " ms. ");
		
		Double time = stopwatch.getElapsedTime()/1000d;
		System.out.print("\tThroughput avg: " + FileUtils.humanReadableByteCount((long)(len/time), false) + " /s.");
		
		long size = getFileSize(hadoopUri);
		System.out.printf("\tOutput size: " + FileUtils.humanReadableByteCount(size, false) + " (%.2f%% overhead).\n", (((double)size/len)*100)-100);
		
		return (long)(len/time);
	}
	public static void listFiles(Configuration config, String hadoopUri) throws IOException
	{
		FileSystem fs = FileSystem.get(URI.create(hadoopUri), config);
		FileStatus[] files = fs.listStatus(new Path(hadoopUri));
		
		if(files != null)
			for(int i=0; i<files.length; i++)
			{
				System.out.println(fileStatusToString(files[i]));
			}
		
		fs.close();
	}
	public static long getFileSize(String hadoopUri) throws IOException
	{
		Configuration conf = new Configuration();
		conf.set("fs.default.name", "s3://AKIAI45LMHSV622K6EAA:X3lTKnuXTy0PpSTGXI3WiDB/Q5oI7lzdfPG8DifN@ffmpeg-mr/");
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
