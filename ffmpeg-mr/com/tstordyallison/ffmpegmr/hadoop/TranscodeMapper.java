package com.tstordyallison.ffmpegmr.hadoop;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;

import com.tstordyallison.ffmpegmr.Chunk;
import com.tstordyallison.ffmpegmr.ChunkData;
import com.tstordyallison.ffmpegmr.ChunkID;
import com.tstordyallison.ffmpegmr.DemuxPacket;
import com.tstordyallison.ffmpegmr.Transcoder;
import com.tstordyallison.ffmpegmr.hadoop.TranscodeJob.ProgressCounter;
import com.tstordyallison.ffmpegmr.util.Printer;
import com.tstordyallison.ffmpegmr.util.Stopwatch;

public class TranscodeMapper extends Mapper<ChunkID,ChunkData,LongWritable,Chunk> {

	/**
	 * The mapper takes a given chunk of audio/video data, and passes it to the transcoder object. 
	 * 
	 * The transcoder then converts the chunk of audio/video into the desired format, and passes back raw DemuxPackets.
	 * 
	 * The mapper splits the chunks that it output on the chunk points that were defined in the demux phase, ensuring
	 * that the duration of any group of chunks from different streams with the same starting timestamp is the same. This
	 * allows us to use multiple reducers in our final output in an elegant fashion.
	 */
	@Override
	protected void map(ChunkID key, ChunkData value, Context context) throws IOException, InterruptedException {
		
		Printer.println("Running mapper for "  + new Chunk(key, value).toString());
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();
		
    	Transcoder trans = new  Transcoder(key.tbNum, key.tbDen, key.outputChunkPoints, value.getData());
    	List<DemuxPacket> currentPackets = new LinkedList<DemuxPacket>();
    	
    	context.setStatus(String.format("Chunk %d.%d: transcode operation starting... (pkts=%d)", key.streamID, key.chunkNumber, value.packet_count ));
		context.progress();
    	
    	// Pull the data through to us whilst building output chunks.
    	DemuxPacket pkt = null;
    	int pkt_counter = 0;
    	while((pkt = trans.getNextPacket()) != null)
    	{
    		pkt_counter += 1;
    		if(pkt_counter % 500 == 0)
    		{
    			context.setStatus(String.format("Chunk %d.%d: %3.1f%% transcoding complete...", key.streamID, key.chunkNumber, ((double)pkt_counter / value.packet_count) * 100));
    			context.progress();
    		}
    		if(pkt_counter % 100 == 0)
    			context.getCounter(ProgressCounter.COMBINED_PROGRESS).increment(100);
    		
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
    	
    	context.getCounter(ProgressCounter.COMBINED_PROGRESS).increment(pkt_counter % 100);
    	
    	trans.close();
    	stopwatch.stop();
    	
    	context.setStatus(String.format("Chunk %d.%d: Transcoding complete (time taken: %d ms.)", key.streamID, key.chunkNumber,  stopwatch.getElapsedTime()));
		context.progress();
 
    	Printer.println("Mapper complete, time taken: " + stopwatch.getElapsedTime() + " ms. ");
    }
	
	private void emptyPacketBuffer(List<DemuxPacket> currentPackets, Context context)
	{
		if(currentPackets.size() > 0){
			
			// TODO: Spin this output stage off into a new thread as soon as possible?
			
			// Keeps the mapper alive.
			if(context != null)
				context.progress();
			
			// Build ChunkID
			ChunkID chunkID = new ChunkID();
			chunkID.chunkNumber = -1;
			chunkID.streamID = currentPackets.get(0).streamID;
			chunkID.startTS = currentPackets.get(0).ts;
			chunkID.endTS = currentPackets.get(currentPackets.size()-1).ts + currentPackets.get(currentPackets.size()-1).duration;
			chunkID.streamID = currentPackets.get(0).streamID;
			chunkID.tbNum = currentPackets.get(0).tb_num;
			chunkID.tbDen = currentPackets.get(0).tb_den;
			
			// Build value
			ChunkData chunkData = new ChunkData(currentPackets);
			
			// Output value
			try {
				Chunk chunk = new Chunk(chunkID, chunkData);
				context.write(new LongWritable(chunkID.startTS), chunk);
				Printer.println("Map output: " + chunk.toString());
			} catch (IOException e) {
				System.err.println("IO Error writing to map output.");
				e.printStackTrace();
			} catch (InterruptedException e) {
				System.err.println("Map output thread cancelled.");
				e.printStackTrace();
			}
			
			// Clear this buffer.
			currentPackets.clear();
		
		}
	}
}
