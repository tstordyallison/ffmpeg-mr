package com.tstordyallison.ffmpegmr.emr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.tstordyallison.ffmpegmr.emr.Logger.TimedEvent;

public class TimeEntry implements Comparable<TimeEntry> {
	
	private static AmazonSimpleDB sdb;
	
	private DateTime startDate = null;
	private String jobID = "Unknown";
	private int jobCounter = -1;
	private String jobFlowID = "Unknown";
	private int instanceCount = -1;
	private int mapTaskCount = -1;
	private int blockSize = -1;
	private int fileSize = -1;
	
	private Map<Logger.TimedEvent, Period> timings = new HashMap<Logger.TimedEvent, Period>();
	private Map<Logger.TimedEvent, DateTime> startTimes = new HashMap<Logger.TimedEvent, DateTime>();
	private Map<Logger.TimedEvent, DateTime> endTimes = new HashMap<Logger.TimedEvent, DateTime>();
	
	public TimeEntry(Item item){
		String uuid = item.getName();
		
		// Figure out the job id etc
		int splitPoint = uuid.lastIndexOf("-");
		this.jobID = uuid.substring(0, splitPoint-1);
		this.jobCounter = Integer.parseInt(uuid.substring(splitPoint+1));
		
		// Get all of the timestamps.
		for(Attribute at : item.getAttributes())
		{
			String name = at.getName();
			
			if(name.contains("Time")){
				String[] split = name.split("/");
				if(split.length != 2)
					split = name.split(" ");
			
				TimedEvent event = TimedEvent.getFromToString(split[0]);
				DateTime time = new DateTime(new Instant(at.getValue()), DateTimeZone.UTC).toDateTime(DateTimeZone.getDefault());
				
				if(split[1].equals("StartTime"))
					startTimes.put(event, time);
				if(split[1].equals("EndTime"))
					endTimes.put(event, time);
				
				if(startDate == null || time.compareTo(startDate) < 0)
					startDate = time;
			}
			
			// Process other non-time related attributes.
			if(name.equals("jobFlowId"))
				jobFlowID = at.getValue();
			if(name.equals("instanceCount"))
				instanceCount = Integer.parseInt(at.getValue());
			if(name.equals("mapTaskCount"))
				mapTaskCount = Integer.parseInt(at.getValue());
			if(name.equals("fileSize"))
				fileSize = Integer.parseInt(at.getValue());
			if(name.equals("blockSize"))
				blockSize = Integer.parseInt(at.getValue());
		}
		
		// Calculate the timings in seconds.
		for(TimedEvent te : TimedEvent.values())
		{
			if(startTimes.containsKey(te) && endTimes.containsKey(te))
			{
				DateTime start = startTimes.get(te);
				DateTime end = endTimes.get(te);
				
				timings.put(te, new Period(start, end));
			}
		}
	}

	public String getJobID() {
		return jobID;
	}

	public int getJobCounter() {
		return jobCounter;
	}

	public String getJobFlowID() {
		return jobFlowID;
	}

	public int getInstanceCount() {
		return instanceCount;
	}

	public int getMapTaskCount() {
		return mapTaskCount;
	}

	public DateTime getStartDate() {
		return startDate;
	}

	public void setStartDate(DateTime startDate) {
		this.startDate = startDate;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public int getFileSize() {
		return fileSize;
	}

	public Map<Logger.TimedEvent, Period> getTimings() {
		return Collections.unmodifiableMap(timings);
	}

	public Map<Logger.TimedEvent, DateTime> getStartTimes() {
		return Collections.unmodifiableMap(startTimes);
	}

	public Map<Logger.TimedEvent, DateTime> getEndTimes() {
		return Collections.unmodifiableMap(endTimes);
	}

	@Override
	public int compareTo(TimeEntry o) {
		if(startDate == null && o.startDate == null)
			return 0;
		else if(startDate != null && o.startDate != null)
			return startDate.compareTo(o.startDate);
		else if(startDate == null)
			return -1;
		else //if(o.startDate == null)
			return 1;
	}

	@Override
	public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        if (jobID != null) sb.append("\tJobID: " + jobID + "\n");
        if (jobCounter != -1) sb.append("\tJobCounter: " + jobCounter + "\n");
        if (timings != null) sb.append("\tTimings: " + timings + "\n");
        sb.append("\n");
        return sb.toString();
	}

	// ------------------------------------------------------------------
	
	public static List<TimeEntry> getTimeEntries(String jobID){
		SelectResult result = getSDB().select(
				new SelectRequest("SELECT * FROM `" + Logger.JOB_DOMAIN + "` " + 
						 "where itemName() > \"" + jobID +  "\" and itemName() < \"" + jobID + "-99999\" limit 1000"));
		
		List<TimeEntry> timeEntries = new ArrayList<TimeEntry>();
		for(Item item : result.getItems())
			timeEntries.add(new TimeEntry(item));
		
		return timeEntries;
	}
	
	public static SortedSet<TimeEntry> getTimeEntriesByFlow(String jobFlowID){
		SelectResult result = getSDB().select(
				new SelectRequest("SELECT * FROM `" + Logger.JOB_DOMAIN + "` " + 
						 "where jobFlowId = \"" + jobFlowID + "\" limit 2000"));
		
		SortedSet<TimeEntry> timeEntries = new TreeSet<TimeEntry>();
		for(Item item : result.getItems())
			timeEntries.add(new TimeEntry(item));
		
		return timeEntries;
	}
	
	private static AmazonSimpleDB getSDB(){
		if(sdb == null){
			try {
				sdb = new AmazonSimpleDBClient(new PropertiesCredentials(JobController.class
				        .getResourceAsStream("/com/tstordyallison/ffmpegmr/emr/AwsCredentials.properties")));
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			sdb.setEndpoint(Logger.SDB_ENDPOINT);
		}
		return sdb;
	}
	
	public static void printJobTimingInfo(String jobID){
		System.out.println("JobID: " + jobID);
		List<TimeEntry> timeEntries = TimeEntry.getTimeEntries(jobID);
		for(TimeEntry te : timeEntries)
		{
			System.out.println("--------");
			System.out.println("JobCounter: " + te.getJobCounter());
			System.out.println("Timings: ");
			for(TimedEvent timedEvent : te.getTimings().keySet())
				System.out.println(String.format("\t%10s: %s", timedEvent.toString(), PeriodFormat.getDefault().print(te.getTimings().get(timedEvent))));
			if(te.getTimings().size() == 0)
				System.out.println("\tNo timing information.");
		}
	}
	
	public static void main(String[] args)
	{
		String jobID = "a4b48934-8be6-4d4b-93e4-e795711befea";
		printJobTimingInfo(jobID);
	}
}
