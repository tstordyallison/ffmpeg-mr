package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;

import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class ReduceConvert {

	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws URISyntaxException 
	 */
	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException, URISyntaxException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Logger.Printer.ENABLED = true;
		
		if(args.length == 2)
		{
			if(!args[0].startsWith("file://"))
				args[0] = "file://" + args[0];
			
			runConversion(args[0], args[1]);
		}
		else
		{
			runConversion("s3n://ffmpeg-mr/output/mkv-ffmpeg-mr-da2c17e7-fa9f-46ad-b84f-ed4fa8467819/", 
						         "/Users/tom/Code/fyp/example-videos/output-mkv/Test.mkv");
//			runConversion("file:///Users/tom/Code/fyp/downloaded-output/mkv-ffmpeg-mr-5cbe9e22-997f-4bbe-89f2-7cdc7ed9113e-35e5aeda-3feb-46c7-ad94-a3f8b6c9f033/", 
//			                     "/Users/tom/Code/fyp/downloaded-videos/Output/Test.mkv.mkv");
//			runConversion("file:///Users/tom/Code/fyp/downloaded-videos/mp4-ffmpeg-mr-5cbe9e22-997f-4bbe-89f2-7cdc7ed9113e-135da8fd-4958-4b5f-ab00-544328015ec9/", 
//			                     "/Users/tom/Code/fyp/downloaded-videos/Output/Test.mp4.mkv");
		}

	}
	
	public static void runConversion(String inputUri, String outputPrefix) throws InstantiationException, IllegalAccessException, IOException, InterruptedException, URISyntaxException
	{
		Configuration config = TranscodeJob.getConfig();
		
		Logger.println(config, "Converting " + inputUri + "...");
		
		FileSystem fs = FileSystem.get(new URI(inputUri), config);
		
		for(FileStatus item : fs.listStatus(new Path(inputUri)))
		{
			if(item.getPath().toUri().toString().contains("part-")){
				SequenceFile.Reader reader = new SequenceFile.Reader(fs, item.getPath(), config);

				LongWritable key = (LongWritable)reader.getKeyClass().newInstance(); 
				BytesWritable value = (BytesWritable)reader.getValueClass().newInstance();
			
				Logger.println(config, "Reduce file: " + item.getPath());
				
				// Read in all the chunks and sort them in memory.
				while (reader.next(key, value)){
					Logger.println(config, "Reduce output: ts=" + key.get() + ", size=" + FileUtils.humanReadableByteCount(value.getLength(), false) + "(" + value.getLength() + " bytes)");
					
					File outputFile = new File(outputPrefix + "."  + key.get());
					FileOutputStream outputStream = new FileOutputStream(outputFile);
					outputStream.write(value.getBytes());
					outputStream.close();
				}
				reader.close();
			}	
		}
		
		Logger.println(config, "Sucessfully ran reduce test for " + inputUri + ".");
	}


}
