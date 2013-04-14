#include <iostream>
#include "com_tstordyallison_ffmpegmr_Remuxer.h"
#include "SharedUtil.h"

#define DEBUG_PRINT 1
#define DEBUG_PRINT_CRAZY 0

#define INT32_MAX 2147483647

extern "C" {
    #include "ffmpeg_tpl.h"
    #include "libavcodec/avcodec.h"
    #include "libavformat/avformat.h"
#ifndef AWSBUILD
    #include "libavutil/timestamp.h"
#endif
}

static int read_avpacket(TPLImageRef *image_list, size_t image_list_size, size_t *image_cursor, AVPacket *pkt)
{
    // Get the next packet from the data stream.
    int err = 0;
    if(*image_cursor != image_list_size)
    {
        TPLImageRef packet = image_list[*image_cursor];
        err = read_avpacket_chunk_from_memory(packet.data, packet.size, pkt);
        
        if(err == 0){
#ifndef AWSBUILD
            if(DEBUG_PRINT_CRAZY)
                fprintf(stderr, "Demuxed packet: pts:%s, dts:%s, key=%c\n", av_ts2str(pkt->pts), av_ts2str(pkt->dts), 
                        pkt->flags & AV_PKT_FLAG_KEY ? 'Y' : 'N');
#endif
            *image_cursor += 1;
            return (int)pkt->size;
        }
        else
            return err;
    }
    else
        return 0;
    
}



