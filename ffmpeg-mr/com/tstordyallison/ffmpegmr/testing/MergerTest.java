package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;

import com.tstordyallison.ffmpegmr.Merger;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class MergerTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
		
		//Merger.merge(TranscodeJob.getConfig(), new File("/Users/tom/Code/fyp/downloaded-output/Test.avi.mkv"), new File("/Users/tom/Movies/Test.avi"));
		
//		Merger.merge(TranscodeJob.getConfig(), "s3n://ffmpeg-mr/output/m4v-ffmpeg-mr-11822f58-0a6c-4e91-9367-ae24d37e1a8b/",
//				             "file:///Users/tom/Code/fyp/example-videos/TestOutput.m4v");
		
		Merger.merge(TranscodeJob.getConfig(), "s3n://ffmpeg-mr/output/Test4.avi.mkv",
	             			 "file:///Users/tom/Code/fyp/example-videos/TestOutput.mkv");
	}

}
