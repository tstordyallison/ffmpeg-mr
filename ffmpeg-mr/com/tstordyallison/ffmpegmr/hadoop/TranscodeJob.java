package com.tstordyallison.ffmpegmr.hadoop;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;
import com.tstordyallison.ffmpegmr.Chunker;
import com.tstordyallison.ffmpegmr.Chunker.ChunkerReport;
import com.tstordyallison.ffmpegmr.Merger;
import com.tstordyallison.ffmpegmr.WriterThread;
import com.tstordyallison.ffmpegmr.util.OSValidator;
import com.tstordyallison.ffmpegmr.util.Printer;

public class TranscodeJob {

	static{
		Printer.ENABLED = true;
		Printer.USE_ERR = true;
		WriterThread.BLOCK_SIZE *= 1; // 16Mb increments.
		WriterThread.PRINT_WRITE = true;
	}
	
	public static enum ProgressCounter { AUDIO_PROGRESS, VIDEO_PROGRESS, COMBINED_PROGRESS} 
	
	private static URI[] nativeLibs = null;
	private static URI[] nativeLibs64 = null;
	static { 
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
	}
	
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException, URISyntaxException, InstantiationException, IllegalAccessException {		
		String jobID = UUID.randomUUID().toString();
		
		JobConf config = new JobConf();
		config.set("fs.s3.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		config.set("fs.s3n.awsAccessKeyId", "01MDAYB509VJ53B2EK02");
		config.set("fs.s3n.awsSecretAccessKey", "zwajpazry7Me7tnbYaw3ldoj5mbRDMFMHqYHgDmv");
		config.setCompressMapOutput(false);
		
		// Setup the libary path if not testing.
		if(!OSValidator.isMac())
			copyNativeToLibPath(config);
		
		// For testing.
		if(args.length == 0)
		{
			args = new String[2];
			args[0] = "file:///Users/tom/Code/fyp/example-videos/Test.avi";
			args[1] = "file:///Users/tom/Code/fyp/example-videos/Test.avi.mkv.output" + jobID + "/";
		}
	        
		// --------------------------
		// Demux the file into the local HDFS.
		// --------------------------
		Path movieFile = new Path(args[0]);
		Path demuxData = new Path("/tmp/demux-temp-" + jobID); 
		Path outputData = new Path("/tmp/reduced-output-temp-" + jobID); 
		
		// Demux the file.
		ChunkerReport report = Chunker.chunkInputFile(config, movieFile.toUri().toString(), demuxData.toUri().toString());
        Printer.println("Total number of frames to process: " + report.getPacketCount());  
        config.setLong("tsa.tstordyallison.ffmpegmr.packetcount", report.getPacketCount());
        config.setLong("tsa.tstordyallison.ffmpegmr.duration", report.getEndTS());
		
		// --------------------------
		// Run the transcode job.
		// --------------------------
		
		// Copy native binaries to the distributed cache if we are not testing.
		if(!OSValidator.isMac())
		{	
	        DistributedCache.createSymlink(config);
	        //if(System.getProperty("os.arch").contains("64"))
				for(URI lib : nativeLibs64)
					DistributedCache.addCacheFile(lib, config);
	        //else if(System.getProperty("os.arch").contains("86"))
		        //for(URI lib : nativeLibs)
					//DistributedCache.addCacheFile(lib, config);
		}

		Job job = new Job(config);
		job.setJobName("FFmpeg-MR Job: " + jobID);
		
		job.setInputFormatClass(SequenceFileInputFormat.class);
		job.setMapperClass(TranscodeMapper.class);
		
	    job.setMapOutputKeyClass(LongWritable.class);
	    job.setMapOutputValueClass(Chunk.class);
	   
		job.setReducerClass(RemuxReducer.class);
		
	    job.setOutputFormatClass(SequenceFileOutputFormat.class);
	    job.setOutputKeyClass(LongWritable.class);
	    job.setOutputValueClass(BytesWritable.class);
	    
        SequenceFileInputFormat.addInputPath(job, demuxData);
        //SequenceFileOutputFormat.setOutputPath(job, outputData);
        SequenceFileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        job.setJarByClass(TranscodeJob.class);
       
        job.waitForCompletion(true);
        
        FileSystem.get(config).delete(demuxData, false);
        
        // -----------------------------------------------------------------
        // Merge the output in HDFS back into a file, and place it in S3.
        // -----------------------------------------------------------------

        //Merger.merge(config, outputData, new Path(args[1]));
        //FileSystem.get(config).delete(outputData, false);
	}
	
	private static void copyNativeToLibPath(JobConf config) throws IOException, URISyntaxException
	{
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
				
			}
		}
	}
	
}
