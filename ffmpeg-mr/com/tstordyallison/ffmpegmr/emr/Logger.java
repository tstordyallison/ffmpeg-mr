package com.tstordyallison.ffmpegmr.emr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.tstordyallison.ffmpegmr.emr.JobflowConfiguration.JobFlow;

public class Logger {
	
	public final static String SDB_ENDPOINT = "sdb.eu-west-1.amazonaws.com";
	public final static String JOB_DOMAIN = "FFmepg-mr-Jobs";
	public final static String LOGGING_DOMAIN = "FFmepg-mr-Logging";
	public final static String CONF_JOBID_NAME = "ffmpeg-mr.jobID";
	public final static String CONF_JOBCOUNTER_NAME = "ffmpeg-mr.jobCounter";
	
	public enum TimedEvent{
		CONTROLLER("Controller"),
		JOBRUN("JobRun"),
		JOB("Job"),
		RAW_COPY_IN("RawCopyIn"),
		DEMUX("Demux"),
		HADOOP_JOB("HadoopJob"),
		MERGE("Merge"), 
		RAW_COPY_OUT("RawCopyOut");

		private TimedEvent(String name) {
			this.name = name;
		}
		private final String name;
		public String toString() {
			return name;
		}
	
		public static TimedEvent getFromToString(String toString)
		{
			for(TimedEvent te : TimedEvent.values())
				if(te.toString().equals(toString))
						return te;
			return null;
		}
	}
	
	public static class Printer {
		
		public static boolean ENABLED = true;
		public static boolean USE_ERR = true;

		private static void println(String text)
		{
			if(ENABLED)
			{
				DateTime dt = new DateTime();
				String dtString = "[" + dt.toString("HH:mm:ss") + "] ";
				if(USE_ERR)
					System.err.println(dtString+text);
				else
					System.out.println(dtString+text);	
			}
				
		}
		
		private static void print(String text)
		{	
			DateTime dt = new DateTime();
			String dtString = "[" + dt.toString("HH:mm:ss") + "] ";
			if(ENABLED)
			{
				if(USE_ERR)
					System.err.print(dtString+text);
				else
					System.out.print(dtString+text);
			}
		}
	}
	
	private static Logger logger = new Logger(new Configuration());
	
	private AmazonSimpleDB sdb;
	private static ExecutorService exec = Executors.newFixedThreadPool(2);
	private Configuration config;
//	private static boolean domainsCreated = true;
	
	public Logger(Configuration config)
	{
		this.config = config;
		
		// Connect to SDB.
		try {
			sdb = new AmazonSimpleDBClient(new PropertiesCredentials(JobController.class
			        .getResourceAsStream("/com/tstordyallison/ffmpegmr/emr/AwsCredentials.properties")));
		} catch (IOException e) {
			e.printStackTrace();
			// Doh!
		}
		sdb.setEndpoint(SDB_ENDPOINT);
		
//		if(!domainsCreated) {
//			// Make sure we have our domain to work with.
//			sdb.createDomain(new CreateDomainRequest(LOGGING_DOMAIN));
//			sdb.createDomain(new CreateDomainRequest(JOB_DOMAIN));
//			domainsCreated = true;
//		}
	}
	
	public static void println(Configuration config, Object message)
	{
		logger.config = config;
		logger.println(message);
	}
	
	public void println(Object message)
	{
		Printer.println(message.toString());
		logEntry(message.toString());
	}
	
	public void print(Object message)
	{
		Printer.print(message.toString());
		logEntry(message.toString());
	}
	
	public void logEntry(String message){
		if(config.get(CONF_JOBID_NAME) != null)
			logEntry(config.get(CONF_JOBID_NAME), config.getInt(CONF_JOBCOUNTER_NAME, 0), message);
	}
	
	public void markStartTime(TimedEvent timerName){
		markTimingEvent(timerName, "StartTime");
	}
	
	public void markEndTime(TimedEvent timerName){
		markTimingEvent(timerName, "EndTime");
	}
	
	public void logException(Configuration config, Throwable t){
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		logEntry(sw.toString());
		t.printStackTrace();
	}
	
