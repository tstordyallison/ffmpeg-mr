package com.tstordyallison.ffmpegmr.hadoop;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;

public class RemuxReducer extends Reducer<LongWritable, Chunk, ChunkID, ChunkData> {

	/**
	 * The reducer takes all of the 'Chunks' with the same timestamp (and because of our demux 
	 * chunking method, the same duration also) and interleaves the different streams of data (audio/video)
	 * into a single chunk for output.
	 * 
	 * Its output will be a valid binary audio/video file, in the desired output container.
	 */
	@Override
	protected void reduce(LongWritable timestamp, Iterable<Chunk> values, Context context) throws IOException, InterruptedException {

	}

}
