
/*
 * TranscodeMapper.java
 *
 * Created on 21-Dec-2011, 16:30:22
 */

package com.tstordyallison.ffmpegmr;


import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.mapreduce.Mapper;

import com.tstordyallison.ffmpegmr.util.Printer;

/**
 *
 * @author tom
 */
public class TranscodeMapper extends Mapper<ChunkID,ChunkData,ChunkID,ChunkData> {
	
	@Override
	protected void map(ChunkID key, ChunkData value, Context context) throws IOException, InterruptedException {
		
		Printer.println("Running mapper for "  + key.toString());
		
    	Transcoder trans = new  Transcoder(key.outputChunkPoints, value.getData());
    	List<DemuxPacket> currentPackets = new LinkedList<DemuxPacket>();
    	
    	// Pull the data through to us whilst building output chunks.
    	DemuxPacket pkt = null;
    	while((pkt = trans.getNextPacket()) != null)
    	{
    		if(pkt.splitPoint)
    		{
    			// Empty the current buffer before adding this new packet.
    			emptyPacketBuffer(currentPackets, context);
    		}
    		
    		// Add the new packet
    		currentPackets.add(pkt);
    	}
    	
    	// Empty anything left in the buffer.
    	emptyPacketBuffer(currentPackets, context);
    	
    	trans.close();
    }
	
	private void emptyPacketBuffer(List<DemuxPacket> currentPackets, Context context)
	{
		if(currentPackets.size() > 0){
			
			// TODO: Spin this output stage off into a new thread as soon as possible.
			
			// Keeps the mapper alive.
			if(context != null)
				context.progress();
			
			// Build ChunkID
			ChunkID chunkID = new ChunkID();
			chunkID.chunkNumber = 1;
			chunkID.startTS = currentPackets.get(0).ts;
			chunkID.endTS = currentPackets.get(currentPackets.size()-1).ts + currentPackets.get(currentPackets.size()-1).duration;
			chunkID.streamID = currentPackets.get(0).streamID;
			
			// Build value
			ChunkData chunkData = new ChunkData(currentPackets);
			
			// Output value
			try {
				context.write(chunkID, chunkData);
			} catch (IOException e) {
				System.err.println("IO Error writing to map output.");
				e.printStackTrace();
			} catch (InterruptedException e) {
				System.err.println("Map output thread cancelled.");
				e.printStackTrace();
			}
			
			chunkData.dealloc(chunkID);
		
		}
	}
}
