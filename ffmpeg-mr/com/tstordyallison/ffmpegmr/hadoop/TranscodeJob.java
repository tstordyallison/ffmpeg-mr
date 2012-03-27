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
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.Chunker.ChunkerReport;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.OSValidator;
import com.tstordyallison.ffmpegmr.util.Printer;

public class TranscodeJob extends Configured implements Tool {

	public static enum ProgressCounter { AUDIO_PROGRESS, VIDEO_PROGRESS, COMBINED_PROGRESS} 
	
	private static URI[] nativeLibs = null;
	private static URI[] nativeLibs64 = null;
	
	static{
		Printer.ENABLED = true;
		Printer.USE_ERR = true;
		WriterThread.BLOCK_SIZE *= 1; // 16Mb increments.
		WriterThread.PRINT_WRITE = true;
		
		try {
			nativeLibs = new URI[] {
				new URI("s3n://ffmpeg-mr/lib/libffmpeg-mr.so#libffmpeg-mr.so"),
			    new URI("s3n://ffmpeg-mr/lib/libavcodec.so#libavcodec.so.54"),
			    new URI("s3n://ffmpeg-mr/lib/libavformat.so#libavformat.so.54"),
			    new URI("s3n://ffmpeg-mr/lib/libavutil.so#libavutil.so.51"),
			    new URI("s3n://ffmpeg-mr/lib/libfaac.so#libfaac.so.0"),
			    new URI("s3n://ffmpeg-mr/lib/libx264.so#libx264.so.120"),
			    new URI("s3n://ffmpeg-mr/lib/libmp3lame.so#libmp3lame.so.0")
			};
			nativeLibs64 = new URI[] {
					new URI("s3n://ffmpeg-mr/lib64/libffmpeg-mr.so#libffmpeg-mr.so"),
				    new URI("s3n://ffmpeg-mr/lib64/libavcodec.so#libavcodec.so.54"),
				    new URI("s3n://ffmpeg-mr/lib64/libavformat.so#libavformat.so.54"),
				    new URI("s3n://ffmpeg-mr/lib64/libavutil.so#libavutil.so.51"),
				    new URI("s3n://ffmpeg-mr/lib64/libfaac.so#libfaac.so.0"),
				    new URI("s3n://ffmpeg-mr/lib64/libx264.so#libx264.so.120"),
				    new URI("s3n://ffmpeg-mr/lib64/libmp3lame.so#libmp3lame.so.0")
				};
		} catch (URISyntaxException e) {
		}
		
		if(!OSValidator.isMac())
			copyNativeToLibPath();
	}
	
	@Override
	public int run(String[] args) throws Exception {
		
		String jobID = UUID.randomUUID().toString();
		Configuration config = getConfig();
		   
		// Copy native binaries to the distributed cache if we are not testing.
		if(!OSValidator.isMac())
		{	
	        DistributedCache.createSymlink(config);
			for(URI lib : nativeLibs64)
				DistributedCache.addCacheFile(lib, config);
		}
		
		// --------------------------
		// Demux the file into the local HDFS if needed.
		// --------------------------
		Path demuxData = null;
		Path movieFile = new Path(args[0]);
		
		if(!movieFile.getName().endsWith(".seq")){
			demuxData = new Path("/tmp/demux-temp-" + jobID); 
			
			if(FileSystem.get(movieFile.toUri(), config).getFileStatus(movieFile).getLen() > FileUtils.GIBIBYTE*2){
				Printer.println("WARNING: This file is over 2GB and will likely take a very long time to Demux via S3.");
				Printer.println("WARNING: Please upload this file as a pre-demuxed SequenceFile to improve performance.");
			}

			ChunkerReport report = Chunker.chunkInputFile(config, movieFile.toUri().toString(), demuxData.toUri().toString());
	        Printer.println("Total number of frames to process: " + report.getPacketCount());  
		}
		else{
			Printer.println("Using a pre-demuxed SequenceFile.");
			demuxData = movieFile;
		}	
		
		// --------------------------
		// Run the transcode job.
		// --------------------------

		Job job = new Job(config);
		job.setJobName("FFmpeg-MR Job: " + jobID);
		
		job.setInputFormatClass(SequenceFileInputFormat.class);
		job.setMapperClass(TranscodeMapper.class);
		
	    job.setMapOutputKeyClass(LongWritable.class);
	    job.setMapOutputValueClass(Chunk.class);
	   
	    job.setPartitionerClass(TranscodePartitioner.class);
	    
		job.setReducerClass(RemuxReducer.class);
		
	    job.setOutputFormatClass(SequenceFileOutputFormat.class);
	    job.setOutputKeyClass(LongWritable.class);
	    job.setOutputValueClass(BytesWritable.class);
	    
        SequenceFileInputFormat.addInputPath(job, demuxData);
        SequenceFileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        job.setJarByClass(TranscodeJob.class);
        job.waitForCompletion(true);
        
        // Remove the temp HDFS data if needed.
        if(demuxData != movieFile)
        	FileSystem.get(config).delete(demuxData, false);
        
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
		
		// Copy all of the native libraries to the cwd.
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
					Printer.println("Failed to load native lib: " + lib.toASCIIString());
					e.printStackTrace();
				} catch (URISyntaxException e) {
					Printer.println("Native lib URI incorrect: " + lib.toASCIIString());
					e.printStackTrace();
				}
				
			}
		}
	}
	
	public static void main(String[] args) throws Exception {	
		Configuration config = getConfig();
		
		// Run the job.
        int res = ToolRunner.run(config, new TranscodeJob(), args);
        System.exit(res);
	}
		
	public static Configuration getConfig() {
		Configuration config = new Configuration();
		config.set("fs.s3.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		config.set("fs.s3n.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3n.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		config.set("mapred.compress.map.output", "fasle");
		return config;
	}
}
