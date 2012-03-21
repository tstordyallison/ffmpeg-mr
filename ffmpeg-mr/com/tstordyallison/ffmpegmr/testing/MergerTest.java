package com.tstordyallison.ffmpegmr.testing;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import com.tstordyallison.ffmpegmr.Merger;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class MergerTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		Printer.ENABLED = true;
		
		if(args.length == 2)
		{
			if(!args[0].contains("://"))
				args[0] = "file://" + new File(args[0]).getAbsolutePath();
			
			if(!args[1].contains("://"))
				args[1] = "file://" + new File(args[1]).getAbsolutePath();
			
			Merger.merge(args[0], args[1]);
		}
		else
		{
			Merger.merge(new Configuration(), "/Users/tom/Code/fyp/downloaded-output/Test.avi.mkv/", 
					new Path("file:///Users/tom/Code/fyp/example-videos/Test.avi.mkv"));
			
			//Merger.merge("file:///Users/tom/Code/fyp/example-videos/Test.mv.mkv.output0f8fda26-7160-41be-86b5-b09d03a5453c",
					//"file:///Users/tom/Code/fyp/example-videos/Output/Test.wmv.mkv");
			//Merger.merge("file:///Users/tom/Code/fyp/example-videos/avi-ffmpeg-mr-5cbe9e22-997f-4bbe-89f2-7cdc7ed9113e-9f9028e5-58d3-4d6a-9221-f1aeb98fef47/", 
			             //"file:///Users/tom/Code/fyp/example-videos/Output/Test.avi.mkv");
			//Merger.merge("file:///Users/tom/Code/fyp/example-videos/mkv-ffmpeg-mr-5cbe9e22-997f-4bbe-89f2-7cdc7ed9113e-35e5aeda-3feb-46c7-ad94-a3f8b6c9f033/", 
                         //"file:///Users/tom/Code/fyp/example-videos/Output/Test.mkv.mkv");
			//Merger.merge("file:///Users/tom/Code/fyp/example-videos/mp4-ffmpeg-mr-5cbe9e22-997f-4bbe-89f2-7cdc7ed9113e-135da8fd-4958-4b5f-ab00-544328015ec9/", 
                         //"file:///Users/tom/Code/fyp/example-videos/Output/Test.mp4.mkv");
		}
	}

}
