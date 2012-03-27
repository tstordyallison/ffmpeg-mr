package com.tstordyallison.ffmpegmr.hadoop;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Partitioner;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.util.Printer;

public class TranscodePartitioner extends Partitioner<LongWritable, Chunk> {

	@Override
	public int getPartition(LongWritable ts, Chunk chunk, int numPartitions) {
		int partition = getPartitionImpl(ts.get(), chunk.getChunkID().getStreamDuration(), numPartitions);
		Printer.println("Chunk with TS: " + ts.get()+1 + " allocated reducer " + partition + "/" + numPartitions);
		return partition;
	}
	
	public static int getPartitionImpl(long chunkTs, long streamDuration, int numPartitions){
		long partitionSize = streamDuration/numPartitions;
		int partition = (int) Math.floor(((double)chunkTs / partitionSize));
		return partition;
	}
	
	private static void testPartition(long ts, long streamDuration, int numPartitions){
		System.out.println("TS: " + ts + " = " + getPartitionImpl(ts, streamDuration, numPartitions));
	}
	
	public static void main(String[] args){
		testPartition(1872015, 2469790, 14);
		testPartition(2430797, 2469790, 14);
	}
	
}
