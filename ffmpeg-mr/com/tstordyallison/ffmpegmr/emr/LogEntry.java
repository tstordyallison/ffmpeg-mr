package com.tstordyallison.ffmpegmr.emr;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;

public class LogEntry implements Comparable<LogEntry> {
	private String uuid;
	private String jobID;
	private DateTime timestamp;
	private String timestampRaw;
	private int jobCounter;
	private String message;
	
	public LogEntry(Item item)
	{
		this.uuid = item.getName();
		
		for(Attribute at : item.getAttributes())
		{
			if(at.getName().equals("Time")){
				timestamp = new DateTime(new Instant(at.getValue()), DateTimeZone.UTC).toDateTime(DateTimeZone.getDefault());
				timestampRaw = at.getValue();
			}
			if(at.getName().equals("JobID")){
				jobID = at.getValue();
			}
			if(at.getName().equals("JobCounter")){
				jobCounter = Integer.parseInt(at.getValue());
			}
			if(at.getName().equals("Message")){
				message = at.getValue();
			}
		}
	}
	
	public String getUuid() {
		return uuid;
	}

	public DateTime getTimestamp(){
		return timestamp;
	}
	
	public String getTimestampRaw() {
		return timestampRaw;
	}

	public String getJobID() {
		return jobID;
	}

	public int getJobCounter() {
		return jobCounter;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof LogEntry))
			return false;
		LogEntry other = (LogEntry) obj;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}

	@Override
	public int compareTo(LogEntry o) {
		return this.timestamp.compareTo(o.timestamp);
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("[" + timestamp.toString("HH:mm:ss") + "]");
		if(jobCounter > 0) 
			sb.append("[" + jobCounter + "]");
		sb.append(" " + message);
		return sb.toString(); 
	}
}
