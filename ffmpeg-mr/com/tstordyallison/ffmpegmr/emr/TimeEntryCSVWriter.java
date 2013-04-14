package com.tstordyallison.ffmpegmr.emr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;

import com.csvreader.CsvWriter;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob;

public class TimeEntryCSVWriter extends CsvWriter {

	public TimeEntryCSVWriter(OutputStream outputStream, char delimiter, Charset charset) {
		super(outputStream, delimiter, charset);
	}

	public TimeEntryCSVWriter(String fileName, char delimiter, Charset charset) {
		super(fileName, delimiter, charset);
	}

	public TimeEntryCSVWriter(String fileName) {
		super(fileName);
	}

	public TimeEntryCSVWriter(Writer outputStream, char delimiter) {
		super(outputStream, delimiter);
	}
	
	public void writeTimeEntryHeader() throws IOException
	{
		write("JobID");
		write("JobFlowID");
		write("InstanceCount");
		write("MapTaskCount");
		write("JobCounter");
		write("BlockSize");
		write("FileSize");
		write(Logger.TimedEvent.RAW_COPY_IN.toString());
		write(Logger.TimedEvent.DEMUX.toString());
		write(Logger.TimedEvent.PROCESS_JOB.toString());
		write(Logger.TimedEvent.MERGE.toString());
		write(Logger.TimedEvent.RAW_COPY_OUT.toString());
		write(Logger.TimedEvent.JOB.toString());
		write(Logger.TimedEvent.JOBRUN.toString());
		endRecord();
	}
	
	public void writeTimeEntry(TimeEntry entry) throws IOException
	{
		write(entry.getJobID());
		write(entry.getJobFlowID());
		write(Integer.toString(entry.getInstanceCount()));
		write(Integer.toString(entry.getMapTaskCount()));
		write(Integer.toString(entry.getJobCounter()));
		write(Integer.toString(entry.getBlockSize()));
		write(Long.toString(entry.getFileSize()));
		write(entry.getTimings().containsKey(Logger.TimedEvent.RAW_COPY_IN) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.RAW_COPY_IN).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.DEMUX) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.DEMUX).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.PROCESS_JOB) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.PROCESS_JOB).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.MERGE) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.MERGE).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.RAW_COPY_OUT) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.RAW_COPY_OUT).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.JOB) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.JOB).toStandardDuration().getStandardSeconds()) : "0");
		write(entry.getTimings().containsKey(Logger.TimedEvent.JOBRUN) ? 
				Long.toString(entry.getTimings().get(Logger.TimedEvent.JOBRUN).toStandardDuration().getStandardSeconds()) : "0");
		endRecord();
	}

	public static void outputTimings(String filePath, String jobFlowID) throws IOException
	{
		File outputFile = new File(filePath);
		boolean exists = outputFile.exists();
		TimeEntryCSVWriter writer = new TimeEntryCSVWriter(new FileWriter(outputFile, true), ',');
		if(!exists) 
			writer.writeTimeEntryHeader();
		for(TimeEntry entry : TimeEntry.getTimeEntriesByFlow(jobFlowID))
			writer.writeTimeEntry(entry);
		writer.close();
	}

	public static void main(String[] args) throws IOException, URISyntaxException{
		Configuration conf = TranscodeJob.getConfig();
		String uri = args[0];
		FileSystem fs = FileSystem.get(new URI(uri), conf);
		Path path = new Path(uri);
		
		boolean exists;
		FSDataOutputStream fos;
		if((exists = fs.exists(path)))
			fos = fs.append(path);
		else
			fos = fs.create(path);
		
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(fos));
		TimeEntryCSVWriter writer = new TimeEntryCSVWriter(output, ',');
		if(!exists) 
			writer.writeTimeEntryHeader();
		for(TimeEntry entry : TimeEntry.getTimeEntriesByFlow(args[1]))
			writer.writeTimeEntry(entry);
		writer.close();
		output.close();
		fos.close();
		fs.close();
	}
}
