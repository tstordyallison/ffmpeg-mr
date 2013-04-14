package com.tstordyallison.ffmpegmr.emr;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tstordyallison.ffmpegmr.emr.Logger.TimedEvent;


public class JobProgressWatcher {
	
	public interface JobProgressUpdateListener {
		public void streamProgressChanged(JobProgressWatcher sender, int jobCounter, int stream, int newValue, int total);
		public void newStream(JobProgressWatcher sender, int jobCounter, int stream);
	}
	
	private class JobProgressWatcherThread implements Runnable {
		private Map<TimeEntry, Map<Integer, ProgressFraction>> currentEntries = new HashMap<TimeEntry, Map<Integer, ProgressFraction>>();
		
		@Override
		public void run() {
			while(keepMonitoring && !Thread.interrupted()){
				// Get the new TimeEntry list.
				List<TimeEntry> entries = TimeEntry.getTimeEntries(jobID);

				// Save the old entries.
				Map<TimeEntry, Map<Integer, ProgressFraction>> oldEntries = new HashMap<TimeEntry, Map<Integer, ProgressFraction>>(currentEntries);
				
				// Save the new version of the entries.
				currentEntries.clear();
				if(entries != null)
					for(TimeEntry entry : entries)
						currentEntries.put(entry, entry.getStreamProgress());
				
				// Compare the entries with our current version, and send notifications.
				if(entries != null)
					for(TimeEntry entry : entries){
						// Is this a new job?
						if(!oldEntries.containsKey(entry)){
							for(Integer stream : entry.getStreamProgress().keySet()){
								ProgressFraction progress = entry.getStreamProgress().get(stream);
								if(progress.getTotalValue() > 0){
									notifyNewStream(entry.getJobCounter(), stream);
									notifyStreamProgressChanged(entry.getJobCounter(), stream, progress.getCurrentValue(), progress.getTotalValue());
								}
							}
						}
						else{
							for(Integer stream : entry.getStreamProgress().keySet()){
								Map<Integer, ProgressFraction> oldEntry = oldEntries.get(entry);
								ProgressFraction progress = entry.getStreamProgress().get(stream);
								
								// Is this a new stream?
								if(!oldEntry.containsKey(stream)){
									notifyNewStream(entry.getJobCounter(), stream);
									notifyStreamProgressChanged(entry.getJobCounter(), stream, progress.getCurrentValue(), progress.getTotalValue());
								}
								else{
									// If not, has the progress changed?
									if(!oldEntry.get(stream).equals(progress))
										notifyStreamProgressChanged(entry.getJobCounter(), stream, progress.getCurrentValue(), progress.getTotalValue());
								}
							}
						}				
					}	
				
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
	
	private Set<JobProgressUpdateListener> listeners = Collections.synchronizedSet(new HashSet<JobProgressUpdateListener>());
	private int pollInterval = 2000;
	private String jobID;
	private boolean keepMonitoring = true;
	private Thread monitor = null;
	private JobProgressWatcherThread watcher = new JobProgressWatcherThread();
	
	public JobProgressWatcher(String jobID){
		this(jobID, true);
	}
	
	public JobProgressWatcher(String jobID, boolean startMonitoring){
		this.jobID = jobID;
		if(startMonitoring)
			startMonitoring();
	}
	
	public void registerProgress(JobProgressUpdateListener listener){
		listeners.add(listener);
	}
	
	private void notifyStreamProgressChanged(int jobCounter, int stream, int newValue, int total){
		synchronized(listeners) {
			for(JobProgressUpdateListener listener : listeners)
				if(total != 0)
					listener.streamProgressChanged(this, jobCounter, stream, newValue, total);
		}
	}
	private void notifyNewStream(int jobCounter, int stream){
		synchronized(listeners) {
			for(JobProgressUpdateListener listener : listeners)
				listener.newStream(this, jobCounter, stream);
		}
	}
	
	public Map<TimeEntry, Map<Integer, ProgressFraction>> getCurrentProgress(){
		if(watcher != null)
			return watcher.currentEntries;
		else
			return null;
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
	
	public void monitorJoin(){
		try {
			monitor.join();
		} catch (InterruptedException e) {
		}
	}
	
	// ---------------------------------------------
	
	public static void main(String[] args){
		// Set args if it isn't set already.
		if(args.length == 0)
		{
			args = new String[1];
			args[0] = "18212e0e-8360-4bf5-9e80-412efc7a5b6b-1";
		}
		
		// Test the watcher.
		JobProgressWatcher watcher = new JobProgressWatcher(args[0]);
		watcher.registerProgress(new JobProgressUpdateListener() {
			@Override
			public void streamProgressChanged(JobProgressWatcher sender, int jobCounter, int stream, int newValue, int total) {
				Logger.Printer.print(String.format("Stream [%d]:%d: %2.2f%% (%d of %d)\n", jobCounter, stream, ((double)newValue/total)*100, newValue, total));
			}
			
			@Override
			public void newStream(JobProgressWatcher sender, int jobCounter, int stream) {
				//System.out.printf("Stream Added [%d]:%d\n", jobCounter, stream);
			}
		});
		watcher.monitorJoin();
	}
}
