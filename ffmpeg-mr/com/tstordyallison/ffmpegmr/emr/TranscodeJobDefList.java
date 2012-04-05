package com.tstordyallison.ffmpegmr.emr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

public class TranscodeJobDefList {
	private static transient Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private List<TranscodeJobDef> jobs = new ArrayList<TranscodeJobDef>();
	
	public List<TranscodeJobDef> getJobs() {
		return jobs;
	}

	public void setJobs(List<TranscodeJobDef> jobs) {
		this.jobs = jobs;
	}
	
	public void add(TranscodeJobDef job){
		this.jobs.add(job);
	}
	
	public static TranscodeJobDefList fromJSON(Configuration conf, String uri) throws IOException, URISyntaxException
	{
		FileSystem fs;
		if(uri.startsWith("file://"))
			fs = new RawLocalFileSystem();
		else
			fs = FileSystem.get(new URI(uri), conf);
		Path path = new Path(uri);
		FSDataInputStream fis = fs.open(path);
        BufferedReader input = new BufferedReader(new InputStreamReader(fis));
        TranscodeJobDefList list = gson.fromJson(input, TranscodeJobDefList.class);
        input.close();
        fis.close();
        fs.close();
        return list;
	}
	
	public void toJSON(Configuration conf, String uri) throws IOException, URISyntaxException
	{
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
        gson.toJson(this, TranscodeJobDefList.class, writer);
        output.close();
        fos.close();
        fs.close();
	}
}
