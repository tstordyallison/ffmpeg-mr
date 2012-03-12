package com.tstordyallison.ffmpegmr.emr;

import java.io.File;
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
import com.amazonaws.services.elasticmapreduce.model.JobFlowDetail;
import com.amazonaws.services.elasticmapreduce.model.JobFlowExecutionState;
import com.amazonaws.services.elasticmapreduce.model.JobFlowInstancesConfig;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowRequest;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowResult;
import com.amazonaws.services.elasticmapreduce.model.ScriptBootstrapActionConfig;
import com.amazonaws.services.elasticmapreduce.model.StepConfig;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.tstordyallison.ffmpegmr.util.Stopwatch;

public class JobLoader {
	private static final String AMI_VERSION = "2.0";
    private static final String HADOOP_VERSION = "0.20.205";
    
    private static final int    INSTANCE_COUNT = 3;
    private static final String INSTANCE_TYPE = InstanceType.M1Large.toString();
    private static final int    NUMBER_OF_CORES = 2;
    
    private static final UUID   RANDOM_UUID = UUID.randomUUID();
    private static final String FLOW_NAME = "ffmpeg-mr-" + RANDOM_UUID.toString();
    private static final String BUCKET_NAME = "ffmpeg-mr";
    private static final String S3N_HADOOP_JAR = "s3n://ffmpeg-mr/ffmpegmr.jar";
    private static final String S3N_LOG_URI = "s3n://" + BUCKET_NAME + "/jobs";
    private static final String EC2_KEY_NAME = "tstordyallison";
    private static final String REGION_ID =  "eu-west-1";
    private static final String EMR_ENDPOINT = "https://elasticmapreduce." + REGION_ID + ".amazonaws.com";
    
    private static final boolean USE_NEW_CLUSTER = true;
    private static final boolean DEBUGGING = true;
    private static final boolean STAY_ALIVE = false;

    private static final String[] JOB_ARGS = new String[] { "s3://ffmpeg-mr/Test.mp4", 
    														"s3://ffmpeg-mr/output/" + FLOW_NAME};
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
                .getResourceAsStream("/com/tstordyallison/ffmpegmr/emr/AwsCredentials.properties"));

        emr = new AmazonElasticMapReduceClient(credentials);
        emr.setEndpoint(EMR_ENDPOINT);

        try {
        	// Copy the jar up to s3.
        	TransferManager tm = new TransferManager(credentials);
        	Upload upload = tm.upload("ffmpeg-mr", "ffmpegmr.jar", new File("ffmpegmr.jar")); 
        	upload.waitForCompletion();
        	tm.shutdownNow();
        	System.out.println("Upload of jar file complete.");
        	
            // Configure the Hadoop jar to use
            HadoopJarStepConfig jarConfig = new HadoopJarStepConfig(S3N_HADOOP_JAR);
            jarConfig.setArgs(Arrays.asList(JOB_ARGS));           
            StepConfig stepConfig = new StepConfig("Transcode Job", jarConfig);
            if(STAY_ALIVE)
            	stepConfig.setActionOnFailure(ActionOnFailure.CANCEL_AND_WAIT);
 
        	// Check to see if we have a spare cluster (this works on the assumption that I'm the only one using this!)
            if(!USE_NEW_CLUSTER)
            		System.out.println("Checking for spare cluster...");
            String jobFlowID = "";
        	DescribeJobFlowsResult checker = emr.describeJobFlows((new DescribeJobFlowsRequest()).withJobFlowStates(JobFlowExecutionState.WAITING.toString()));
            List<JobFlowDetail> flows = checker.getJobFlows();
            
            if(flows.size() > 0 && !USE_NEW_CLUSTER)
            {
            	System.out.println("Using existing cluster.");
            	jobFlowID = flows.get(0).getJobFlowId();
            	AddJobFlowStepsRequest request = new AddJobFlowStepsRequest(jobFlowID, Arrays.asList(new StepConfig[] { stepConfig }));
            	emr.addJobFlowSteps(request);
            	Thread.sleep(5000);
            }
            else
            {
            	System.out.println("Configuring new cluster.");
            	System.out.println("Using instance count: " + INSTANCE_COUNT);
            	System.out.println("Using instance type: " + INSTANCE_TYPE);
            	
                JobFlowInstancesConfig instances = new JobFlowInstancesConfig();
                instances.setHadoopVersion(HADOOP_VERSION);
                instances.setInstanceCount(INSTANCE_COUNT);
                instances.setMasterInstanceType(INSTANCE_TYPE);
                instances.setSlaveInstanceType(INSTANCE_TYPE);
                instances.setEc2KeyName(EC2_KEY_NAME);
                if(STAY_ALIVE)
                	instances.setKeepJobFlowAliveWhenNoSteps(true);
                
                // For now this only allow for us to use identical machines.
                BootstrapActionConfig bootstrap = new BootstrapActionConfig();
                bootstrap.setName("Configure map task max...");
                bootstrap.setScriptBootstrapAction(
                		new ScriptBootstrapActionConfig("s3://elasticmapreduce/bootstrap-actions/configure-hadoop", 
                				Arrays.asList(new String[] {"-s", "mapred.tasktracker.map.tasks.maximum=" + NUMBER_OF_CORES })));
                
                RunJobFlowRequest request = new RunJobFlowRequest(FLOW_NAME, instances);
                request.setBootstrapActions(Arrays.asList(new BootstrapActionConfig[]{ bootstrap }));
                request.setAmiVersion(AMI_VERSION);
                request.setLogUri(S3N_LOG_URI);
                
                if(DEBUGGING)	
                {
                   HadoopJarStepConfig debugJarConfig = new HadoopJarStepConfig("s3://"+ REGION_ID + ".elasticmapreduce/libs/script-runner/script-runner.jar");
                   debugJarConfig.setArgs(Arrays.asList(new String[]{"s3://"+ REGION_ID + ".elasticmapreduce/libs/state-pusher/0.1/fetch"}));           
                   StepConfig debugInitConfig = new StepConfig("Initialse debugger...", debugJarConfig );
                   debugInitConfig.setActionOnFailure(ActionOnFailure.CANCEL_AND_WAIT);
                   request.setSteps(Arrays.asList(new StepConfig[] { debugInitConfig, stepConfig }));
                }
                else
                	request.setSteps(Arrays.asList(new StepConfig[] { stepConfig }));

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
        if(!STAY_ALIVE)
        	System.out.println("Approx time taken for transcode: " + PeriodFormat.getDefault().print(new Period(stopwatch.getElapsedTime())));
    }

    public static boolean isDone(String value)
    {
        JobFlowExecutionState state = JobFlowExecutionState.fromValue(value);
        return DONE_STATES.contains(state);
    }
}
