package com.tstordyallison.ffmpegmr.testing;

import java.io.IOException;
import com.amazonaws.services.ec2.model.InstanceType;
import com.tstordyallison.ffmpegmr.emr.JobController;
import com.tstordyallison.ffmpegmr.emr.TimeEntryCSVWriter;
import com.tstordyallison.ffmpegmr.emr.JobController.JobControllerSettings;

public class ThreadingTestRun {

	public static void main(String[] args) throws IOException, InterruptedException {
		
		int startInstanceCount = 2;
		int instanceIncrement = 2;
		int endInstanceCount = 8;
	
		String outputFilePath = "./TestThreading.csv";
		String jobSubmission1 = "s3n://ffmpeg-mr/job-submissions/TestThreading1.json";
		String jobSubmission4 = "s3n://ffmpeg-mr/job-submissions/TestThreading4.json";
		
		JobControllerSettings jcSettings = new JobControllerSettings();
		jcSettings.instanceCount = startInstanceCount;
		jcSettings.instanceType = InstanceType.C1Xlarge.toString();
		jcSettings.bidPrice = "0.40";
		jcSettings.masterInstanceType = InstanceType.M1Large.toString();
		jcSettings.masterBidPrice = "0.30";
		jcSettings.numberOfMapTasksPerMachine = 4;
		jcSettings.flowName = "ffmpeg-mr: Threading Tests - 4 map tasks.";
		jcSettings.keepClusterAlive = false;
		jcSettings.performNativeBuild = false;
		jcSettings.uploadJar = true;
		jcSettings.reuseJVMTaskCount = 1;
		
		JobControllerSettings jcSettingsSingle = new JobControllerSettings();
		jcSettingsSingle.instanceCount = startInstanceCount;
		jcSettingsSingle.instanceType = InstanceType.C1Xlarge.toString();
		jcSettingsSingle.bidPrice = "0.40";
		jcSettingsSingle.masterInstanceType = InstanceType.M1Large.toString();
		jcSettingsSingle.masterBidPrice = "0.30";
		jcSettingsSingle.numberOfMapTasksPerMachine = 8;
		jcSettingsSingle.flowName = "ffmpeg-mr: Threading Tests - 8 map tasks, single threaded.";
		jcSettingsSingle.keepClusterAlive = false;
		jcSettingsSingle.performNativeBuild = false;
		jcSettingsSingle.uploadJar = true;
		jcSettingsSingle.reuseJVMTaskCount = 20;
		
		// Setup multi threaded cluster.
		JobController jc = new JobController(jcSettings);
		jc.addJobSubmission(jobSubmission4);
		for(int i = jc.getInstanceCount()+instanceIncrement; i <= endInstanceCount; i+=instanceIncrement){
			jc.addResizeStep(i);
			jc.addJobSubmission(jobSubmission4);
		}
		
		// Setup single threaded cluster.
		JobController jcSingle = new JobController(jcSettingsSingle);
		jcSingle.addJobSubmission(jobSubmission1);
		for(int i = jcSingle.getInstanceCount()+instanceIncrement; i <= endInstanceCount; i+=instanceIncrement){
			jcSingle.addResizeStep(i);
			jcSingle.addJobSubmission(jobSubmission1);
		}
		
		// Follow both until completion.
		JobController.followJobFlow(jc);
		JobController.followJobFlow(jcSingle);
		
		// Write out all of the timing info.
		TimeEntryCSVWriter.outputTimings(outputFilePath, jc.getJobFlowID());
		TimeEntryCSVWriter.outputTimings(outputFilePath, jcSingle.getJobFlowID());
	}

}
