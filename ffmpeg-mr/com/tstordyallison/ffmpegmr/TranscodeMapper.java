
/*
 * TranscodeMapper.java
 *
 * Created on 21-Dec-2011, 16:30:22
 */

package com.tstordyallison.ffmpegmr;


import java.io.IOException;
import org.apache.hadoop.mapreduce.Mapper;

import com.tstordyallison.ffmpegmr.Chunker;
/**
 *
 * @author tom
 */
public class TranscodeMapper extends Mapper<Chunker.ChunkID,Chunker.ChunkData,Chunker.ChunkID,Chunker.ChunkData> {
	
	@Override
    protected void map(Chunker.ChunkID key, Chunker.ChunkData value, Context context) throws IOException, InterruptedException {
    	
    }
}
