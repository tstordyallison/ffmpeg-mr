package com.tstordyallison.ffmpegmr.hadoop;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.ClusterStatus;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.ClusterMetrics;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.Merger;
import com.tstordyallison.ffmpegmr.WriterThread;

import com.tstordyallison.ffmpegmr.emr.JobflowConfiguration;
import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.emr.TimeEntry;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef;
import com.tstordyallison.ffmpegmr.emr.Logger.TimedEvent;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef.OutputType;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef.ProcessingType;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDefList;
import com.tstordyallison.ffmpegmr.emr.TranscodeJobDef.InputType;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.OSValidator;

public class TranscodeJob extends Configured implements Tool {

	public static enum ProgressCounter { AUDIO_PROGRESS, VIDEO_PROGRESS, COMBINED_PROGRESS, INPUT_PACKETS_PROCESSED} 
	private static URI[] nativeLibs = null;
	private static URI[] nativeLibs64 = null;
	
	public static void main(String[] args) throws Exception {	
		Logger.Printer.ENABLED = true;
		Logger.Printer.USE_ERR = true;
		WriterThread.BLOCK_SIZE *= 1; // 16Mb increments.
		WriterThread.PRINT_WRITE = true;
		FileUtils.PRINT_INFO = true;
		
		try {
			nativeLibs = new URI[] {
				new URI("s3n://ffmpeg-mr/lib/libffmpeg-mr.so#libffmpeg-mr.so"),
			    new URI("s3n://ffmpeg-mr/lib/libavcodec.so#libavcodec.so.54"),
			    new URI("s3n://ffmpeg-mr/lib/libavformat.so#libavformat.so.54"),
			    new URI("s3n://ffmpeg-mr/lib/libavdevice.so#libavdevice.so.53"),
			    new URI("s3n://ffmpeg-mr/lib/libavfilter.so#libavfilter.so.2"),
			    new URI("s3n://ffmpeg-mr/lib/libavutil.so#libavutil.so.51"),
			    new URI("s3n://ffmpeg-mr/lib/libswscale.so#libswscale.so.2"),
			    new URI("s3n://ffmpeg-mr/lib/libfaac.so#libfaac.so.0"),
			    new URI("s3n://ffmpeg-mr/lib/libx264.so#libx264.so.120"),
			    new URI("s3n://ffmpeg-mr/lib/libmp3lame.so#libmp3lame.so.0"),
			    new URI("s3n://ffmpeg-mr/lib/libswresample.so#libswresample.so.0"),
			    new URI("s3n://ffmpeg-mr/lib/libpostproc.so#libpostproc.so.52"),
			    new URI("s3n://ffmpeg-mr/lib/ffmpeg#ffmpeg")
			};
			nativeLibs64 = new URI[] {
				new URI("s3n://ffmpeg-mr/lib64/libffmpeg-mr.so#libffmpeg-mr.so"),
			    new URI("s3n://ffmpeg-mr/lib64/libavcodec.so#libavcodec.so.54"),
			    new URI("s3n://ffmpeg-mr/lib64/libavformat.so#libavformat.so.54"),
			    new URI("s3n://ffmpeg-mr/lib64/libavfilter.so#libavfilter.so.2"),
			    new URI("s3n://ffmpeg-mr/lib64/libavdevice.so#libavdevice.so.53"),
			    new URI("s3n://ffmpeg-mr/lib64/libavutil.so#libavutil.so.51"),
			    new URI("s3n://ffmpeg-mr/lib64/libswscale.so#libswscale.so.2"),
			    new URI("s3n://ffmpeg-mr/lib64/libfaac.so#libfaac.so.0"),
			    new URI("s3n://ffmpeg-mr/lib64/libx264.so#libx264.so.120"),
			    new URI("s3n://ffmpeg-mr/lib64/libmp3lame.so#libmp3lame.so.0"),
			    new URI("s3n://ffmpeg-mr/lib64/libswresample.so#libswresample.so.0"),
			    new URI("s3n://ffmpeg-mr/lib64/libpostproc.so#libpostproc.so.52"),
			    new URI("s3n://ffmpeg-mr/lib64/ffmpeg#ffmpeg")
				};
		} catch (URISyntaxException e) {
		}
		
		if(!OSValidator.isMac())
			copyNativeToLibPath();
		
		// Run the job.
        int res = ToolRunner.run(getConfig(), new TranscodeJob(), args);
        System.exit(res);
	}
		
