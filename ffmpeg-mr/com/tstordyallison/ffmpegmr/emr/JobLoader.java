package com.tstordyallison.ffmpegmr.emr;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient;
import com.amazonaws.services.elasticmapreduce.model.ActionOnFailure;
import com.amazonaws.services.elasticmapreduce.model.AddJobFlowStepsRequest;
import com.amazonaws.services.elasticmapreduce.model.BootstrapActionConfig;
import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsResult;
import com.amazonaws.services.elasticmapreduce.model.HadoopJarStepConfig;
import com.amazonaws.services.elasticmapreduce.model.InstanceGroupConfig;
import com.amazonaws.services.elasticmapreduce.model.JobFlowDetail;
import com.amazonaws.services.elasticmapreduce.model.JobFlowExecutionState;
import com.amazonaws.services.elasticmapreduce.model.JobFlowInstancesConfig;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowRequest;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowResult;
import com.amazonaws.services.elasticmapreduce.model.ScriptBootstrapActionConfig;
import com.amazonaws.services.elasticmapreduce.model.StepConfig;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.ChunkerThread;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.Stopwatch;

public class JobLoader {
	private static final String AMI_VERSION = "2.0.4";
    private static final String HADOOP_VERSION = "0.20.205";
    
    // Normal core worker instances.
    private static final int    INSTANCE_COUNT = 0;
    private static final String INSTANCE_TYPE = InstanceType.C1Xlarge.toString();
    private static final String BID_PRICE = "0.40"; // Normally $0.744 per hour (+ $0.12 per hour for EMR)
    private static final int    NUMBER_OF_MAP_CORES = 8;
    private static final int    NUMBER_OF_REDUCE_PER_MACHINE = 8;
    
    // Extra helper instances - not part of HDFS, and can come and go as the prices fluctuate.
    private static final int    TASK_INSTANCE_COUNT = 0;
    private static final String TASK_BID_PRICE = "0.25"; // Normally $0.744 per hour (+ $0.12 per hour for EMR)
    
    private static final UUID   RANDOM_UUID = UUID.randomUUID();
    private static final String FLOW_NAME = "ffmpeg-mr-" + RANDOM_UUID.toString();
    private static final String BUCKET_NAME = "ffmpeg-mr";
    private static final String S3N_HADOOP_JAR = "s3n://ffmpeg-mr/jar/ffmpegmr.jar";
    private static final String S3N_LOG_URI = "s3n://" + BUCKET_NAME + "/jobs";
    private static final String EC2_KEY_NAME = "tstordyallison";
    private static final String REGION_ID =  "eu-west-1";
    private static final String EMR_ENDPOINT = "https://elasticmapreduce." + REGION_ID + ".amazonaws.com";
    private static final String MASTER_INSTANCE_TYPE = InstanceType.C1Xlarge.toString();
    private static final String MASTER_BID_PRICE = "0.40";  // Normally $0.186 per hour (+ $0.03 per hour for EMR)
    
    private static final boolean USE_NEW_CLUSTER = false;
    private static final boolean DEBUGGING = false;
    private static final boolean STAY_ALIVE = true;
    private static final boolean MASTER_BUILD = false;

