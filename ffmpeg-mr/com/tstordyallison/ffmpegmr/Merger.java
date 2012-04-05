package com.tstordyallison.ffmpegmr;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;

import com.tstordyallison.ffmpegmr.emr.Logger;
import com.tstordyallison.ffmpegmr.util.FileUtils;
import com.tstordyallison.ffmpegmr.util.NativeUtil;

/**
 * This object uses FFmpeg to take several segments of output and merge them into one final file.
 * 
 * It is used after the Reducer to place the output back onto S3.
 * 
 * @author tom
 *
 */

public class Merger {

	static{
		NativeUtil.loadFFmpegMR();
	}
	
	private File output;
	private Path outputUri;
	private FSDataOutputStream outputStream;
	private boolean closed = false;
	
	private Merger(Configuration config, Path outputUri) throws IOException
	{
		this.outputUri = outputUri;	
		FileSystem fs = FileSystem.get(outputUri.toUri(), config);
		outputStream = fs.create(outputUri, true);
		initWithOutputStream(outputStream);
	}
	private Merger(File output) throws IOException
	{
		this.output = output;	
		initWithFile(output.getAbsolutePath());
	}
	
	private native void initWithFile(String filePath);
	private native void initWithOutputStream(FSDataOutputStream output);
	private native void addSegment(byte[] segment, long off, long len);
	private native void addSegment(String filePath); 
	private native void closeOutput();
	
	private void close() throws IOException
	{
		this.closeOutput();
		if(outputStream != null)
			this.outputStream.close();
	}
	private boolean isClosed(){return closed;};
	
	public static void merge(Configuration config, String inputUri, String outputUri) throws IOException{
		if(outputUri.startsWith("file://"))
			merge(config, new Path(inputUri), new File(outputUri.substring(7)));
		else
			merge(config, new Path(inputUri), new Path(outputUri));
	}
	
	public static void merge(Configuration config, File folder, Path outputUri) throws IOException{
		Merger merger = new Merger(config, outputUri);
		mergeFromFolder(merger, folder);
		merger.close();
	}
	
	public static void merge(Configuration config, Path inputUri, Path outputUri) throws IOException{
		Merger merger = new Merger(config, outputUri);
		mergeFromStream(merger, config, inputUri);
		merger.close();
	}
	
	public static void merge(Configuration config, File folder, File file) throws IOException{
		Merger merger = new Merger(file);
		mergeFromFolder(merger, folder);
		merger.close();
	}
	
	public static void merge(Configuration config, Path inputUri, File file) throws IOException{
		Merger merger = new Merger(file);
		mergeFromStream(merger, config, inputUri);
		merger.close();
	}
	
	public static void mergeFromStream(Merger merger, Configuration config, Path inputUri) throws IOException{
		Logger logger = new Logger(config);
		
		if(!merger.isClosed()){
			
			// This causes the actual merge operation to happen.
			logger.println("Merging " + inputUri + "...");
		
			FileSystem fs = FileSystem.get(inputUri.toUri(), config);
			
			for(FileStatus item : fs.listStatus(inputUri))
			{
				if(item.getPath().toUri().toString().contains("part-")){
					SequenceFile.Reader reader = new SequenceFile.Reader(fs, item.getPath(), config);
	
					LongWritable key = null;
					BytesWritable value = null;
					try {
						key = (LongWritable)reader.getKeyClass().newInstance();
						value = (BytesWritable)reader.getValueClass().newInstance();
					} catch (InstantiationException e) {
						e.printStackTrace();
						throw new RuntimeException("Reducer file format invalid.");
					} catch (IllegalAccessException e) {
						e.printStackTrace();
						throw new RuntimeException("Reducer file format invalid.");
					} 
					 
					while (reader.next(key, value)){
						merger.addSegment(value.getBytes(), 0, value.getLength()-1);
						logger.println("Reduce output merged: ts=" + key.get() + ", size=" + FileUtils.humanReadableByteCount(value.getLength(), false));
					}
					reader.close();
				}	
			}
			
			logger.println("Sucessfully merged " + inputUri + ".");
			logger.flush();
		}
		else
		{
			throw new RuntimeException("This merger is closed and further merge operations cannot be performed.");
		}
	}
	
	private static void mergeFromFolder(Merger merger, File folder) throws IOException
	{
		if (!merger.isClosed()) {

			// This causes the actual merge operation to happen.
			System.out.println("Merging " + folder.getAbsolutePath() + "...");

			if (folder.isDirectory()) {
				List<String> files = Arrays.asList(folder.list());
				Collections.sort(files, new Comparator<String>() {

					@Override
					public int compare(String o1, String o2) {
						if (o1.lastIndexOf(".") >= 0
								&& o2.lastIndexOf(".") >= 0) {
							try {
								long o1Num = Long.parseLong(o1.substring(o1
										.lastIndexOf(".") + 1));
								long o2Num = Long.parseLong(o2.substring(o2
										.lastIndexOf(".") + 1));
								return new Long(o1Num)
										.compareTo(new Long(o2Num));
							} catch (Exception e) {
								e.printStackTrace();
								return o1.compareTo(o2);
							}
						} else
							return o1.compareTo(o2);
					}

				});
				System.out.println(files.toString());
				for (String file : files) {
					File segment = new File(folder.getAbsolutePath() + "/"
							+ file);
					if (segment.isFile() && !file.startsWith(".")) {
						String segPath = folder.getAbsolutePath() + "/" + file;
						System.out.println(segPath);
						merger.addSegment(segPath);
						System.out.println("Reduce output merged: s="
								+ segment.getName()
								+ ", size="
								+ FileUtils.humanReadableByteCount(
										segment.length(), false));
					}
				}
			}
			System.out.println("Sucessfully merged " + folder.getAbsolutePath()
					+ ".");
		} else {
			throw new RuntimeException(
					"This merger is closed and further merge operations cannot be performed.");
		}
	}
}