/*
 * Take a set of chunks (one for each stream) and muxes them back into an MKV container. 
 *
 * Class:     com_tstordyallison_ffmpegmr_Remuxer
 * Method:    muxChunks
 * Signature: (Ljava/lang/Iterable;)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_tstordyallison_ffmpegmr_Remuxer_muxChunks(JNIEnv *env, jclass clazz, jobject chunks)
{
    // Get the iterator for the Interable object.
    jclass      iterable_clazz    = env->GetObjectClass(chunks);
    jmethodID   getIterator       = env->GetMethodID(iterable_clazz, "iterator", "()Ljava/util/Iterator;");
    jobject     iterator          = env->CallObjectMethod(chunks, getIterator);
    
    jclass      iterator_clazz = env->GetObjectClass(iterator);
    jmethodID   hasNext        = env->GetMethodID(iterator_clazz, "hasNext", "()Z");
    jmethodID   next        = env->GetMethodID(iterator_clazz, "next", "()Ljava/lang/Object;");
    
    
    uint8_t **data_chunks = NULL; // Array of byte arrays containing each streams data. 
    size_t *data_chunks_size  = NULL;
    int *data_chunks_streamid  = NULL;
    int nb_chunks = 0;
    
    while(env->CallBooleanMethod(iterator, hasNext))
    {
        // Increment and alloc.
        nb_chunks += 1;
        data_chunks = (uint8_t **)realloc(data_chunks, sizeof(uint8_t *) * nb_chunks);
        data_chunks_size = (size_t *)realloc(data_chunks_size, sizeof(size_t *) * nb_chunks);
        data_chunks_streamid = (int *)realloc(data_chunks_streamid, sizeof(int *) * nb_chunks);
        
        // Get the chunk and copy over the data.
        jclass  chunk_clazz = env->FindClass("com/tstordyallison/ffmpegmr/Chunk");
        jobject chunk       = env->CallObjectMethod(iterator, next);
        jmethodID getChunkID   = env->GetMethodID(chunk_clazz, "getChunkID", "()Lcom/tstordyallison/ffmpegmr/ChunkID;");
        jmethodID getChunkData   = env->GetMethodID(chunk_clazz, "getChunkData", "()Lcom/tstordyallison/ffmpegmr/ChunkData;");
        
        if(!getChunkData || !getChunkID)
        {
            throw_new_exception(env, "getChunkData/ID method missing");
            return NULL;
        }
        
        jclass chunk_data_clazz = env->FindClass("com/tstordyallison/ffmpegmr/ChunkData");
        jobject chunk_data      = env->CallObjectMethod(chunk, getChunkData);
        
        if(!chunk_data)
        {
            throw_new_exception(env, "getChunkData() returned null.");
            return NULL;
        }

        jmethodID  getData   = env->GetMethodID(chunk_data_clazz, "getData", "()[B");
        jbyteArray dataArray = (jbyteArray)env->CallObjectMethod(chunk_data, getData);
        
        if(!dataArray)
        {
            throw_new_exception(env, "ChunkData.getData() returned null.");
            return NULL;
        } 
        
        // Copy the chunk over into memory from the JVM.
        int data_size = env->GetArrayLength(dataArray);
        uint8_t *data = (uint8_t *)malloc(sizeof(jbyte) * data_size);
        env->GetByteArrayRegion(dataArray, 0, (jint)data_size, (jbyte *)data);
        env->DeleteLocalRef(dataArray);
        
        // Get ChunkID
        jclass chunk_id_clazz = env->FindClass("com/tstordyallison/ffmpegmr/ChunkID");
        jobject chunk_id      = env->CallObjectMethod(chunk, getChunkID);
        jfieldID streamID     = env->GetFieldID(chunk_id_clazz, "streamID", "I");
        
        // Save the data pointers.
        data_chunks[nb_chunks-1] = data;
        data_chunks_size[nb_chunks-1] = data_size;
        data_chunks_streamid[nb_chunks-1] = (int)env->GetIntField(chunk_id, streamID);
    }
    
#ifdef DEBUG 
        fprintf(stderr, "Loaded %d chunks for remuxer.\n", nb_chunks);
#endif
    // ------------------------------------------------------------------------------------------
    
    // This is a really awful bubble sort of the data.
    {
        int i, j;
        int temp_id;
        uint8_t *temp_data;
        size_t temp_size;
        
        for (i = (nb_chunks - 1); i > 0; i--)
        {
            for (j = 1; j <= i; j++)
            {
                if (data_chunks_streamid[j-1] > data_chunks_streamid[j])
                {
                    // Swap the streamids
                    temp_id = data_chunks_streamid[j-1];
                    data_chunks_streamid[j-1] = data_chunks_streamid[j];
                    data_chunks_streamid[j] = temp_id;
                    
                    // Swap the data sizes
                    temp_size = data_chunks_size[j-1];
                    data_chunks_size[j-1] = data_chunks_size[j];
                    data_chunks_size[j] = temp_size;
                    
                    // Swap the data pointers
                    temp_data = data_chunks[j-1];
                    data_chunks[j-1] = data_chunks[j];
                    data_chunks[j] = temp_data;
                }
            }
        }
    }
    
    
    // ------------------------------------------------------------------------------------------
    
    TPLImageRef **image_lists = (TPLImageRef **)malloc(sizeof(TPLImageRef *) * nb_chunks);
    size_t *image_sizes = (size_t *)malloc(sizeof(size_t) * nb_chunks);
    size_t *image_cursors = (size_t *)malloc(sizeof(size_t) * nb_chunks);
    AVRational *stream_input_tbs = (AVRational *)malloc(sizeof(AVRational) * nb_chunks);
    
    // For each of the streams, use tpl_gather to get a list of the images they contain.
    for(int i = 0; i < nb_chunks; i++){
        tpl_gather_image_list(data_chunks[i], data_chunks_size[i], &(image_lists[i]), &(image_sizes[i]));
        image_cursors[i] = 0;
    }
    
    // ------------------------------------------------------------------------------------------
    
    AVIOContext *output_io_context = NULL; avio_open_dyn_buf(&output_io_context);
    AVOutputFormat *output_format = av_guess_format("matroska", "filename.mkv", NULL);
    AVFormatContext *output_format_context = NULL;
    
    // Create an output file AVFormatContext and open the file for writing to the filesystem (dynamic buffer).
    avformat_alloc_output_context2(&output_format_context, output_format, NULL, NULL);
    if (!&output_format_context) {
        throw_new_exception(env, "Unable to create output context.");
        return NULL;
    }
    
    // Set the IO context for the output.
    output_format_context->pb = output_io_context;
    
    // ------------------------------------------------------------------------------------------
    // Open up each of the stream based on the header sent with the chunk.
    
    for (int i = 0; i < nb_chunks; i++) {
        // Read in the stream from the chunk.
        AVStream *stream;
        TPLImageRef stream_header = image_lists[i][image_cursors[i]]; image_cursors[i] += 1;
        if((read_avstream_chunk_from_memory(stream_header.data, stream_header.size, output_format_context, &stream) < 0))
        {
            fprintf(stderr, "Failed to read AVStream chunk.\n");
            return NULL;
        };
        
        stream_input_tbs[i] = stream->time_base; // This is a hack, as this value gets lost after we call avformat_write_header.
        
        if(DEBUG_PRINT_CRAZY)
        {
            fprintf(stderr, "Post init STB (%d): %d/%d (1/=%2.2f)\n", i, stream->time_base.num, stream->time_base.den, (float)stream->time_base.den/stream->time_base.num);
            fprintf(stderr, "Post init RTB (%d): %d/%d (1/=%2.2f)\n", i, stream->r_frame_rate.den, stream->r_frame_rate.num, (float)stream->r_frame_rate.num/stream->r_frame_rate.den);
        }
    }
    
    // Write out any headers for the output files.
    if(avformat_write_header(output_format_context, NULL) != 0)
    {
        fprintf(stderr, "Failed to write header.\n");
        return NULL;
    }
    
    // Go through and read from each stream over and over, taking the packets and av_interleaved_write_frame to the correct stream in the output to merge the files again.
    AVPacket *pkt = (AVPacket *)malloc(sizeof(AVPacket));
    int chunk = 0;
    int read_rets[nb_chunks];
    
    // Init the returns.
    for (int i = 0; i < nb_chunks; i++) {
       read_rets[i] = INT32_MAX;
    }
    
    for(;;)
    {
        // Read in some data.
        if(read_rets[chunk] > 0){
            read_rets[chunk] = read_avpacket(image_lists[chunk], image_sizes[chunk], &image_cursors[chunk], pkt);
        }
        
        // If we got something, process it.
        if(read_rets[chunk] > 0)
        {
            // Set the stream ID (this is incase we have skipped any streams).
            pkt->stream_index = chunk;
            
            // Set the pts and dts.
            if (pkt->pts != AV_NOPTS_VALUE)
                pkt->pts = av_rescale_q(pkt->pts, stream_input_tbs[chunk], output_format_context->streams[chunk]->time_base);
            if (pkt->dts != AV_NOPTS_VALUE)
                pkt->dts = av_rescale_q(pkt->dts, stream_input_tbs[chunk], output_format_context->streams[chunk]->time_base);
            if (pkt->duration != AV_NOPTS_VALUE)
                pkt->duration = av_rescale_q(pkt->duration, stream_input_tbs[chunk], output_format_context->streams[chunk]->time_base);
            
            // For the stream we have a packet for, write out the contents to the corresponding stream output file.
            if(DEBUG_PRINT_CRAZY){
                fprintf(stderr,"Writing packet for stream %d (dts=%lld, pts=%lld, size=%d, dyn_pos=%lld)\n", pkt->stream_index, pkt->dts, pkt->pts, pkt->size, output_io_context->pos);
            }
            
            int ret = av_interleaved_write_frame(output_format_context, pkt);
            av_free_packet(pkt); av_init_packet(pkt);
            
            if(DEBUG_PRINT_CRAZY && ret != 0){
                 fprintf(stderr,"Error writing frame (skippped): ret=%d\n", ret);
            }
        }
        
        // Increment the chunk
        if(chunk == nb_chunks-1)
            chunk = 0;
        else
            chunk++;
        
        // Break if all of the fds are empty.
        int empty_count = 0;
        for (int i = 0; i < nb_chunks; i++) {
            if(read_rets[i] == 0)
                empty_count++;
        }
        
        if (empty_count == nb_chunks) {
            break;
        }
    }
    
    av_freep(&pkt);
    
    // Write the output file trailers.
    av_write_trailer(output_format_context);
    
    // Store the output.
    uint8_t *output_data;
    size_t output_data_size = avio_close_dyn_buf(output_io_context, &output_data) - FF_INPUT_BUFFER_PADDING_SIZE;
    
    if(DEBUG_PRINT){
        fprintf(stderr, "Final output chunk size: %lu bytes\n", output_data_size);
    }
    
    // Free up stuff.
    avformat_free_context(output_format_context);
    
    // ------------------------------------------------------------------------------------------
  
    // Free up all the state.
    
    for(int i = 0; i < nb_chunks; i++){
        free(image_lists[i]); image_lists[i] = NULL;
        free(data_chunks[i]); data_chunks[i] = NULL;
    }

    free(data_chunks);
    free(data_chunks_size);
    free(data_chunks_streamid);
    
    free(image_lists);
    free(image_sizes);
    free(image_cursors);
    free(stream_input_tbs);
    
    // Return the new data.
    jbyteArray dataArray = env->NewByteArray(output_data_size);
    env->SetByteArrayRegion(dataArray, 0, output_data_size, (jbyte *)output_data);
    av_free(output_data);
    
    return dataArray;
};