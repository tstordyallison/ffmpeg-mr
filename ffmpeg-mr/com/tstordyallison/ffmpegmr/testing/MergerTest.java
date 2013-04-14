package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;

import com.tstordyallison.ffmpegmr.Merger;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;
import com.tstordyallison.ffmpegmr.util.ThreadCatcher;

public class MergerTest {

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {
		Thread.setDefaultUncaughtExceptionHandler(new ThreadCatcher()); 
	
		Merger.merge(TranscodeJob.getConfig(), "file:///Users/tom/Code/fyp/example-videos/TestCandidates/Test2.avi.segments/",
	             			 "file:///Users/tom/Code/fyp/example-videos/TestOutput.mkv");
	}

}