    private static final String[][] JOB_ARGS = 
    	{
    	 //new String[] { "s3://ffmpeg-mr/movies/Test.mkv", "s3://ffmpeg-mr/output/mkv-" + FLOW_NAME}, 
    	 new String[] { "s3://ffmpeg-mr/movies/Test.m4v.seq", "s3://ffmpeg-mr/output/Test.m4v-" + FLOW_NAME},
    	 //new String[] { "s3://ffmpeg-mr/movies/TestLarge.m4v.seq", "s3://ffmpeg-mr/output/TestLarge.m4v-" + FLOW_NAME},
    	 //new String[] { "s3://ffmpeg-mr/movies/Shutter.Island.2010.720p.BluRay.x264.DTS-WiKi.m4v", "s3://ffmpeg-mr/output/shutter-" + FLOW_NAME}
    	 };
    private static final List<JobFlowExecutionState> DONE_STATES = Arrays
        .asList(new JobFlowExecutionState[] { JobFlowExecutionState.COMPLETED,
                                              JobFlowExecutionState.FAILED,
                                              JobFlowExecutionState.TERMINATED, 
                                              JobFlowExecutionState.WAITING});
    private static AmazonElasticMapReduce emr;

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {

		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
		
        System.out.println("===========================================");
        System.out.println("Welcome to FFmpeg-MR!");
        System.out.println("===========================================");

        AWSCredentials credentials = new PropertiesCredentials(JobLoader.class
                .getResourceAsStream("/com/tstordyallison/ffmpegmr/AwsCredentials.properties"));

        emr = new AmazonElasticMapReduceClient(credentials);
        emr.setEndpoint(EMR_ENDPOINT);

        try {
        	// Copy the jar up to s3.
        	TransferManager tm = new TransferManager(credentials);
        	Upload upload = tm.upload("ffmpeg-mr", "jar/ffmpegmr.jar", new File("ffmpegmr.jar")); 
        	upload.waitForCompletion();
        	tm.shutdownNow();
        	System.out.println("Upload of jar file complete.");
        	
            // Configure the Hadoop jar to use
        	List<StepConfig> steps = new ArrayList<StepConfig>();
        	for(String[] jobArgs : JOB_ARGS){
        		HadoopJarStepConfig jarConfig = new HadoopJarStepConfig(S3N_HADOOP_JAR);
                jarConfig.setArgs(Arrays.asList(jobArgs));           
                StepConfig stepConfig = new StepConfig("Transcode Job", jarConfig);
                if(STAY_ALIVE)
                	stepConfig.setActionOnFailure(ActionOnFailure.CANCEL_AND_WAIT);
                steps.add(stepConfig);
        	}
            
        	// Check to see if we have a spare cluster (this works on the assumption that I'm the only one using this!)
            if(!USE_NEW_CLUSTER)
            		System.out.println("Checking for spare cluster...");
            String jobFlowID = "";
        	DescribeJobFlowsResult checker = emr.describeJobFlows((new DescribeJobFlowsRequest()).withJobFlowStates(JobFlowExecutionState.WAITING.toString()));
            List<JobFlowDetail> flows = checker.getJobFlows();
            
            if(flows.size() > 0 && !USE_NEW_CLUSTER && flows.get(0).getName().startsWith("ffmpeg-mr-"))
            {
            	System.out.println("Using existing cluster.");
            	jobFlowID = flows.get(0).getJobFlowId();
            	AddJobFlowStepsRequest request = new AddJobFlowStepsRequest(jobFlowID, steps );
            	emr.addJobFlowSteps(request);
            	Thread.sleep(5000);
            }
            else
            {
            	System.out.println("Configuring new cluster.");
            	System.out.println("Using instance count: " + INSTANCE_COUNT + " (+ 1 master)");
            	System.out.println("Using instance type: " + INSTANCE_TYPE);
            	
            	List<InstanceGroupConfig> instanceGroups = new ArrayList<InstanceGroupConfig>();
            	
            	instanceGroups.add(new InstanceGroupConfig()
	            	.withInstanceCount(1)
	            	.withInstanceRole("MASTER")
	            	.withInstanceType(MASTER_INSTANCE_TYPE)
	            	.withMarket("SPOT")
	            	.withBidPrice(MASTER_BID_PRICE));
            	
                 
                if(INSTANCE_COUNT > 0) // We also turn on the task group here too, as we must be doing something decent.
                {
                	instanceGroups.add(new InstanceGroupConfig()
	                	.withInstanceCount(INSTANCE_COUNT)
	                	.withInstanceRole("CORE")
	                	.withInstanceType(INSTANCE_TYPE)
	                	.withMarket("SPOT")
	                	.withBidPrice(BID_PRICE));
                	
                	instanceGroups.add(new InstanceGroupConfig()
	                	.withInstanceCount(TASK_INSTANCE_COUNT)
	                	.withInstanceRole("TASK")
	                	.withInstanceType(INSTANCE_TYPE)
	                	.withMarket("SPOT")
	                	.withBidPrice(TASK_BID_PRICE));
                }
               
                if(DEBUGGING)	
                {
                   HadoopJarStepConfig debugJarConfig = new HadoopJarStepConfig("s3://"+ REGION_ID + ".elasticmapreduce/libs/script-runner/script-runner.jar");
                   debugJarConfig.setArgs(Arrays.asList(new String[]{"s3://"+ REGION_ID + ".elasticmapreduce/libs/state-pusher/0.1/fetch"}));           
                   StepConfig debugInitConfig = new StepConfig("Initialse debugger...", debugJarConfig );
                   debugInitConfig.setActionOnFailure(ActionOnFailure.CANCEL_AND_WAIT);
                   
                   steps.add(0, debugInitConfig);
                }
                
                JobFlowInstancesConfig instances = new JobFlowInstancesConfig();
                instances.setHadoopVersion(HADOOP_VERSION);
                instances.setInstanceGroups(instanceGroups);
                instances.setEc2KeyName(EC2_KEY_NAME);
                if(STAY_ALIVE)
                	instances.setKeepJobFlowAliveWhenNoSteps(true);

                List<BootstrapActionConfig> bootstraps = new ArrayList<BootstrapActionConfig>();
                
                BootstrapActionConfig bsMultiSupport = new BootstrapActionConfig();
                bsMultiSupport.setName("Configure multi-part/5TB support/block size/etc...");
                bsMultiSupport.setScriptBootstrapAction(
                		new ScriptBootstrapActionConfig("s3://elasticmapreduce/bootstrap-actions/configure-hadoop", 
                				Arrays.asList(new String[] {"-c", "fs.s3.multipart.uploads.enabled=true",
                											"-c", "fs.s3.multipart.uploads.split.size=524288000",
                											"-c", "fs.s3n.multipart.uploads.enabled=true",
                											"-c", "fs.s3n.multipart.uploads.split.size=524288000",
                											"-s", "fs.s3n.blockSize=" + WriterThread.BLOCK_SIZE, 
                											"-s", "fs.s3.blockSize=" + WriterThread.BLOCK_SIZE, 
                											"-s", "mapred.tasktracker.map.tasks.maximum=" + NUMBER_OF_MAP_CORES,
                											"-s", "mapred.tasktracker.reduce.tasks.maximum=" + NUMBER_OF_REDUCE_PER_MACHINE, 
                											"-m", "mapred.job.reuse.jvm.num.tasks=1"})));
                bootstraps.add(bsMultiSupport);
                
                BootstrapActionConfig bsSwap = new BootstrapActionConfig();
                bsSwap.setName("Configure 4GB swap space to alleviate memory pressure.");
                bsSwap.setScriptBootstrapAction(
                		new ScriptBootstrapActionConfig("s3://elasticmapreduce/bootstrap-actions/create-swap-file.rb", 
                							Arrays.asList(new String[] {"-s", "/mnt/swapfile", "4096"})));
                bootstraps.add(bsSwap);
                
                if(MASTER_BUILD)
                {
                	BootstrapActionConfig bs = new BootstrapActionConfig();
                	bs.setName("Build the FFmpeg-MR native libs on the master node.");
                	bs.setScriptBootstrapAction(
                    		new ScriptBootstrapActionConfig("s3://ffmpeg-mr/build/build.sh", Arrays.asList(new String[] {"master-only"})));
                    bootstraps.add(bs);
                }
                	
                RunJobFlowRequest request = new RunJobFlowRequest(FLOW_NAME, instances);
                request.setBootstrapActions(bootstraps);
                request.setAmiVersion(AMI_VERSION);
                request.setLogUri(S3N_LOG_URI);
            	request.setSteps(steps);
               
                RunJobFlowResult result = emr.runJobFlow(request);
                jobFlowID = result.getJobFlowId();
            }
            
            System.out.println("Request for job flow id: " + jobFlowID + " successful.");
            System.out.print("----------");
            
            String lastState = "";
            STATUS_LOOP: while (true)
            {
                DescribeJobFlowsRequest desc = new DescribeJobFlowsRequest(Arrays.asList(new String[] { jobFlowID }));
                DescribeJobFlowsResult descResult = emr.describeJobFlows(desc);
                for (JobFlowDetail detail : descResult.getJobFlows())
                {
                    String state = detail.getExecutionStatusDetail().getState();
                    if (isDone(state))
                    {
                        System.out.println("\nJob " + state + ": " + detail.toString().replace(", ", ",\n\t\t"));
                        break STATUS_LOOP;
                    }
                    else if (!lastState.equals(state))
                    {
                        lastState = state;
                        System.out.println("\nJob " + state + " at " + new Date().toString());
                    }
                    System.out.print(".");
                }
                Thread.sleep(10000);
            }
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
        
        stopwatch.stop();
        System.out.println("Approx time taken for transcode: " + PeriodFormat.getDefault().print(new Period(stopwatch.getElapsedTime())));
    }

    public static boolean isDone(String value)
    {
        JobFlowExecutionState state = JobFlowExecutionState.fromValue(value);
        return DONE_STATES.contains(state);
    }
}
