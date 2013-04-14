package com.tstordyallison.ffmpegmr.hadoop;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.util.OSValidator;

public class FFmpegMock {
	
	public static void convert(Configuration config, File input, File output){
		Logger logger = new Logger(config);
		String javaLibPath = System.getProperty("java.library.path");
		if(javaLibPath.indexOf(":") > -1)
			javaLibPath = javaLibPath.substring(0, javaLibPath.indexOf(":"));
		
		float videoResScale = config.getFloat("ffmpeg-mr.videoResScale", 1);
		float videoCrf = config.getFloat("ffmpeg-mr.videoCrf", 21);
		int videoBitrate = config.getInt("ffmpeg-mr.videoBitrate", 512000);
		int audioBitrate = config.getInt("ffmpeg-mr.audioBitrate", 128000);
		int videoThreads = config.getInt("ffmpeg-mr.videoThreads", 0);

		List<String> args = new ArrayList<String>();
		if(OSValidator.isMac())
			args.add("ffmpeg");
		else
			args.add(new File(javaLibPath + "/ffmpeg").getAbsolutePath());
		args.add("-y");
		args.add("-benchmark");
		//args.add("-v"); args.add("error");
		args.add("-threads"); args.add(Integer.toString(videoThreads));
		args.add("-i");
		args.add(input.getAbsolutePath());

		// Output options
		args.add("-f"); args.add("matroska");
		args.add("-vcodec"); args.add("libx264"); args.add("-preset"); args.add("medium"); 
		if(videoCrf > 0){
			args.add("-crf"); args.add(Float.toString(videoCrf));
		}
		else{
			args.add("-b:v"); args.add(Integer.toString(videoBitrate));
		}
		if(videoResScale != 1)
		{
			args.add("-vf"); args.add("scale=" + Float.toString(videoResScale) + "*iw:" + Float.toString(videoResScale) + "*ih");
		}
		args.add("-acodec"); args.add("libfaac");
		args.add("-b:a"); args.add(Integer.toString(audioBitrate));
		args.add("-map"); args.add("0");    // Map all streams (not just the first v and a).
		args.add("-map"); args.add("-0:s"); // Disable subtitle streams.
		args.add("-map"); args.add("-0:d"); // Disable data streams.
		args.add(output.getAbsolutePath());
		
		StringBuilder argsString = new StringBuilder();
		for(String arg : args){
			argsString.append(arg);
			argsString.append(" ");
		}
		
		logger.println("Executing command: " + argsString.toString());
		
		ProcessBuilder builder = new ProcessBuilder(args);
		builder.directory(new File(javaLibPath));
		builder.environment().put("LD_LIBRARY_PATH", System.getenv("LD_LIBRARY_PATH") + ":" + javaLibPath);
		builder.redirectErrorStream(true);
		
		try {
			logger.logEntry("Starting ffmpeg: " + args);
			Process process = builder.start();
			Thread execThread = new Thread(new FFmpegOutputReader(logger, process));
			execThread.setPriority(Thread.NORM_PRIORITY + 1);
			execThread.start();
			process.waitFor();
			// FIXME: Add a logger increment to get the progress to 100%.
			int exitCode = process.exitValue();
			if(exitCode != 0)
				throw new RuntimeException("FFmpeg exec failed - exit code:" + exitCode);
		} catch (IOException e) {
			e.printStackTrace();
			new RuntimeException("Unable to start FFmpeg executable.");
		} catch (InterruptedException e) {
			e.printStackTrace();
			new RuntimeException("FFmpeg run interrupted.");
		}
	
		logger.flush();
	}
	
	private static class FFmpegOutputReader implements Runnable {
		private Process p;
		private Logger logger;
		
		public FFmpegOutputReader(Logger logger, Process process) {
			this.p = process;
			this.logger = logger;
		}

		@Override
		public void run() {
			InputStreamReader input = new InputStreamReader(p.getInputStream());
			Pattern durationPattern = Pattern.compile("Duration:.[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\\.[0-9][0-9]");
			Pattern currentDurationPattern = Pattern.compile("time=[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\\.[0-9][0-9]");
			DateTimeFormatter formatter = DateTimeFormat.forPattern("HH:mm:ss.SS").withZoneUTC();
			
			long duration = 0;
			long currentDuration = 0;
			char value;
			try
			{
				StringBuilder lineBuffer = new StringBuilder();
				while (true)
				{
					value = (char)input.read();
					if((int)value == 65535 || (int)value < 0)
						break;
					
					if(value == '\n' || value == '\r'){
						String line = lineBuffer.toString();
						
						if(value == '\n')
							logger.println(line);
						
						try {
							// Find the total duration.
							Matcher durationMatcher = durationPattern.matcher(line);
							if(duration == 0 && durationMatcher.find()){
								String time = durationMatcher.group().split(" ", 2)[1].trim();
								duration = formatter.parseMillis(time);
								logger.incrementGlobalCounter("StreamCount", duration);
								logger.println("Duration in ms: " + duration);
							}
							
							// Find the current duration.
							Matcher currentMatcher = currentDurationPattern.matcher(line);
							if(duration != 0 && currentMatcher.find()){
								String time = currentMatcher.group().split("=", 2)[1].trim();
								long ts = formatter.parseMillis(time);
								long increment = ts - currentDuration;
								int percentage = (int)(((double)ts / duration) * 100);
								int percentageChange = (int)(((double)increment / duration) * 100);
								if( percentageChange >= 1){ 
									currentDuration = ts;
									logger.println("Current progress: " + percentage + "%");
									logger.incrementGlobalCounter("StreamProgress", increment);
								}	
							}
							
						} catch (Exception e) {
							// Ignore - this stuff isn't really that important.
							e.printStackTrace();
						}

						// Reset the buffer.
						lineBuffer = new StringBuilder();
					}
					else
						lineBuffer.append(value);
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

}
