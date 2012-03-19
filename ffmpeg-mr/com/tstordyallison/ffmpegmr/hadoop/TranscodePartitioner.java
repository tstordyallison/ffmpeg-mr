package com.tstordyallison.ffmpegmr.hadoop;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Partitioner;

import com.tstordyallison.ffmpegmr.Chunk;

public class TranscodePartitioner extends Partitioner<LongWritable, Chunk> {

	@Override
	public int getPartition(LongWritable ts, Chunk chunk, int numPartitions) {
		long partitionSize = chunk.getChunkID().streamDuration/numPartitions;
		return (int)(ts.get() / partitionSize);
	}

}
