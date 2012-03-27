package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;
import java.net.URISyntaxException;

import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJobDef;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJobDefList;

public class OutputTestRun {

	public static void main(String[] args) throws IOException, URISyntaxException {
		TranscodeJobDefList list = new TranscodeJobDefList();
		list.add(new TranscodeJobDef("s3n://ffmpeg-mr/testcandidates/Test1.wmv.seq", "s3n://ffmpeg-mr/output/Test1.wmv.mkv"));
		list.add(new TranscodeJobDef("s3n://ffmpeg-mr/testcandidates/Test2.avi.seq", "s3n://ffmpeg-mr/output/Test1.avi.mkv"));
		list.toJSON(TranscodeJob.getConfig(), "s3n://ffmpeg-mr/job-submissions/TestRun.json");
	}

}
