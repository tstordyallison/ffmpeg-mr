package com.tstordyallison.ffmpegmr.emr;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
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
import com.amazonaws.services.elasticmapreduce.model.InstanceGroupDetail;
import com.amazonaws.services.elasticmapreduce.model.JobFlowDetail;
import com.amazonaws.services.elasticmapreduce.model.JobFlowExecutionState;
import com.amazonaws.services.elasticmapreduce.model.JobFlowInstancesConfig;
import com.amazonaws.services.elasticmapreduce.model.JobFlowInstancesDetail;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowRequest;
import com.amazonaws.services.elasticmapreduce.model.RunJobFlowResult;
import com.amazonaws.services.elasticmapreduce.model.ScriptBootstrapActionConfig;
import com.amazonaws.services.elasticmapreduce.model.StepConfig;
import com.amazonaws.services.elasticmapreduce.util.ResizeJobFlowStep;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.tstordyallison.ffmpegmr.WriterThread;

public class JobController {
  
    // ---------------------------------------------------------------------------------------------------------------
	
    public static final String BUCKET_NAME = "ffmpeg-mr";
    public static final String S3N_HADOOP_JAR = "s3n://ffmpeg-mr/jar/ffmpegmr.jar";
    public static final String S3N_LOG_URI = "s3n://" + BUCKET_NAME + "/jobs";
    public static final String EC2_KEY_NAME = "tstordyallison";
    public static final String REGION_ID =  "eu-west-1";
    public static final String EMR_ENDPOINT = "https://elasticmapreduce." + REGION_ID + ".amazonaws.com";
    public static final String AMI_VERSION = "2.0.4";
    public static final String HADOOP_VERSION = "0.20.205";
    
    // ---------------------------------------------------------------------------------------------------------------
    private static final List<JobFlowExecutionState> DONE_STATES = Arrays
        .asList(new JobFlowExecutionState[] { JobFlowExecutionState.COMPLETED,
                                              JobFlowExecutionState.FAILED,
                                              JobFlowExecutionState.TERMINATED, 
                                              JobFlowExecutionState.WAITING,
                                              JobFlowExecutionState.SHUTTING_DOWN});
    private static AmazonElasticMapReduce emr;
    private static AWSCredentials credentials;
	
    static{
    	try{
    		credentials = new PropertiesCredentials(JobController.class
    				.getResourceAsStream("/com/tstordyallison/ffmpegmr/emr/AwsCredentials.properties"));
    	}
        catch (Exception e) {
			throw new RuntimeException("Error reading AWS crendtials from prop file.");
		}
        
        setEmr(new AmazonElasticMapReduceClient(credentials));
        getEmr().setEndpoint(EMR_ENDPOINT);
    }
    
	// ---------------------------------------------------------------------------------------------------------------
	
    @SuppressWarnings("serial")
	public static class JobControllerSettings implements Serializable {		
		public enum MarketType {ONDEMAND, SPOT};
    	
	    // Job params.
	    public String flowName = "ffmpeg-mr-" + UUID.randomUUID().toString();
	    public String  jobFlowID = null;
	    
	    // Normal core worker instances - required for the cluster to start.
	    public int    instanceCount = 1;
	    public String instanceType = InstanceType.C1Xlarge.toString();
	    public String bidPrice = "0.40"; // Normally $0.744 per hour (+ $0.12 per hour for EMR)
	    public int numberOfMapTasksPerMachine = 4;
	    public int numberOfReduceTasksPerMachine = 4;
	    public int numberOfVideoThreads = 3;
	    public MarketType instanceMarketType = MarketType.SPOT;
	    public int reuseJVMTaskCount = 20;
		public boolean speculativeExecution = false;
		
	    // Master instance settings. 
	    public String masterInstanceType = InstanceType.M1Large.toString(); // (C1Medium is 32bit)
	    public String masterBidPrice = "0.25";  // Normally $0.186 per hour (+ $0.03 per hour for EMR)
	    public MarketType masterMarketType = MarketType.SPOT;
	    
	    // Cluster launch settings.
	    public boolean createNewCluster = true;
	    public boolean debugging = false;
	    public boolean keepClusterAlive = false;
	    public boolean performNativeBuild = false;
	    public boolean uploadJar = false;
	    public boolean skipFailedJobs = true;
    }
    
    private JobControllerSettings settings;
    
    public JobController() {
		settings = new JobControllerSettings();
    	processClusterRequest();
	}
    
    public JobController(JobControllerSettings settings) {
		this.settings = settings; //TODO: make this a deep copy. 
    	processClusterRequest();
	}
    
	public JobController(int instanceCount) {
		this(instanceCount, InstanceType.C1Xlarge, "0.40");
	}
    
