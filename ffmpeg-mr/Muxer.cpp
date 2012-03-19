#include <iostream>
#include "com_tstordyallison_ffmpegmr_Remuxer.h"
#include "SharedUtil.h"

#define DEBUG_PRINT 1
#define DEBUG_PRINT_CRAZY 1

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
    int nb_chunks = 0;
    
    while(env->CallBooleanMethod(iterator, hasNext))
    {
        // Increment and alloc.
        nb_chunks += 1;
        data_chunks = (uint8_t **)realloc(data_chunks, sizeof(uint8_t *) * nb_chunks);
        data_chunks_size = (size_t *)realloc(data_chunks_size, sizeof(uint8_t *) * nb_chunks);
        
        // Get the chunk and copy over the data.
        jclass  chunk_clazz = env->FindClass("com/tstordyallison/ffmpegmr/Chunk");
        jobject chunk       = env->CallObjectMethod(iterator, next);
        jmethodID getChunkData   = env->GetMethodID(chunk_clazz, "getChunkData", "()Lcom/tstordyallison/ffmpegmr/ChunkData;");
        
        if(!getChunkData)
        {
            throw_new_exception(env, "getChunkData method missing");
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
        
        // Save the data pointers.
        data_chunks[nb_chunks-1] = data;
        data_chunks_size[nb_chunks-1] = data_size;
    }
    
    if(DEBUG)  
        fprintf(stderr, "Loaded %d chunks for remuxer.\n", nb_chunks);
    
    // ------------------------------------------------------------------------------------------
    
    TPLImageRef **image_lists = (TPLImageRef **)malloc(sizeof(TPLImageRef *) * nb_chunks);
    size_t *image_sizes = (size_t *)malloc(sizeof(size_t) * nb_chunks);
    size_t *image_cursors = (size_t *)malloc(sizeof(size_t) * nb_chunks);
    
    // For each of the streams, use tpl_gather to get a list of the images they contain.
    for(int i = 0; i < nb_chunks; i++){
        tpl_gather_image_list(data_chunks[i], data_chunks_size[i], &(image_lists[i]), &(image_sizes[i]));
        image_cursors[i] = 0;
    }
    
    // ------------------------------------------------------------------------------------------
    
    AVIOContext *output_io_context = NULL; avio_open_dyn_buf(&output_io_context);
    AVOutputFormat *output_format = av_guess_format("mkv", "filename.mkv", NULL);
    AVFormatContext *output_format_context = NULL;
    
    // Create an output file AVFormatContext and open the file for writing to the filesystem.
    avformat_alloc_output_context2(&output_format_context, output_format, NULL, NULL);
    if (!&output_format_context) {
        throw_new_exception(env, "Unable to create output context.");
        return NULL;
    }
    
    // Set the IO context for the output.
    output_format_context->pb = output_io_context;
    
    // ------------------------------------------------------------------------------------------
    // Open up each of the stream based on the header sent with the 
    
    for (int i = 0; i < nb_chunks; i++) {
        // Read in the stream from the chunk.
        AVStream *stream;
        TPLImageRef stream_header = image_lists[i][image_cursors[i]]; image_cursors[i] += 1;
        read_avstream_chunk_from_memory(stream_header.data, stream_header.size, output_format_context, &stream);
        
        if(DEBUG_PRINT)
            dump_buffer(stream->codec->extradata, stream->codec->extradata_size);
        
        // Open the codec. Do we need to do this? 
        AVCodec *codec;
        if (!(codec = avcodec_find_decoder(stream->codec->codec_id))) {
            fprintf(stderr, "Unsupported codec with id %d for input stream %d\n", stream->codec->codec_id, stream->index);
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n", stream->index);
        }
        
        if(DEBUG_PRINT)
        {
            fprintf(stderr, "Post init CTB: %d/%d (1/=%2.2f)\n", stream->codec->time_base.num, stream->codec->time_base.den, (float)stream->codec->time_base.den/stream->codec->time_base.num);
            fprintf(stderr, "Post init STB: %d/%d (1/=%2.2f)\n", stream->time_base.num, stream->time_base.den, (float)stream->time_base.den/stream->time_base.num);
            fprintf(stderr, "Post init RTB: %d/%d (1/=%2.2f)\n", stream->time_base.den, stream->r_frame_rate.num, (float)stream->r_frame_rate.num/stream->r_frame_rate.den);
            fprintf(stderr, "Post init TPF: %d\n", stream->codec->ticks_per_frame);
        }
    }
    
    // Write out any headers for the output files.
    if(avformat_write_header(output_format_context, NULL) != 0)
    {
        fprintf(stderr, "Failed to write header.\n");
        return NULL;
    }
    
    // Go through and read from each stream over and over, taking the packets and av_interleaved_write_frame to the correct stream in the output to merge the files again.
    AVPacket pkt;
    int chunk = 0;
    av_init_packet(&pkt);
    int read_rets[nb_chunks];
    
    // Init the returns.
    for (int i = 0; i < nb_chunks; i++) {
       read_rets[i] = 0;
    }
    
    for(;;)
    {
        // Read in some data.
        if(!read_rets[chunk]){
            read_rets[chunk] = read_avpacket(image_lists[chunk], image_sizes[chunk], &image_cursors[chunk], &pkt);
        }
        
        // If we got something, process it.
        if(!read_rets[chunk])
        {
            // For the stream we have a packet for, write out the contents to the corresponding stream output file.
            if(DEBUG_PRINT_CRAZY){
                printf("Writing packet to merge for stream %d (dts=%lld, pts=%lld, size=%d)\n", pkt.stream_index, pkt.dts, pkt.pts, pkt.size);
            }
            
            int ret = av_interleaved_write_frame(output_format_context, &pkt);
            
            if(DEBUG_PRINT_CRAZY && ret != 0){
                printf("Frame written: ret=%d\n", ret);
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
            if(read_rets[i] == -1)
                empty_count++;
        }
        
        if (empty_count == nb_chunks) {
            break;
        }
    }
    
    // Write the output file trailers.
    av_write_trailer(output_format_context);
    
    // Store the output.
    uint8_t *output_data;
    size_t output_data_size = avio_close_dyn_buf(output_io_context, &output_data);
    
    // Free up stuff.
    avformat_free_context(output_format_context);
    
    // ------------------------------------------------------------------------------------------
  
    // TODO: free up the TPL image list
    
    // Free up all the state.
    for(int i = 0; i < nb_chunks; i++)
    {
        free(data_chunks[i]);
        data_chunks[i] = NULL;
    }
    free(data_chunks_size);
    
    // Return the new data.
    jbyteArray dataArray = env->NewByteArray(output_data_size);
    env->SetByteArrayRegion(dataArray, 0, output_data_size, (jbyte *)output_data);
    free(output_data);
    
    return dataArray;
};