	private void logEntry(final String jobId, final int jobCounter, String message)
	{
		DateTime dt = new DateTime(DateTimeZone.UTC); 
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
		final String time = fmt.print(dt);
		
		final String actualMesssage;
		if(message.length() > 1024)
			actualMesssage = message.substring(0, 1021) + "...";
		else
			actualMesssage = message;

		synchronized (exec) {
			exec.execute(new Runnable() {
				@Override
				public void run() {
					try {
						List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
						attributes.add(new ReplaceableAttribute().withName("Time").withValue(time));
						attributes.add(new ReplaceableAttribute().withName("JobID").withValue(jobId));
						attributes.add(new ReplaceableAttribute().withName("JobCounter").withValue(String.format("%05d", jobCounter)));
						attributes.add(new ReplaceableAttribute().withName("Message").withValue(actualMesssage));

						sdb.putAttributes(new PutAttributesRequest(LOGGING_DOMAIN, UUID.randomUUID().toString(), attributes));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
	}
	
	private void markTimingEvent(final TimedEvent timerName, final String suffix)
	{
		if(config.get(CONF_JOBID_NAME) != null){
			final String itemName = config.get(CONF_JOBID_NAME) + "-" + config.getInt(CONF_JOBCOUNTER_NAME, 0);
			final String time = new DateTime(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss.SSS");
			
			synchronized (exec) {
				exec.execute(new Runnable() {
					@Override
					public void run() {
						boolean success = false;
						int counter = 0;
						while(!success && counter <= 10){
							try {
								counter += 1;
								List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
								attributes.add(new ReplaceableAttribute().withName(timerName.toString() + "/" + suffix).withValue(time));
								sdb.putAttributes(new PutAttributesRequest(JOB_DOMAIN, itemName, attributes));
								success = true;
							} catch (Exception e) {
								e.printStackTrace();
								success = false;
							}
							
							if(!success){
								if(Thread.interrupted())
									break;
								
								long delay = (long) (Math.random() * (Math.pow(4, counter) * 10L));
						        try {
						        	Thread.sleep(delay);
						        } catch (InterruptedException iex){
						        }
							}
						}
						
						if(!success && counter > 10)
						{
							throw new RuntimeException("Failed to log timing event after 10 exp attempts (or a flush). This is a fatal error.");
						}
					}
				});
			}
		}
	}
	
	public void logClusterDetails(final JobFlow jobflow, final TranscodeJobDef jobDef) {
		if(config.get(CONF_JOBID_NAME) != null && jobflow != null){
			final String itemName = config.get(CONF_JOBID_NAME) + "-" + config.getInt(CONF_JOBCOUNTER_NAME, 0);
			final int mapTaskCount = config.getInt("mapred.tasktracker.map.tasks.maximum", -1);
			synchronized (exec) {
				exec.execute(new Runnable() {
					@Override
					public void run() {
						long fileSize = -1;
						int chunkSize = -1;
						
						if(jobDef != null){
							try {
								fileSize = FileSystem.get(new URI(jobDef.getInputUri()), config).getFileStatus(new Path(jobDef.getInputUri())).getLen();
							} catch (IOException e1) {
								e1.printStackTrace();
							} catch (URISyntaxException e1) {
								e1.printStackTrace();
							}
							chunkSize = jobDef.getDemuxChunkSize();
						}
						
						boolean success = false;
						int counter = 0;
						while(!success && counter <= 10){
							try {
								counter += 1;
								List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
								attributes.add(new ReplaceableAttribute().withName("jobFlowId").withValue(jobflow.jobFlowId));
								attributes.add(new ReplaceableAttribute().withName("instanceCount").withValue(Integer.toString(jobflow.coreInstanceCount)));
								attributes.add(new ReplaceableAttribute().withName("mapTaskCount").withValue(Integer.toString(mapTaskCount)));
								attributes.add(new ReplaceableAttribute().withName("blockSize").withValue(Integer.toString(chunkSize)));
								attributes.add(new ReplaceableAttribute().withName("fileSize").withValue(Long.toString(fileSize)));
								sdb.putAttributes(new PutAttributesRequest(JOB_DOMAIN, itemName, attributes));
								success = true;
							} catch (Exception e) {
								e.printStackTrace();
								success = false;
							}
							
							if(!success){
								if(Thread.interrupted())
									break;
								
								long delay = (long) (Math.random() * (Math.pow(4, counter) * 10L));
						        try {
						        	Thread.sleep(delay);
						        } catch (InterruptedException iex){
						        }
							}
						}
						
						if(!success && counter > 10)
						{
							throw new RuntimeException("Failed to log timing event after 10 exp attempts (or a flush). This is a fatal error.");
						}
					}
				});
			}
		}
		
	}

	public void flush()
	{
		synchronized (exec) {
			exec.shutdown();
			try {
				exec.awaitTermination(15, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
			}
			exec = Executors.newFixedThreadPool(2);
		}
		
	}

}
