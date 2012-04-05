package com.tstordyallison.ffmpegmr.emr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.joda.time.DateTime;

import com.amazonaws.services.ec2.model.InstanceType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.tstordyallison.ffmpegmr.emr.JobController.JobControllerSettings;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;

public class Benchmark {
	private static transient Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private transient String fileLocation = null;
	private transient Configuration conf = null;
	
	private String name = "<name>";
	private String description = "";
	
	private int instanceIncrement = 2;
	private int endInstanceCount = 8;
	
	private String jobSubmissionUri = null;
	private String resultsUri = null;
	private String resultsURL = null;
	
	private JobControllerSettings settings;
	
	private TranscodeJobDefList jobSubmission = null;
	
	@SuppressWarnings("unused")
	private Benchmark(){
		// for GSON.
	}
	
	public Benchmark(String name, String jobSubmissionUri, JobControllerSettings settings) {
		this.name = name;
		this.jobSubmissionUri = jobSubmissionUri;
		this.settings = settings;
	}
	
	public Benchmark(String name, TranscodeJobDefList jobSubmission, JobControllerSettings settings) {
		this.name = name;
		this.jobSubmission = jobSubmission;
		this.settings = settings;
	}

	public static Benchmark loadDefinition(Configuration conf, String uri) throws IOException, URISyntaxException{
		FileSystem fs = FileSystem.get(new URI(uri), conf);
		Path path = new Path(uri);
		FSDataInputStream fis = fs.open(path);
        BufferedReader input = new BufferedReader(new InputStreamReader(fis));
        Benchmark bench = gson.fromJson(input, Benchmark.class);
        bench.conf = conf;
        bench.fileLocation = uri;
        input.close();
        fis.close();
        fs.close();
        return bench;
	}
	
	/**
	 * Process the details of this Benchmark and send them to EMR/EC2.
	 * @return the JobContoller that was created/submitted to.
	 */
	public JobController processRequest() {
		JobController jc = new JobController(getSettings());
		
		if(jobSubmission != null){
			jobSubmissionUri = "s3n://" + JobController.BUCKET_NAME + "/job-submissions/Bench-" + name.replace(" ", "") + ".json";
			try {
				jobSubmission.toJSON(TranscodeJob.getConfig(), jobSubmissionUri);
			} catch (IOException e) {
			} catch (URISyntaxException e) {
			}
		}
		
		String jobSubmissionUri = this.jobSubmissionUri;
		if(!jobSubmissionUri.contains("://"))
			jobSubmissionUri = "s3n://" + JobController.BUCKET_NAME + "/job-submissions/" + jobSubmissionUri; 
			
		jc.addJobSubmission(jobSubmissionUri);
		for(int i = jc.getInstanceCount()+instanceIncrement; i <= endInstanceCount; i+=instanceIncrement){
			jc.addResizeStep(i);
			jc.addJobSubmission(jobSubmissionUri);
		}
	
		if(resultsUri == null){
			String date = new DateTime().toString("yyyyMMdd-HHmmss");
			resultsUri = "s3n://" + JobController.BUCKET_NAME + "/results/" + name.replace(" ", "") + "-" + date + ".csv";
			resultsURL = "http://" + JobController.BUCKET_NAME + ".s3.amazonaws.com/results/" + name.replace(" ", "") + "-" + date + ".csv";
		}
		
		jc.addRecordTimingsStep(resultsUri);
	
		return jc;
	}
	
	public void save() throws IOException, URISyntaxException{
		if(conf != null && fileLocation != null)
			save(conf, fileLocation);
	}
	
	public void save(Configuration conf, String uri) throws IOException, URISyntaxException{
		FileSystem fs;
		if(uri.startsWith("file://"))
			fs = new RawLocalFileSystem();
		else
			fs = FileSystem.get(new URI(uri), conf);
		Path path = new Path(uri);
		FSDataOutputStream fos = fs.create(path);
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(fos));
		JsonWriter writer = new JsonWriter(output);
		writer.setIndent("  ");
        gson.toJson(this, Benchmark.class, writer);
        output.close();
        fos.close();
        fs.close();
	}

	public String getFileLocation() {
		return fileLocation;
	}

	public void setFileLocation(String fileLocation) {
		this.fileLocation = fileLocation;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getInstanceIncrement() {
		return instanceIncrement;
	}

	public void setInstanceIncrement(int instanceIncrement) {
		this.instanceIncrement = instanceIncrement;
	}

	public int getEndInstanceCount() {
		return endInstanceCount;
	}

	public void setEndInstanceCount(int endInstanceCount) {
		this.endInstanceCount = endInstanceCount;
	}

	public JobControllerSettings getSettings() {
		return settings;
	}

	public void setSettings(JobControllerSettings settings) {
		this.settings = settings;
	}

	public String getResultsUri() {
		return resultsUri;
	}

	public void setResultsUri(String resultsUri) {
		this.resultsUri = resultsUri;
	}
	
	public String getJobSubmissionUri() {
		return jobSubmissionUri;
	}

	public void setJobSubmissionUri(String jobSubmissionUri) {
		this.jobSubmissionUri = jobSubmissionUri;
	}

	public TranscodeJobDefList getJobSubmission() {
		return jobSubmission;
	}

	public void setJobSubmission(TranscodeJobDefList jobSubmission) {
		this.jobSubmission = jobSubmission;
	}

	public String getResultsURL() {
		return resultsURL;
	}

	public void setResultsURL(String resultsURL) {
		this.resultsURL = resultsURL;
	}

	public static void saveExample() throws IOException, URISyntaxException{
		JobControllerSettings jcSettings = new JobControllerSettings();
		jcSettings.instanceCount = 2;
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
		
		Benchmark bench = new Benchmark("Multi Threaded Bench Test (2 and 3)", 
				TranscodeJobDefList.fromJSON(TranscodeJob.getConfig(), "s3n://ffmpeg-mr/job-submissions/TestThreading4.json"), 
				jcSettings);
		bench.save(TranscodeJob.getConfig(), "file://" + new File("./benchmarks/MultiThreading.json").getAbsolutePath());
	}
	
	public static void main(String[] args) throws IOException, URISyntaxException{		
		if(args.length >= 1)
		{
			Benchmark bench = Benchmark.loadDefinition(TranscodeJob.getConfig(), args[0]);
			System.out.println("Processing benchmark: " + bench.getName());
			bench.processRequest();
			System.out.println("Benchmark submitted.");
			System.out.println("Output data URL: " + bench.getResultsURL());
			bench.save();
		}
		else
			System.out.println("Please specify a benchmark definition file.");
	}
}
