package com.tstordyallison.ffmpegmr;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Partitioner;


/**
 *
 * @author tom
 */
public class TranscodeGroupPartitioner extends Partitioner<ChunkID, ChunkData> implements Configurable {

    private Configuration conf;

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        initPartitioner();
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

    private void initPartitioner() {
        // TODO any initialization goes here
    }

    @Override
    public int getPartition(ChunkID key, ChunkData value, int numReduceTasks) {
    	// conf.getString("dts_max")
    	// conf.getString("num_outputs")
        // TODO return a partition in [0, numReduceTasks)
        return key.hashCode() % numReduceTasks;
    }
}
