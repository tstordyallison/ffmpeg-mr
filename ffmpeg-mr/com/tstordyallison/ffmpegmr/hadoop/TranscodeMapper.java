package com.tstordyallison.ffmpegmr.hadoop;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.hadoop.conf.Configuration;
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

	private float videoResScale = 2;
	private float videoCrf = 0;
	private int videoBitrate = 512000;
	private int audioBitrate = 64000;
	
	@Override
	protected void setup(Context context) throws IOException, InterruptedException {
		super.setup(context);
		
		Configuration config = context.getConfiguration();
		videoResScale = config.getFloat("ffmpeg-mr.videoResScale", videoResScale);
		videoCrf = config.getFloat("ffmpeg-mr.videoCrf", videoCrf);
		videoBitrate = config.getInt("ffmpeg-mr.videoBitrate", videoBitrate);
		audioBitrate = config.getInt("ffmpeg-mr.audioBitrate", audioBitrate);
	}

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
		
    	Transcoder trans = new  Transcoder(key.getTbNum(), key.getTbDen(), key.getOutputChunkPoints(), value.getData(), 
    									   videoResScale, videoCrf, videoBitrate, audioBitrate);
    	byte[] header = trans.getStreamData();
    	List<DemuxPacket> currentPackets = new LinkedList<DemuxPacket>();
    	
    	context.setStatus(String.format("Chunk %d.%d: transcode operation starting... (pkts=%d)", key.getStreamID(), key.getChunkNumber(), value.getPacketCount()));
		context.progress();
    	
		// This is another awful hack:
		// So that we set all the correct chunk point numbers on output (even if the TS is different)
		// we will have a list of expected outputs that we will map to the real ones.
		Queue<Long> expectedChunks = new LinkedList<Long>();
		expectedChunks.add(key.getChunkNumber()); // The first item is the chunk with the key.chunkNumber.
		for(Long chunkPoint : key.getOutputChunkPoints())
			expectedChunks.add(ChunkID.toMs(chunkPoint, key.getTbNum(), key.getTbDen()));
		
    	// Pull the data through to us whilst building output chunks.
    	DemuxPacket pkt = null;
    	int pkt_counter = 0;
    	while((pkt = trans.getNextPacket()) != null)
    	{
    		pkt_counter += 1;
    		if(pkt_counter % 500 == 0)
    		{
    			context.setStatus(String.format("Chunk %d.%d: %3.1f%% transcoding complete...", key.getStreamID(), key.getChunkNumber(), ((double)pkt_counter / value.getPacketCount()) * 100));
    			context.progress();
    		}
    		if(pkt_counter % 100 == 0)
    			context.getCounter(ProgressCounter.COMBINED_PROGRESS).increment(100);
    		
    		if(pkt.splitPoint)
    		{
    			// Empty the current buffer before adding this new packet.
    			emptyPacketBuffer(header, key, expectedChunks, currentPackets, context);
    		}
    		
    		// Add the new packet
    		currentPackets.add(pkt);
    	}
    	
    	// Empty anything left in the buffer.
    	emptyPacketBuffer(header, key, expectedChunks, currentPackets, context);
    	
    	context.getCounter(ProgressCounter.COMBINED_PROGRESS).increment(pkt_counter % 100);
    	
    	trans.close();
    	stopwatch.stop();
    	
    	context.setStatus(String.format("Chunk %d.%d: Transcoding complete (time taken: %d ms.)", key.getStreamID(), key.getChunkNumber(),  stopwatch.getElapsedTime()));
		context.progress();
 
		if(expectedChunks.size() > 0)
			Printer.println("WARNING: Mapper did not output the expected number of chunks. This will likely lead to an mux/merge error.");
		
    	Printer.println("Mapper complete, time taken: " + stopwatch.getElapsedTime() + " ms. ");
    }
	
	private void emptyPacketBuffer(byte[] header, ChunkID key, Queue<Long> expectedChunks, List<DemuxPacket> currentPackets, Context context)
	{
		if(currentPackets.size() > 0){
			
			// TODO: Spin this output stage off into a new thread to keep the processor busy?
			
			// Keeps the mapper alive.
			if(context != null)
				context.progress();
			
			// Build ChunkID
			ChunkID chunkID = new ChunkID();
			chunkID.setChunkNumber(expectedChunks.remove()); // This will throw an exception if we cock up :)
			chunkID.setStartTS(currentPackets.get(0).ts);
			chunkID.setEndTS(currentPackets.get(currentPackets.size()-1).ts + currentPackets.get(currentPackets.size()-1).duration);
			chunkID.setTbNum(currentPackets.get(0).tb_num);
			chunkID.setTbDen(currentPackets.get(0).tb_den);
			
			chunkID.setStreamID(key.getStreamID());
			chunkID.setStreamDuration(key.getStreamDuration());
			
			if(chunkID.getMillisecondsStartTs() != chunkID.getChunkNumber())
				Printer.println("WARNING: Output startTS != desired chunk point (to the nearest ms).");
			
			// Build value
			ChunkData chunkData = new ChunkData(header, currentPackets);
			
			// Output value
			try {
				Chunk chunk = new Chunk(chunkID, chunkData);
				context.write(new LongWritable(chunkID.getChunkNumber()), chunk);
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
