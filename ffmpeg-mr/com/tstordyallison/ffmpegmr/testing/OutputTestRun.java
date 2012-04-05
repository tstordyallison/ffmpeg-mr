package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;
import java.net.URISyntaxException;

import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDefList;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;

public class OutputTestRun {

	public static void main(String[] args) throws IOException, URISyntaxException {
		TranscodeJobDefList list = new TranscodeJobDefList();
		list.add(new TranscodeJobDef("s3n://ffmpeg-mr/testcandidates/Test1.wmv.seq", "s3n://ffmpeg-mr/output/Test1.wmv.mkv"));
		list.add(new TranscodeJobDef("s3n://ffmpeg-mr/testcandidates/Test2.avi.seq", "s3n://ffmpeg-mr/output/Test1.avi.mkv"));
		list.toJSON(TranscodeJob.getConfig(), "s3n://ffmpeg-mr/job-submissions/TestRun.json");
	}

}
