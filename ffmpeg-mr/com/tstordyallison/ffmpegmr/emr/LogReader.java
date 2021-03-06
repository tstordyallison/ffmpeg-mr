package com.tstordyallison.ffmpegmr.emr;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;

/**
 * Connects to the SimpleDB Domain containing FFmpeg logs, and tails the output for the cluster.
 * 
 */
public class LogReader implements Runnable {

	private SortedSet<LogEntry> entries = new TreeSet<LogEntry>();
	private AmazonSimpleDB sdb;
	private final static String LOGGING_DOMAIN = "FFmepg-mr-Logging";
	private String jobID = "";
	private boolean ending = false;
	private int pollInterval = 2500;
	private DateTime startTime;
	
	public LogReader(String jobID, DateTime startFrom)
	{
		this.startTime = startFrom;
		this.jobID = jobID;	
	}
	
	public LogReader(String jobID){
		this(jobID, new DateTime(0));
	}
	
	@Override
	public void run() {
		// Connect to SDB.
		try {
			sdb = new AmazonSimpleDBClient(new PropertiesCredentials(JobController.class
			        .getResourceAsStream("/com/tstordyallison/ffmpegmr/emr/AwsCredentials.properties")));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		sdb.setEndpoint("sdb.eu-west-1.amazonaws.com");
		
		while(!ending)
		{
			String time;
			if(entries.size() == 0)
				time = startTime.toDateTime(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss.SSS");
			else
				time = entries.last().getTimestampRaw();
			
			String sql = "SELECT * FROM `" + LOGGING_DOMAIN + 
					"` WHERE `JobID` = \"" + jobID + "\"" +
					" AND `Time` > \"" + time +"\" ORDER BY `Time` ASC limit 25";
			
			System.out.println("Polling: " + sql);
			
			String nextToken = null;
			do{
				SelectResult result = sdb.select(new SelectRequest(sql).withNextToken(nextToken));
				
				for(Item item : result.getItems())
				{
					LogEntry entry = new LogEntry(item);
					if(!entries.contains(entry)){
						entries.add(entry);
						System.out.println(entry.toString());
					}
				}	
				
				nextToken = result.getNextToken();
			} while (nextToken != null);
			
			if(entries.size() > 0 && entries.last().getMessage().contains("JobRun/EndTime"))
				break;
			
			try {
				Thread.sleep(pollInterval);
			} catch (InterruptedException e) {
				ending = true;
				return;
			}
		}
	}
	
	public void setPollInterval(int ms)
	{
		pollInterval = ms;
	}
	
	public void end(){
		ending = true;
	}
	
	public static void main(String[] args) throws InterruptedException {
		// Set args if it isn't set already.
		if(args.length == 0)
		{
			args = new String[1];
			args[0] = "0ba88b4f-d30b-4260-990f-fddf995b103a";
		}
		
		LogReader reader = new LogReader(args[0]);
		reader.run();
	}
	

}
