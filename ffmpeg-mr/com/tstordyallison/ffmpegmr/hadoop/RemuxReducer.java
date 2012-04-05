package com.tstordyallison.ffmpegmr.hadoop;

import java.io.IOException;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.Remuxer;
import com.tstordyallison.ffmpegmr.emr.Logger;

public class RemuxReducer extends Reducer<LongWritable, Chunk, LongWritable, BytesWritable> {

	/**
	 * The reducer takes all of the 'Chunks' with the same timestamp (and because of our demux 
	 * chunking method, the same duration also) and interleaves the different streams of data (audio/video)
	 * into a single chunk for output.
	 * 
	 * Its output will be a valid binary audio/video file, in the desired output container.
	 * 
	 * This is all done in native code through one static method.
	 */
	@Override
	protected void reduce(LongWritable timestamp, Iterable<Chunk> chunks, Context context) throws IOException, InterruptedException {
		Logger.println(context.getConfiguration(), "Reducing ts=" + timestamp);
		context.write(new LongWritable(timestamp.get()), new BytesWritable(Remuxer.muxChunks(chunks)));
	}

}