	@Override
	public int run(String[] args) throws Exception {
		// Get our job ID.
		String jobID = "";
		if(args.length == 1)
			jobID = UUID.randomUUID().toString();
		else if(args.length >= 1)
			jobID = args[1];
			
		// Set up our config and printing.
		Configuration config = getConfig();
		config.set("ffmpeg-mr.jobID", jobID);
		
		// Check for a threading hint.
		if(args.length >=3)
			config.setInt("ffmpeg-mr.videoThreads", Integer.parseInt(args[2]));
		
		// Setup a logger.
		Logger logger = new Logger(config);
		logger.markStartTime(TimedEvent.JOBRUN);
		
		// Get cluster status from Hadoop.
		@SuppressWarnings("deprecation")
		JobClient jobClient = new JobClient(new JobConf(config));
		ClusterStatus status = jobClient.getClusterStatus();
		
		// Get this cluster config information from EMR.
		JobflowConfiguration jobFlowConfig = new JobflowConfiguration();
		logger.logClusterDetails(jobFlowConfig.getJobflow(), status);
		
		try{
			// Copy native binaries to the distributed cache if we are not testing.
			if(!OSValidator.isMac())
			{	
		        DistributedCache.createSymlink(config);
				for(URI lib : nativeLibs64)
					DistributedCache.addCacheFile(lib, config);
			}
			
			logger.logEntry("Starting job run...");
			
			// Get the TranscodeJobDef list.
			TranscodeJobDefList list = TranscodeJobDefList.fromJSON(config, args[0]);
			
			// Print out the job list.
			logger.println("Job submission from: " + args[0]);
			
			// Process each of the jobs.
			int counter = 0;
			for(TranscodeJobDef jobDef : list.getJobs())
			{
				try{
					long[] packetCount = null;
					// -----------------
					// Set job params.
					// -----------------
					counter +=1;
					config.setInt("ffmpeg-mr.jobCounter", counter);
					config.setFloat("ffmpeg-mr.videoResScale", jobDef.getVideoResScale());
					config.setFloat("ffmpeg-mr.videoCrf", jobDef.getVideoCrf());
					config.setInt("ffmpeg-mr.videoBitrate", jobDef.getVideoBitrate());
					config.setInt("ffmpeg-mr.audioBitrate", jobDef.getAudioBitrate());
					if(jobDef.getVideoThreads() >= 0)
						config.setInt("ffmpeg-mr.videoThreads", jobDef.getVideoThreads());
					logger.markStartTime(TimedEvent.JOB);

					// --------------------------------------
					// Log this job starting and print its params.
					// --------------------------------------
					logger.println("Starting job " + counter + ".");
					logger.println(jobDef.toString());
					
					if(jobDef.getProcessingType() == ProcessingType.MapReduce){
						// -----------------------------------------------
						// Demux the file into the local HDFS if needed.
						// -----------------------------------------------
						Path demuxData = null;
						Path movieFile = new Path(jobDef.getInputUri());
						Path outputData = new Path("/tmp/output-temp-" + jobID + "-" + counter);
						
						if(jobDef.getInputType() == InputType.RawFile){
							logger.println("Job requires demux. Demuxing direct from S3 and copying into HDFS.");
							
							demuxData = new Path("/tmp/demux-temp-" + jobID + "-" + counter); 
							
							if(FileSystem.get(movieFile.toUri(), config).getFileStatus(movieFile).getLen() > FileUtils.GIBIBYTE*2){
								logger.println("WARNING: This file is over 2GB and will likely take a very long time to Demux via S3.");
								logger.println("WARNING: Please upload this file as a pre-demuxed SequenceFile to improve performance, or copy locally.");
							}
							
							logger.markStartTime(TimedEvent.DEMUX);
								packetCount = Chunker.chunkInputFile(config, movieFile.toUri().toString(), demuxData.toUri().toString(), jobDef.getDemuxChunkSize()).getPacketCounts();
							logger.markEndTime(TimedEvent.DEMUX);
						}
						if(jobDef.getInputType() == InputType.RawFileCopy){
							logger.println("Job requires demux. Copying locallly, then demuxing and copying into HDFS.");
							
							// Copy the data locally.
							File tempFile = File.createTempFile("temp-demux", ".movie");
							tempFile.deleteOnExit();
							
							logger.markStartTime(TimedEvent.RAW_COPY_IN);
								FileUtils.copy(movieFile, new Path("file://" + tempFile.getAbsolutePath()), false, true, config);
							logger.markEndTime(TimedEvent.RAW_COPY_IN);
							
							// Demux onto HDFS.
							demuxData = new Path("/tmp/demux-temp-" + jobID); 
							
							logger.markStartTime(TimedEvent.DEMUX);
								packetCount = Chunker.chunkInputFile(config, tempFile, demuxData.toUri().toString(), jobDef.getDemuxChunkSize()).getPacketCounts();
							logger.markEndTime(TimedEvent.DEMUX);
							
							tempFile.delete();
						}
						if(jobDef.getInputType() == InputType.Demuxed){
							logger.println("Using a pre-demuxed SequenceFile.");
							demuxData = movieFile;
						}	
						
						logger.logClusterDetails(jobFlowConfig.getJobflow(), status, jobDef, packetCount);
						
						// ------------------------
						// Delete the output if it exists.
						// ------------------------
						if(jobDef.isOverwrite()){
							FileSystem fs = FileSystem.get(new URI(jobDef.getOutputUri()), config);
							if(fs.exists(new Path(jobDef.getOutputUri())))
								fs.delete(new Path(jobDef.getOutputUri()), true);
						}

						// ------------------------
						// Run the transcode job.
						// ------------------------
						Job job = new Job(config);
						job.setJobName("FFmpeg-MR Job: " + jobDef.getJobName());
						
						job.setInputFormatClass(SequenceFileInputFormat.class);
						job.setMapperClass(TranscodeMapper.class);
						
					    job.setMapOutputKeyClass(LongWritable.class);
					    job.setMapOutputValueClass(Chunk.class);
					   
					    job.setPartitionerClass(TranscodePartitioner.class);
						job.setReducerClass(RemuxReducer.class);
						
						// Always use all of the available reduce slots.
						// This pretty much assumes we are benchmarking and not sharing the cluster.
						// Which is bad. And we have to use the old API. 
						job.setNumReduceTasks(status.getMaxReduceTasks());
						
					    job.setOutputFormatClass(SequenceFileOutputFormat.class);
					    job.setOutputKeyClass(LongWritable.class);
					    job.setOutputValueClass(BytesWritable.class);
					    
				        SequenceFileInputFormat.addInputPath(job, demuxData);
				        if(jobDef.getOutputType() == OutputType.RawFile)
				        	SequenceFileOutputFormat.setOutputPath(job, outputData);
				        else if(jobDef.getOutputType() == OutputType.ReducerSegments)
				        	SequenceFileOutputFormat.setOutputPath(job, new Path(jobDef.getOutputUri()));
				        
				        job.setJarByClass(TranscodeJob.class);
				        logger.println("Job submitted to cluster. Mappers will start shortly.");
				        
				        logger.markStartTime(TimedEvent.PROCESS_JOB);
				        	boolean success = job.waitForCompletion(true);
				        logger.markEndTime(TimedEvent.PROCESS_JOB);
				        
				        if(success)
				        	logger.println("Hadoop job completed sucessfully.");
				        else
				        	logger.println("Hadoop job completed with failure");
	
				        if(jobDef.getInputType() == InputType.RawFile || jobDef.getInputType() == InputType.RawFileCopy){
				        	logger.println("Deleting temp demuxed data from HDFS.");
				        	FileSystem.get(config).delete(demuxData, false);
				        }
			        
				        if(jobDef.getOutputType() == OutputType.RawFile && success)
				        {
				        	// Merge the reducer output to a location on the local fs.
							File tempFile = File.createTempFile("temp-output", ".movie");
							Path tempPath = new Path("file://" + tempFile.getAbsolutePath());
							
							logger.markStartTime(TimedEvent.MERGE);
								Merger.merge(config, outputData, tempFile);
							logger.markEndTime(TimedEvent.MERGE);
							
							FileSystem.get(outputData.toUri(), config).delete(outputData, true);
							
							logger.markStartTime(TimedEvent.RAW_COPY_OUT);
								FileUtils.copy(tempPath, new Path(jobDef.getOutputUri()), true, jobDef.isOverwrite(), config);
							logger.markEndTime(TimedEvent.RAW_COPY_OUT);
				        }
					}
					
					if(jobDef.getProcessingType() == ProcessingType.FFmpeg){
						File tempFile = File.createTempFile("temp-ffmpeg", ".movie");
						tempFile.deleteOnExit();
						File tempFileOutput = File.createTempFile("temp-ffmpeg-out", ".movie");
						tempFileOutput.deleteOnExit();
						
						try {
							logger.logClusterDetails(jobFlowConfig.getJobflow(), null, jobDef, null);
							
							// Check the overwrite.
							FileSystem fs = FileSystem.get(new URI(jobDef.getOutputUri()), config);
							if(fs.exists(new Path(jobDef.getOutputUri())) && !jobDef.isOverwrite())
								throw new RuntimeException("Output file already exists - set overwrite:true to override");	
							
							String inputUri = jobDef.getInputUri();
							String tempInputUri = null;
							String tempOutputUri = null;
							String outputUri = jobDef.getOutputUri();
							
							if(jobDef.getInputUri().contains("file://")){
								tempInputUri = inputUri;
							}
							else{
								tempInputUri = "file://" + tempFile.getAbsolutePath();
							}
							
							if(jobDef.getOutputUri().contains("file://")){
								tempOutputUri = outputUri;
							}
							else{
								tempOutputUri = "file://" + tempFileOutput.getAbsolutePath();
							}
							
							if(!inputUri.equals(tempInputUri)){
								logger.markStartTime(TimedEvent.RAW_COPY_IN);
									FileUtils.copy(new Path(inputUri), new Path(tempInputUri), false, true, config);
								logger.markEndTime(TimedEvent.RAW_COPY_IN);
							}
							
							logger.markStartTime(TimedEvent.PROCESS_JOB);
								FFmpegMock.convert(config, new File(tempInputUri.substring(7)), new File(tempOutputUri.substring(7)));
				        	logger.markEndTime(TimedEvent.PROCESS_JOB);
				        	
				        	if(!tempOutputUri.equals(outputUri)){
								logger.markStartTime(TimedEvent.RAW_COPY_OUT);
									FileUtils.copy(new Path(tempOutputUri), new Path(outputUri), true, jobDef.isOverwrite(), config);
								logger.markEndTime(TimedEvent.RAW_COPY_OUT);
				        	}
							
						} catch (Exception e) {
							e.printStackTrace();
							logger.println("FFmpeg job failed.");
							Thread.sleep(5000);
						}
						finally {
							tempFile.delete();
							tempFileOutput.delete();
						}
					}

				}
				catch(Exception exJob){
					logger.logException(config, exJob);
					logger.println("Skipping job due to exception.");
				}
				finally{
					logger.markEndTime(TimedEvent.JOB);
					
				}
			}
		}
		catch(Exception exRun){
			logger.logException(config, exRun);
			logger.println("Fatal error, ending job run.");
			throw exRun;
		}
		finally{
			logger.logEntry("Job run complete.");
			logger.markEndTime(TimedEvent.JOBRUN);
			logger.flush();
			TimeEntry.printJobTimingInfo(jobID);
		}

		return 0;
	}
	