    public JobController(int instanceCount, InstanceType instanceType, String bidPrice) {
    	settings = new JobControllerSettings();
    	settings.instanceCount = instanceCount;
    	settings.instanceType = instanceType.toString();
    	settings.bidPrice = bidPrice;
    	processClusterRequest();
	}

    private void processClusterRequest() {
	
	        try {
	        	// Copy the jar up to s3.
	        	if(settings.uploadJar){
		        	TransferManager tm = new TransferManager(credentials);
		        	Upload upload = tm.upload("ffmpeg-mr", "jar/ffmpegmr.jar", new File("ffmpegmr.jar")); 
		        	upload.waitForCompletion();
		        	tm.shutdownNow();
		        	System.out.println("Upload of jar file complete.");
	        	}
	        	
	        	List<StepConfig> steps = new ArrayList<StepConfig>();
	        	
	        	if(!settings.createNewCluster) {
	        		List<JobFlowDetail> flows;
	        		if(settings.jobFlowID != null && !settings.jobFlowID.isEmpty()){
	        			DescribeJobFlowsResult checker = getEmr().describeJobFlows(new DescribeJobFlowsRequest()
	        					.withJobFlowIds(settings.jobFlowID)
	        					.withJobFlowStates(JobFlowExecutionState.WAITING.toString(), 
	        									   JobFlowExecutionState.RUNNING.toString(), 
	        									   JobFlowExecutionState.STARTING.toString(), 
	        									   JobFlowExecutionState.BOOTSTRAPPING.toString()));
			            flows = checker.getJobFlows();
	        			
	        		}
	        		else
	        		{
	            		DescribeJobFlowsResult checker = getEmr().describeJobFlows(new DescribeJobFlowsRequest()
	        					.withJobFlowStates(JobFlowExecutionState.WAITING.toString(), 
	        									   JobFlowExecutionState.RUNNING.toString(), 
	        									   JobFlowExecutionState.STARTING.toString(), 
	        									   JobFlowExecutionState.BOOTSTRAPPING.toString()));
			            flows = checker.getJobFlows();
	        		}
		            
		            FLOW_LOOP: for(JobFlowDetail flow : flows)
		            {
		            	if(flow.getName().startsWith("ffmpeg-mr")){
		            		JobFlowInstancesDetail detail = flow.getInstances();
		            		
		            		for(InstanceGroupDetail group : detail.getInstanceGroups()){
		            			if(group.getInstanceRole().equals("MASTER")){
		            				if(!group.getInstanceType().equals(settings.masterInstanceType))
		            					continue FLOW_LOOP;
		            			}
		            			if(settings.instanceCount > 0 && group.getInstanceRole().equals("CORE")){
			            				if(group.getInstanceRunningCount() != settings.instanceCount)
			            					continue FLOW_LOOP;
			            				if(!group.getInstanceType().equals(settings.instanceType))
			            					continue FLOW_LOOP;
		            			}
		            			if(settings.instanceCount == 0 && group.getInstanceRole().equals("CORE")){
		            				continue FLOW_LOOP;
		            			}
		            		}
		            	}
		            	
		            	// Woop - this is a good enough match for us. 
		            	settings.jobFlowID = flow.getJobFlowId();
		            }
	        	}

            	if(settings.jobFlowID != null && !settings.jobFlowID.isEmpty() && steps.size() > 0){
	            	getEmr().addJobFlowSteps(new AddJobFlowStepsRequest(settings.jobFlowID, steps));
	            	Thread.sleep(2500);
            	}
//            	else
//            		if(!settings.createNewCluster)
//            			throw new RuntimeException("Could not find a matching cluster in the WAITING/RUNNING/STARTING/BOOTSTRAPPING state.");
	            
	            if((settings.jobFlowID == null || settings.jobFlowID.isEmpty()) && settings.createNewCluster)
	            {
	            	System.out.println("Configuring new cluster.");
	            	System.out.println("Using instance count: " + settings.instanceCount + " (+ 1 master)");
	            	System.out.println("Using instance type: " + settings.instanceType);
	            	
	            	List<InstanceGroupConfig> instanceGroups = new ArrayList<InstanceGroupConfig>();
	            	
	            	instanceGroups.add(new InstanceGroupConfig()
		            	.withInstanceCount(1)
		            	.withInstanceRole("MASTER")
		            	.withInstanceType(settings.masterInstanceType)
		            	.withMarket(settings.masterMarketType.toString())
		            	.withBidPrice(settings.masterBidPrice));
	            	
	                 
	                if(settings.instanceCount > 0) // We also turn on the task group here too, as we must be doing something decent.
	                {
	                	instanceGroups.add(new InstanceGroupConfig()
		                	.withInstanceCount(settings.instanceCount)
		                	.withInstanceRole("CORE")
		                	.withInstanceType(settings.instanceType)
		                	.withMarket(settings.instanceMarketType.toString())
		                	.withBidPrice(settings.bidPrice));
	                }
	               
	                if(settings.debugging)	
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
	                if(settings.keepClusterAlive)
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
	                											"-s", "mapred.tasktracker.map.tasks.maximum=" + settings.numberOfMapTasksPerMachine,
	                											"-s", "mapred.tasktracker.reduce.tasks.maximum=" + settings.numberOfReduceTasksPerMachine, 
	                											"-m", "mapred.job.reuse.jvm.num.tasks=" + settings.reuseJVMTaskCount, 
	                											"-s", "mapred.reduce.tasks.speculative.execution=" + settings.speculativeExecution,
	                											"-s", "mapred.map.tasks.speculative.execution=" + settings.speculativeExecution })));
	                bootstraps.add(bsMultiSupport);
	                
	                BootstrapActionConfig bsSwap = new BootstrapActionConfig();
	                bsSwap.setName("Configure swap space to alleviate memory pressure.");
	                bsSwap.setScriptBootstrapAction(
	                		new ScriptBootstrapActionConfig("s3://elasticmapreduce/bootstrap-actions/create-swap-file.rb", 
	                							Arrays.asList(new String[] {"-E", "/mnt/swapfile", "4096"})));
	                bootstraps.add(bsSwap);
	                
	                BootstrapActionConfig bsMem = new BootstrapActionConfig();
	                bsMem.setName("Set everything in memory intensive mode for Chunking/Merging/Reducing large binary data.");
	                bsMem.setScriptBootstrapAction(
	                		new ScriptBootstrapActionConfig("s3://ffmpeg-mr/jar/memory-intensive", Arrays.asList(new String[] {})));
	                bootstraps.add(bsMem);
	                
	                if(settings.performNativeBuild)
	                {
	                	BootstrapActionConfig bs = new BootstrapActionConfig();
	                	bs.setName("Build the FFmpeg-MR native libs on the master node.");
	                	bs.setScriptBootstrapAction(new ScriptBootstrapActionConfig("s3://elasticmapreduce/bootstrap-actions/run-if", 
    									Arrays.asList(new String[] {"instance.isMaster=true", "s3://ffmpeg-mr/build/build.sh"})));
	                    bootstraps.add(bs);
	                }
	                	
	                RunJobFlowRequest request = new RunJobFlowRequest(settings.flowName, instances);
	                request.setBootstrapActions(bootstraps);
	                request.setAmiVersion(AMI_VERSION);
	                request.setLogUri(S3N_LOG_URI);
	            	request.setSteps(steps);
	               
	                RunJobFlowResult result = getEmr().runJobFlow(request);
	                settings.jobFlowID = result.getJobFlowId();
	            }
	            
	            System.out.println("Job flow id: " + settings.jobFlowID);
		}
		catch(Exception e){
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
    }
    
    private static boolean isDone(String value)
    {
        JobFlowExecutionState state = JobFlowExecutionState.fromValue(value);
        return DONE_STATES.contains(state);
    }
   
    public void addResizeStep(int targetCoreSize){
    	
    	// TODO: Check to see if this is greater than the current value - CORE groups can only go up. 
    	
		ResizeJobFlowStep resize = new ResizeJobFlowStep().
				withWait(true).
				withResizeAction(new ResizeJobFlowStep.ModifyInstanceGroup()
					.withInstanceCount(targetCoreSize)
					.withInstanceGroup("CORE"));
		
		HadoopJarStepConfig hadoop = resize.toHadoopJarStepConfig();
		hadoop.getArgs().add("--wait-for-change");
		hadoop.setJar("s3://ffmpeg-mr/jar/resize-job-flow.jar");

	    StepConfig resizeStep = new StepConfig()
	         .withName("Resize the cluster to " + targetCoreSize + " CORE nodes.")
	         .withHadoopJarStep(hadoop);
	    
        if(settings.keepClusterAlive)
        	resizeStep.setActionOnFailure(ActionOnFailure.CANCEL_AND_WAIT);
        
        //System.out.println("Adding resize step...");
		getEmr().addJobFlowSteps(new AddJobFlowStepsRequest(getJobFlowID(), Arrays.asList(new StepConfig[]{resizeStep})));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
    }
    
    public String addJobSubmission(String jobSubmissionUri)
    {
        UUID jobID = UUID.randomUUID();
        
		HadoopJarStepConfig jarConfig = new HadoopJarStepConfig(S3N_HADOOP_JAR);
        jarConfig.setArgs(Arrays.asList(new String[]{jobSubmissionUri, jobID.toString(), Integer.toString(settings.numberOfVideoThreads)}));
        jarConfig.setMainClass("com.tstordyallison.ffmpegmr.hadoop.TranscodeJob");
        StepConfig stepConfig = new StepConfig("Transcode Job: " + jobID.toString(), jarConfig);
        if(settings.keepClusterAlive || settings.skipFailedJobs)
        	stepConfig.setActionOnFailure(ActionOnFailure.CONTINUE);
        else
        	stepConfig.setActionOnFailure(ActionOnFailure.TERMINATE_JOB_FLOW);
		getEmr().addJobFlowSteps(new AddJobFlowStepsRequest(getJobFlowID(), Arrays.asList(new StepConfig[]{stepConfig})));
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		return jobID.toString();
    }
    
    public void addRecordTimingsStep(String uri){ 
    	HadoopJarStepConfig jarConfig = new HadoopJarStepConfig(S3N_HADOOP_JAR);
    	jarConfig.setMainClass("com.tstordyallison.ffmpegmr.emr.TimeEntryCSVWriter");
        jarConfig.setArgs(Arrays.asList(new String[]{uri, getJobFlowID()})); 
        
        StepConfig stepConfig = new StepConfig("Save timings: " + uri, jarConfig);
        if(settings.keepClusterAlive || settings.skipFailedJobs)
        	stepConfig.setActionOnFailure(ActionOnFailure.CONTINUE);
        else
        	stepConfig.setActionOnFailure(ActionOnFailure.TERMINATE_JOB_FLOW);
		getEmr().addJobFlowSteps(new AddJobFlowStepsRequest(getJobFlowID(), Arrays.asList(new StepConfig[]{stepConfig})));
    }
    
	public int getInstanceCount() {
		return settings.instanceCount;
	}

	public String getInstanceType() {
		return settings.instanceType;
	}

	public String getBidPrice() {
		return settings.bidPrice;
	}

	public int getNumberOfMapTasksPerMachine() {
		return settings.numberOfMapTasksPerMachine;
	}

	public int getNumberOfReduceTasksPerMachine() {
		return settings.numberOfReduceTasksPerMachine;
	}

	public String getMasterInstanceType() {
		return settings.masterInstanceType;
	}

	public String getMasterBidPrice() {
		return settings.masterBidPrice;
	}

	public String getJobFlowID() {
		return settings.jobFlowID;
	}

	public boolean isCreateNewCluster() {
		return settings.createNewCluster;
	}

	public boolean isDebugging() {
		return settings.debugging;
	}

	public boolean isKeepClusterAlive() {
		return settings.keepClusterAlive;
	}

	public boolean isPerformNativeBuild() {
		return settings.performNativeBuild;
	}
	
	public boolean isUploadJar() {
		return settings.uploadJar;
	}

	public static LogReader followJobRun(String jobRunID){
		LogReader logReader = new LogReader(jobRunID, new DateTime());
		Thread logReaderThread = new Thread(logReader, "Log Reader:" + jobRunID);
	    logReaderThread.start();
	    return logReader;
	}

	public static void followJobFlow(JobController jc){
	    try{	
	        String lastState = "";
	        STATUS_LOOP: while (true)
	        {
	            DescribeJobFlowsRequest desc = new DescribeJobFlowsRequest(Arrays.asList(new String[] { jc.getJobFlowID() }));
	            DescribeJobFlowsResult descResult = getEmr().describeJobFlows(desc);
	            for (JobFlowDetail detail : descResult.getJobFlows())
	            {
	                String state = detail.getExecutionStatusDetail().getState();
	                if (isDone(state))
	                {
	                	try {
							Thread.sleep(20000);
						} catch (InterruptedException e) {
							break;
						}
	                	if(isDone(state)){
		                    System.out.println("Job " + state + ": " + detail.toString().replace(", ", ",\n\t\t"));
		                    break STATUS_LOOP;
	                	}
	                }
	                else if (!lastState.equals(state))
	                {
	                    lastState = state;
	                    System.out.println("Job " + state + " at " + new Date().toString());
	                }
	            }
	            try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					break;
				}
	        }
	        
	    } catch (AmazonServiceException ase) {
	    	System.err.println("Caught Exception: " + ase.getMessage());
	    	System.err.println("Response Status Code: " + ase.getStatusCode());
	    	System.err.println("Error Code: " + ase.getErrorCode());
	    	System.err.println("Request ID: " + ase.getRequestId());
	    }
	        
	}

	
	public static AmazonElasticMapReduce getEmr() {
		return emr;
	}

	private static void setEmr(AmazonElasticMapReduce emr) {
		JobController.emr = emr;
	}

}
