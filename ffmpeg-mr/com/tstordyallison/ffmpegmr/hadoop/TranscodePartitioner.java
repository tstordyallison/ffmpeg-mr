package com.tstordyallison.ffmpegmr.hadoop;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Partitioner;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.util.Printer;

public class TranscodePartitioner extends Partitioner<LongWritable, Chunk> {

	@Override
	public int getPartition(LongWritable ts, Chunk chunk, int numPartitions) {
		long partitionSize = chunk.getChunkID().streamDuration/numPartitions;
		int partition = (int) Math.round(((double)ts.get() / partitionSize));
		Printer.println("Chunk with TS: " + ts.get() + " allocated reducer " + partition + "/" + numPartitions);
		return partition;
	}
}
