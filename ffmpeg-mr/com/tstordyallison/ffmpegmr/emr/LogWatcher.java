package com.tstordyallison.ffmpegmr.emr;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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


public class LogWatcher {
	
	public interface LogWatcherListener {
		public void logEntry(LogWatcher sender, String text);
	}
	
	private class LogWatcherThread implements Runnable {
		private SortedSet<LogEntry> entries = Collections.synchronizedSortedSet(new TreeSet<LogEntry>());
		
		@Override
		public void run() {
			while(keepMonitoring && !Thread.interrupted()){
				String time;
				if(entries.size() == 0)
					time = startTime.toDateTime(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss.SSS");
				else
					time = entries.last().getTimestampRaw();
			
				// Subsequent downloads.
				String sql = "SELECT * FROM `" + LOGGING_DOMAIN + 
						"` WHERE `JobID` = \"" + jobID + "\"" +
						(jobCounter > 0 ? " AND `JobCounter` = \"" + String.format("%05d", jobCounter) + "\"" : "") +
						" AND `Time` > \"" + time +"\" ORDER BY `Time` ASC";
				
				String nextToken = null;
				do{
					SelectResult result = getSDB().select(new SelectRequest(sql).withNextToken(nextToken));
					
					for(Item item : result.getItems())
					{
						LogEntry entry = new LogEntry(item);
						if(!entries.contains(entry)){
							entries.add(entry);
							notifyLogEntry(entry.toString());
						}
					}	
					
					nextToken = result.getNextToken();
				} while (nextToken != null);

				if(entries.size() > 0 && entries.last().getMessage().contains("JobRun/EndTime"))
					break;
				
				// Wait again until we poll.
				if(!Thread.interrupted()){
					try {
						Thread.sleep(pollInterval);
					} catch (InterruptedException e) {
						break;
					}

				}
			}
		}
	}
	
	private Set<LogWatcherListener> listeners = Collections.synchronizedSet(new HashSet<LogWatcherListener>());
	private int pollInterval = 2500;
	private String jobID;
	private int jobCounter = -1;
	private boolean keepMonitoring = true;
	private Thread monitor = null;
	private DateTime startTime;
	private LogWatcherThread watcher = new LogWatcherThread();
	private final static String LOGGING_DOMAIN = "FFmepg-mr-Logging";
	private static AmazonSimpleDB sdb;
	
	public LogWatcher(String jobID){
		this(jobID, new DateTime(0), true);
	}
	
	public LogWatcher(String jobID, DateTime startFrom, boolean startMonitoring){
		this.startTime = startFrom;
		
		String[] dashSplit = jobID.split("-");
		if(dashSplit.length == 6){
			this.jobCounter = Integer.parseInt(dashSplit[5]);
			this.jobID = jobID.substring(0, jobID.lastIndexOf("-"));
		}
		else
			this.jobID = jobID;	
		
		if(startMonitoring)
			startMonitoring();
	}
	
	public void registerProgress(LogWatcherListener listener){
		listeners.add(listener);
	}
	
	private void notifyLogEntry(String text){
		synchronized(listeners) {
			for(LogWatcherListener listener : listeners)
				listener.logEntry(this, text);
		}
	}
		
	public void startMonitoring(){
		if(monitor == null){
			keepMonitoring = true;
			monitor = new Thread(watcher);
			monitor.start();
		}
	}
	public void endMonitoring(){
		if(monitor != null){
			keepMonitoring = false;
			try {
				monitor.join();
			} catch (InterruptedException e) {
			}
			monitor = null;
		}
	}
	
	public SortedSet<LogEntry> getCurrentLog(){
		return watcher.entries;
	}
	
	public void monitorJoin(){
		try {
			monitor.join();
		} catch (InterruptedException e) {
		}
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
}