	private static void copyNativeToLibPath()
	{
		Configuration config = getConfig();
		
		URI[] libs = null;
		if(System.getProperty("os.arch").contains("64"))
			libs = nativeLibs64;
		else if(System.getProperty("os.arch").contains("86"))
			libs = nativeLibs;
		else
			throw new RuntimeException("Platform: " + System.getProperty("os.arch") + " not supported.");
		
		
		
		// Copy all of the native libraries.
		for(URI lib : libs)
		{
			String[] libSplit = lib.toString().split("#");
			if(libSplit.length == 2)
			{
				// Get the first lib path (. usually, sometimes specifc though)
				String javaLibPath = System.getProperty("java.library.path");
				if(javaLibPath.indexOf(":") > -1)
					javaLibPath = javaLibPath.substring(0, javaLibPath.indexOf(":"));
				
				String libPath = libSplit[0];
				String libSymlink = javaLibPath + "/" + libSplit[1];
			
				try {
					File tempLocation = File.createTempFile("ffmpegmr-nativelib", ".lib");
					tempLocation.deleteOnExit();
					
					FileSystem fsSrc = FileSystem.get(new URI(libPath), config);
					FileSystem fsDst = FileSystem.get(new URI("file://" + tempLocation.getAbsolutePath()), config);
					FileUtil.copy(fsSrc, new Path(libPath) , fsDst, new Path("file://" + tempLocation.getAbsolutePath()), false, true, config);
					
					// Symlink the so version.
					FileUtil.symLink(tempLocation.getAbsolutePath(), libSymlink);
					new File(libSymlink).deleteOnExit();
					
					// Symlink the unversioned so as well.
					// TODO: make this work on no linux platforms.
					if(!libSymlink.endsWith(".so")){
						String libSymlink2 = libSymlink.substring(0, libSymlink.lastIndexOf(".so") + 3);
						FileUtil.symLink(tempLocation.getAbsolutePath(), libSymlink2);
						new File(libSymlink2).deleteOnExit();
					}
				
				} catch (IOException e) {
					System.err.println("Failed to load native lib: " + lib.toASCIIString());
					e.printStackTrace();
				} catch (URISyntaxException e) {
					System.err.println("Native lib URI incorrect: " + lib.toASCIIString());
					e.printStackTrace();
				}
				
			}
		}
	}
	
	private static Configuration lazyConfig = null;
	public static Configuration getConfig() {
		if(lazyConfig ==  null){
			lazyConfig = new Configuration();
			lazyConfig.set("fs.s3.awsAccessKeyId", "AKIAI45LMHSV622K6EAA");
			lazyConfig.set("fs.s3.awsSecretAccessKey", "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");
			lazyConfig.set("fs.s3n.awsAccessKeyId", "AKIAI45LMHSV622K6EAA");
			lazyConfig.set("fs.s3n.awsSecretAccessKey", "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");
			lazyConfig.set("mapred.compress.map.output", "false");
		}
		return lazyConfig;
	}

}
