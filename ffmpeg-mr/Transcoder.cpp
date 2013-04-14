#include <jni.h>
#include "com_tstordyallison_ffmpegmr_Transcoder.h"
#include "SharedUtil.h"
#include <map>

#define DEBUG 1
#define DEBUG_PRINT 1
#define DEBUG_PRINT_CRAZY 0

using namespace std;

extern "C" {
    #include "ffmpeg_tpl.h"
    #include "libavcodec/avcodec.h"
    #include "libavutil/mathematics.h"
    #include "libavutil/imgutils.h"
    #include "libavutil/dict.h"
    #include "libavformat/avformat.h"
    #include "libavutil/mathematics.h"
    #include "libavutil/fifo.h"
    #include "libavutil/avutil.h"
    #include "libavutil/opt.h"
    #include "libswscale/swscale.h"
#ifndef AWSBUILD
    #include "libavutil/timestamp.h"
#endif
}

typedef struct TranscoderState {
    uint8_t         *data; // Actual TPL chunk data, including an initial header (e.g. stream, pkt, pkt, and so on). 
    size_t          data_size;
    TPLImageRef     *image_list;
    size_t          image_list_size;
    int             image_cursor; // Position in the TPL chunk data array. 
    
    long        *chunk_points; // Ordered list of timestamps (in the stream base), that we must split on.
    size_t      chunk_points_size;
    int         chunk_points_cursor_enc;
    int         chunk_points_cursor_out;
    AVRational  chunk_tb;
    
    AVRational input_tb;
    AVRational input_frame_rate;
    AVRational input_aspect_ratio;

    AVCodecContext  *decoder;
    AVCodecContext  *encoder;
    
    int demux_frame_count;
    int encoder_frame_count;
    int decoder_frame_count;
    
    int64_t decoder_pts; // PTS increment for decoding data.
    int64_t offset_pts;
    int64_t encoder_pts; // PTS increment for encoding data.
    
    AVPacket *input_packet;
    
    int decoder_flushing;
    
    int resample; // true if we are resampling.
    struct SwsContext *img_resample_ctx; /* for image resampling */
    int resample_height;
    int resample_width;
    int resample_pix_fmt;
    
    AVFrame *raw_video; // A decoded picture.
    AVFrame *raw_audio; // A decoded number of audio samples
    
    AVFifoBuffer *fifo; // Circular buffer for storing audio samples and adjusting their size for the encoder. 

    int stream_index;
    
    TranscoderState(){
        data = NULL;
        data_size = 0;
        
        image_list = NULL;
        image_list_size = 0;
        image_cursor = 0;
        
        chunk_points = NULL;
        
        chunk_points_size = 0;
        chunk_points_cursor_enc = 0;
        chunk_points_cursor_out = 0;
        
        input_tb            = (AVRational){0,1};
        input_frame_rate    = (AVRational){0,1};
        input_aspect_ratio  = (AVRational){0,1};
        
        decoder = NULL;
        encoder = NULL;
        
        demux_frame_count = 0;
        encoder_frame_count = 0;
        decoder_frame_count = 0;
        
        decoder_pts = -1;
        offset_pts = -1;
        encoder_pts = -1;
        
        input_packet = (AVPacket *)malloc(sizeof(AVPacket));
        av_init_packet(this->input_packet);
        
        decoder_flushing = 0;
        
        raw_audio = NULL;
        raw_video = avcodec_alloc_frame();
        
        fifo = av_fifo_alloc(1024);
        
        stream_index = -1;
        
        img_resample_ctx = NULL;
    };
    
    ~TranscoderState(){
        // Free some stuff.
        if(data != NULL)
        {
            free(data);
            data = NULL;
        }
        
        if(image_list != NULL)
        {
            free(image_list); // Allocated by ffmpeg_tpl.c
            image_list = NULL;
        }
        
        if(chunk_points != NULL)
        {
            free(chunk_points);
            chunk_points = NULL;
        }
        
        if(decoder != NULL)
        {
            avcodec_close(decoder);
            av_free(decoder);
            decoder = NULL;
        }
        
        if(encoder != NULL)
        {
            avcodec_close(encoder);
            av_free(encoder);
            encoder = NULL;
        }
        
        if(raw_audio != NULL)
        {
            av_freep(&raw_audio);
        }
        
        if(raw_video != NULL)
        {
            av_freep(&raw_video);
        }
        
        if(input_packet){
            if(input_packet->data){
                av_free_packet(input_packet);
                av_freep(&input_packet);
            }
            else
                av_free(input_packet);
        }
        
        if(fifo)
            av_fifo_free(fifo);
        
        if(img_resample_ctx)
            sws_freeContext(img_resample_ctx);
    }
    
    
} TranscoderState;

class TranscoderTracker {
    
    private:
        map<int, TranscoderState*> objectRegister;
        
    public:
        TranscoderState *getObjectState(JNIEnv *env, jobject obj)
        {
            int hashCode = getHashCode(env, obj);
            if(objectRegister.find(hashCode) != objectRegister.end())
            {
                return objectRegister[hashCode];
            }
            return NULL;
        };
        
        void registerObjectState(JNIEnv *env, jobject obj, TranscoderState *objstate)
        {
            int hashCode = getHashCode(env, obj);
            objectRegister[hashCode] = objstate;
        };
        
        void unregisterObjectState(JNIEnv *env, jobject obj)
        {
            TranscoderState *state = getObjectState(env, obj);
            if(state != NULL)
            {
                // Remove the state from the map.
                int hashCode = getHashCode(env, obj);
                objectRegister.erase(hashCode);
                
                // Free the state.
                delete state;
            }
        };
};

static TranscoderTracker tracker;


// --------------- Internal Functions ---------------

static bool initWithBytesThrowNonZero(int err, const char *msg, TranscoderState *state, JNIEnv *env)
{
    
    // Do error catch.
    if(err != 0)
    {
        throw_new_exception(env, msg);
        if(DEBUG)
        {
            char buffer[50];
            av_strerror(err, buffer, 50);
            printf("ffmpeg-mr Error: %s\nffmpeg-mr Return Code: %d (%s)\n", msg, err, buffer);
        }
        return true; // There was an error.
    } 
    else
        return false; // No errors here!
}

static void generate_silence(uint8_t* buf, enum AVSampleFormat sample_fmt, size_t size)
{
    int fill_char = 0x00;
    if (sample_fmt == AV_SAMPLE_FMT_U8)
        fill_char = 0x80;
    memset(buf, fill_char, size);
}

static void getNextPacket_tidy(TranscoderState *state, int err)
{
    // Deallocs
    // JNI strings?
    // JNI memory regions?
    
    if(DEBUG){
        fprintf(stderr, "Number of packets:        %lu\n", state->image_list_size-1);
        fprintf(stderr, "Number of frames demuxed: %d\n", state->demux_frame_count);
        fprintf(stderr, "Number of frames decoded: %d\n", state->decoder_frame_count);
        fprintf(stderr, "Number of frames encoded: %d\n", state->encoder_frame_count);
    }
}

static void video_resample(TranscoderState *state)
{
    AVCodecContext *enc = state->encoder;
    AVFrame *in_picture = state->raw_video;
    
    int resample_changed = state->resample_width   != in_picture->width  ||
                           state->resample_height  != in_picture->height ||
                           state->resample_pix_fmt != in_picture->format;
    
    if (resample_changed) {
        state->resample_width   = in_picture->width;
        state->resample_height  = in_picture->height;
        state->resample_pix_fmt = in_picture->format;
    }
    
    state->resample =  in_picture->width   != enc->width  ||
                       in_picture->height  != enc->height ||
                       in_picture->format  != enc->pix_fmt;
    
    if (state->resample) {
    
        if (!state->img_resample_ctx || resample_changed) {
            // Get a new scaler context and bin the old one.
            sws_freeContext(state->img_resample_ctx);
            state->img_resample_ctx = sws_getContext(in_picture->width, in_picture->height, (enum PixelFormat)in_picture->format,
                                                     enc->width, enc->height, enc->pix_fmt,
                                                     SWS_BICUBIC, NULL, NULL, NULL);
            if (state->img_resample_ctx == NULL) {
                av_log(NULL, AV_LOG_FATAL, "Cannot get resampling context\n");
            }
        }
        
        // Init our new out picture.
        AVFrame *new_frame = avcodec_alloc_frame();
        avpicture_alloc((AVPicture *)new_frame, enc->pix_fmt, enc->width, enc->height);
                
        // Resample.
        sws_scale(state->img_resample_ctx, in_picture->data, in_picture->linesize, 0, state->resample_height, new_frame->data, new_frame->linesize);
        
        // Copy some data into our new frame.
        new_frame->width = enc->width;
        new_frame->height = enc->height;
        new_frame->format = enc->pix_fmt;
        new_frame->pts = in_picture->pts;
        
        // Replace raw video.
        //avpicture_free((AVPicture *)state->raw_video);
        if(state->raw_video != NULL)
            av_freep(&(state->raw_video));
        state->raw_video = new_frame;
    }
}

static int read_avpacket(TranscoderState *state)
{
    // Get the next packet from the data stream.
    int err = 0;
    if(state->image_cursor != state->image_list_size)
    {
        TPLImageRef *packet = &(state->image_list[state->image_cursor]);
        err = read_avpacket_chunk_from_memory(packet->data, packet->size, state->input_packet);
        
        if(err == 0){
#ifndef AWSBUILD
            if(DEBUG_PRINT_CRAZY)
                    fprintf(stderr, "Demuxed packet: pts:%s, pts_time:%s, dts:%s, dts_time:%s, key=%c\n",
                           av_ts2str(state->input_packet->pts), av_ts2timestr(state->input_packet->pts, &state->input_tb),
                           av_ts2str(state->input_packet->dts), av_ts2timestr(state->input_packet->dts, &state->input_tb), 
                            state->input_packet->flags & AV_PKT_FLAG_KEY ? 'Y' : 'N');
#endif
            state->demux_frame_count += 1;
            state->image_cursor += 1;
            
            if(state->stream_index < 0)
                state->stream_index = state->input_packet->stream_index;
            return (int)packet->size;
        }
        else
            return err;
    }
    else
        return 0;

}

static int decode_packet(JNIEnv *env, TranscoderState *state)
{
    // Shorthand.
    AVCodecContext *enc = state->encoder;
    AVCodecContext *dec = state->decoder;
    
    // Decode it into either raw.audio or raw.video.
    // Setup our encoder, and init our raw structs. For now this is for either H.264 or AAC.
    switch (state->decoder->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
        {   
            int got_picture = 0;
            while(!got_picture)
            {
                int ret;
                ret = read_avpacket(state);
                if(ret < 0)
                {
                    throw_new_exception(env, "Read failed - TPL image read error.");
                    return -1;
                }
                else if(ret == 0)
                {
                    if(state->decoder->codec->capabilities & CODEC_CAP_DELAY)
                    {
                        // Set the input packet data and size to NULL - this tells the encoder that we want it to flush.
                        av_free_packet(state->input_packet);
                        state->decoder_flushing = 1;
                    }
                    else
                        return -1;
                }
                
                // Set the pts starting point for the encoder.
                if(state->decoder_pts < 0 && state->input_packet->pts != AV_NOPTS_VALUE){
                    state->decoder_pts = state->input_packet->pts;
                    state->offset_pts = av_rescale_q(state->input_packet->pts, state->input_tb, state->encoder->time_base);
                    state->encoder_pts = av_rescale_q(state->input_packet->pts, state->input_tb, state->encoder->time_base);
                }
                
                // If this is a keyframe, then pts=dts, so we can set the dts (this should pretty much always happen in a valid stream!)
                if(state->decoder_pts < 0 && 
                   state->input_packet->pts == AV_NOPTS_VALUE && 
                   state->input_packet->dts != AV_NOPTS_VALUE && 
                   state->input_packet->flags & AV_PKT_FLAG_KEY){
                    state->decoder_pts = state->input_packet->dts;
                    state->offset_pts = av_rescale_q(state->input_packet->dts, state->input_tb, state->encoder->time_base);
                    state->encoder_pts = av_rescale_q(state->input_packet->dts, state->input_tb, state->encoder->time_base);
                }
                
                // Store away the pts.
                state->decoder->reordered_opaque = state->input_packet->pts;
                
                // Hacky fix for the case of both encoder and decoder flushing.
                AVFrame *output_frame;
                if(state->raw_video != NULL)
                    output_frame = state->raw_video;
                else
                    output_frame = avcodec_alloc_frame();
                
                // Decode.
                ret = avcodec_decode_video2(state->decoder, output_frame, &got_picture, state->input_packet);
        
                // Free input data.
                av_free_packet(state->input_packet);
                
                if(ret >= 0)
                {
                    if (got_picture) {
    
                        // Counter.
                        state->decoder_frame_count += 1;
                        
                        // Set the PTS.
                        int64_t *best_effort_timestamp = (int64_t *)av_opt_ptr(avcodec_get_frame_class(), state->raw_video, "best_effort_timestamp");
                        if(state->raw_video->pts == AV_NOPTS_VALUE && *best_effort_timestamp != AV_NOPTS_VALUE)
                           state->raw_video->pts = *best_effort_timestamp;
                        
                        // If there is no PTS, see if we can get one from the various places we might have stored one in.
                        if(state->raw_video->pts == AV_NOPTS_VALUE)
                            state->raw_video->pts = state->raw_video->pkt_pts;
                        if(state->raw_video->pts == AV_NOPTS_VALUE)
                            state->raw_video->pts = state->decoder->reordered_opaque;
                        
                        // If we have a PTS now and it was in the packet itself set the pts starting point for the encoder.
                        if(state->decoder_pts < 0 && state->raw_video->pts != AV_NOPTS_VALUE){
                            state->decoder_pts = state->raw_video->pts;
                            state->offset_pts = av_rescale_q(state->raw_video->pts, state->input_tb, state->encoder->time_base);
                            state->encoder_pts = av_rescale_q(state->raw_video->pts, state->input_tb, state->encoder->time_base);
                        }
                        
                        // Print the frame details
                        if(DEBUG_PRINT_CRAZY)
                            fprintf(stderr, "Decoded frame: pts=%lld, type=%c\n", state->raw_video->pts, av_get_picture_type_char(state->raw_video->pict_type));

                    }
                    else
                    {
                        if(state->decoder_flushing){
                            if(state->raw_video == NULL && output_frame){
                                free(output_frame);
                                output_frame = NULL;
                            }

                            return -1; // End of the stream.
                        }
                            
                    }
                }
                else{
                    throw_new_exception(env, "Read failed - decoder failed to decode video packet.");
                    return -1;
                }
                
                if(state->raw_video == NULL && output_frame){
                    free(output_frame);
                    output_frame = NULL;
                }
                
            };
            
            break;
        }
        case AVMEDIA_TYPE_AUDIO:
        {

            // Audio is segmented into frames that are <= encoder->frame_size, so we might not always ask for new packet. 
            // If the fifo queue still has some data in it that we can encode, we pass that to the caller instead. 
            // Otherwise we get a new packet from the TPL images. 
            
            int frame_bytes = enc->frame_size * av_get_bytes_per_sample(enc->sample_fmt) * enc->channels;
            int frame_size = enc->frame_size;
            if (av_fifo_size(state->fifo) >= frame_bytes)
            {
                // Alloc a buffer for our new frame.
                uint8_t *audio_buf = (uint8_t*)malloc(frame_bytes);
                
                // Read from the buffer, and place the resulting frame in state->raw_audio;
                av_fifo_generic_read(state->fifo, audio_buf, frame_bytes, NULL);
                
                // Check we've alloc'd a frame.
                if (!state->raw_audio) {
                    state->raw_audio = avcodec_alloc_frame();
                }
                
                // Construct a AVFrame to place in state->raw_audio for the encoder.
                AVFrame *frame = state->raw_audio;
                
                // Free any data left from the last use.
                if (frame->extended_data != frame->data)
                    av_freep(&frame->extended_data);
                
                // Reset the frame.
                avcodec_get_frame_defaults(frame);
                
                // Fill the frame.
                frame->nb_samples = frame_size;
                avcodec_fill_audio_frame(frame, enc->channels, enc->sample_fmt, audio_buf, frame_bytes, 1);
                
                // Set the pts.
                frame->pts = state->decoder_pts;
                
                // Increment out pts counter. 
                state->decoder_pts += frame->nb_samples;
                
                if(DEBUG_PRINT_CRAZY)
                    fprintf(stderr, "Decoded frame: pts=%lld, nb_samples=%d\n", state->raw_audio->pts, state->raw_audio->nb_samples);
                
            }
            else
            {
                // Do a normal read from the TPL images and decode the packet into the fifo or state->raw_audio.
                int got_samples = 0;
                while(!got_samples)
                {
                    int ret;
                    ret = read_avpacket(state);
                    if(ret < 0)
                    {
                        throw_new_exception(env, "Read failed - TPL image read error.");
                        return -1;
                    }
                    else if(ret == 0)
                    {
                        // Flush out the remaining audio frames in the fifo queue.
                        int fifo_bytes = av_fifo_size(state->fifo);
                        if (fifo_bytes > 0) {
                            
                            // Alloc some space for the frame.
                            int frame_bytes = fifo_bytes;
                            uint8_t *audio_buf = (uint8_t*)malloc(frame_bytes);
                            
                            av_fifo_generic_read(state->fifo, audio_buf, frame_bytes, NULL);
                            
                            // Pad the last frame with silence.
                            if (!(enc->codec->capabilities & CODEC_CAP_SMALL_LAST_FRAME)) {
                                fprintf(stderr, "Warning: Padding a frame with slience. Bollocks.");
                                frame_bytes = enc->frame_size * enc->channels * av_get_bytes_per_sample(enc->sample_fmt);
                                generate_silence(audio_buf+fifo_bytes, enc->sample_fmt, frame_bytes - fifo_bytes);
                            }
                            
                            // Construct a AVFrame to place in state->raw_audio for the encoder.
                            AVFrame *frame = state->raw_audio;
                            
                            // Free any data left from the last use.
                            if (frame->extended_data != frame->data)
                                av_freep(&frame->extended_data);
                            
                            // Reset the frame.
                            avcodec_get_frame_defaults(frame);
                            
                            // Calculate the number of samples and fill the frame.
                            frame->nb_samples  = frame_bytes / (enc->channels * av_get_bytes_per_sample(enc->sample_fmt));
                            avcodec_fill_audio_frame(frame, enc->channels, enc->sample_fmt, audio_buf, frame_bytes, 1);
                            
                            // Set the pts.
                            frame->pts = state->decoder_pts;
                            
                            // Increment out pts counter. 
                            state->decoder_pts += frame->nb_samples;
                            
                            // Return with the new frame in the raw_audio.
                            return 0;
                            
                        } 
                        else 
                        {
                            
                            if((state->decoder->codec->capabilities & CODEC_CAP_DELAY)){
                                // Set the input packet data and size to NULL - this tells the encoder that we want it to flush.
                                state->input_packet->data = NULL;
                                state->input_packet->size = 0;
                            }
                            else
                                return -1; // End of stream.
                            
                        }
                    }
                    
                    // Set the starting pts.
                    if(state->decoder_pts < 0){
                        state->encoder_pts = 
                        state->decoder_pts = av_rescale_q(state->input_packet->pts, state->input_tb, state->decoder->time_base);
                    }
                    
                    // Decode.
                    AVFrame *decoded_frame = avcodec_alloc_frame(); avcodec_get_frame_defaults(decoded_frame);
                    ret = avcodec_decode_audio4(state->decoder, decoded_frame, &got_samples, state->input_packet);
                    
                    // Free input data.
                    av_free_packet(state->input_packet);
                    
                    // Act on the return value.
                    if(ret >= 0)
                    {
                        if(!got_samples && (state->decoder->codec->capabilities & CODEC_CAP_DELAY) && state->input_packet->data == NULL && state->input_packet->size == 0)
                        {
                            return -1; // End of the stream.
                        }
                        else // We have a sample.
                        {
                            // Counter.
                            state->decoder_frame_count += 1;
                            
                            // Raw input buffers.
                            uint8_t *input_audio_buf    = decoded_frame->data[0];
                            int      input_audio_size   = decoded_frame->nb_samples * dec->channels * av_get_bytes_per_sample(enc->sample_fmt);
                            
                            // Place on the fifo queue.
                            av_fifo_realloc2(state->fifo, av_fifo_size(state->fifo) + input_audio_size);
                            av_fifo_generic_write(state->fifo, input_audio_buf, input_audio_size, NULL);
                            
                            // This pulls the next frame from the fifo queue.
                            decode_packet(env, state);
                        }
                    }
                    else{
                        throw_new_exception(env, "Read failed - decoder failed to decode audio packet.");
                        return -1;
                    }
                    
                    // Free the decoded frame struct (the data will live on to the encoder).
                    av_freep(&decoded_frame);
                };
                
            }
            
            break;
        }
        default:
        {
            // This would be an invalid stream, so appears to be empty from the start.
            return -1;
        }
            
    }
    
    return 0;
}


// --------------- JNI Functions ---------------

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    initWithBytes
 * Signature: (JJ[J[B)I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_initWithBytes
(JNIEnv *env, jobject obj, jlong chunk_tb_num, jlong chunk_tb_den, jlongArray chunk_points, jbyteArray data, 
 jdouble video_res_scale, jdouble video_crf, jint video_bitrate, jint audio_bitrate, jint video_threads){
    
    // Init state;
    int err = 0;
    TranscoderState *state = new TranscoderState;
    
    // Check input.
    if(video_res_scale <= 0)
        video_res_scale = 1;
    
    if(video_crf < 0)
        video_crf = 21;
    
    if(video_bitrate < 0)
        video_bitrate = 512000;
    
    if(audio_bitrate <= 0)
        audio_bitrate = 64000;
    
    if(video_crf > 0)
        video_bitrate = 0;

    if(video_threads < 0)
        video_threads = 0;
    
    // Copy the chunk over into memory from the JVM.
    state->data_size = env->GetArrayLength(data);
    state->data = (uint8_t *)malloc(sizeof(jbyte) * state->data_size);
    env->GetByteArrayRegion(data, 0, (jint)state->data_size, (jbyte *)state->data);
    env->DeleteLocalRef(data);
    
    // Copy over the chunk points into memory from the JVM.
    state->chunk_points_size = env->GetArrayLength(chunk_points);
    state->chunk_points = (long *)malloc(sizeof(jlong)* state->chunk_points_size);
    env->GetLongArrayRegion(chunk_points, 0, (jint)state->chunk_points_size, (jlong *)state->chunk_points);
    env->DeleteLocalRef(chunk_points);
    
    state->chunk_tb = (AVRational){chunk_tb_num, chunk_tb_den};
    
    // Go through the data and identify the TPL images that we are going to read.
    tpl_gather_image_list(state->data, state->data_size, &(state->image_list), &(state->image_list_size));
    
    // Get the decoder info from the chunk header.
    TPLImageRef *header = &(state->image_list[state->image_cursor]);
    if((err = read_avstream_chunk_as_cc_from_memory(header->data, header->size, &(state->decoder), &(state->input_tb), &(state->input_frame_rate), &(state->input_aspect_ratio))) != 0)
    {
        throw_new_exception(env, "Init failed - reading header TPL image from data.");
        return err;
    }
    state->image_cursor += 1;
    
    // Setup the decoder (we just get raw data from the TPL read).
    AVCodec *decoder_codec = avcodec_find_decoder(state->decoder->codec_id);
    if(decoder_codec != NULL)
    {
        AVDictionary *copts = NULL;
        if(initWithBytesThrowNonZero(avcodec_open2(state->decoder, decoder_codec, &copts), "Failed to open decoder codec.", state, env)) return -1;
        state->decoder->thread_count = video_threads;
    }
    else
    {
        throw_new_exception(env, "Init failed - loading decoder.");
        return err;
    }
    
    // Setup our encoder, and init our raw structs. For now this is for either H.264 or AAC.
    switch (state->decoder->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
        {
            AVCodec *encoder_codec = avcodec_find_encoder(CODEC_ID_H264);
            if(encoder_codec != NULL)
            {
                AVDictionary *copts = NULL;
                state->encoder = avcodec_alloc_context3(encoder_codec);
                state->encoder->flags |= CODEC_FLAG_GLOBAL_HEADER;
                state->encoder->gop_size = 200;
                state->encoder->keyint_min = 2;
                state->encoder->max_b_frames = 3;
                state->encoder->thread_count = video_threads;
                if(video_crf > 0){
                    char buf[10];
                    sprintf(buf, "%2.2f", video_crf);
                    av_dict_set(&copts, "crf", buf, 0);
                }
                else
                    state->encoder->bit_rate = video_bitrate;
                state->encoder->sample_aspect_ratio = state->input_aspect_ratio;
                state->encoder->width = lround(state->decoder->width*video_res_scale);
                state->encoder->height = lround(state->decoder->height*video_res_scale);
                state->encoder->pix_fmt = state->decoder->pix_fmt;
                state->encoder->time_base = (AVRational){state->input_frame_rate.den,state->input_frame_rate.num};
                
                // Print out the new values.
                if(DEBUG) {
                    fprintf(stderr, "Packet  (st) tb: %d/%d (1/=%2.2f)\n", state->input_tb.num, state->input_tb.den, (float)state->input_tb.den/state->input_tb.num);
                    fprintf(stderr, "Decoder (cc) tb: %d/%d (1/=%2.2f)\n", state->decoder->time_base.num, state->decoder->time_base.den, (float)state->decoder->time_base.den/state->decoder->time_base.num);
                    fprintf(stderr, "Encoder (cc) tb: %d/%d (1/=%2.2f)\n", state->encoder->time_base.num, state->encoder->time_base.den, (float)state->encoder->time_base.den/state->encoder->time_base.num);
                }
                
                if(initWithBytesThrowNonZero(avcodec_open2(state->encoder, encoder_codec, &copts), "Error opening encoder codec.", state, env)) return -1;
                
            }
            else
            {
                throw_new_exception(env, "Init failed - loading encoder H264.");
                return err;
            }
            
            break;
        }
        case AVMEDIA_TYPE_AUDIO:
        {
            AVCodec *encoder_codec = avcodec_find_encoder(CODEC_ID_AAC);
            if(encoder_codec != NULL)
            {
                AVDictionary *copts= NULL;
                state->encoder = avcodec_alloc_context3(encoder_codec);
                state->encoder->flags |= CODEC_FLAG_GLOBAL_HEADER;
                state->encoder->bit_rate = audio_bitrate;
                state->encoder->sample_fmt = state->decoder->sample_fmt;
                state->encoder->sample_rate = state->decoder->sample_rate;
                state->encoder->channels = state->decoder->channels;
                state->encoder->time_base = state->decoder->time_base;
                
                // Print out the new values.
                if(DEBUG) {
                    fprintf(stderr, "Packet  (st) tb: %d/%d (1/=%2.2f)\n", state->input_tb.num, state->input_tb.den, (float)state->input_tb.den/state->input_tb.num);
                    fprintf(stderr, "Decoder (cc) tb: %d/%d (1/=%2.2f)\n", state->decoder->time_base.num, state->decoder->time_base.den, (float)state->decoder->time_base.den/state->decoder->time_base.num);
                    fprintf(stderr, "Encoder (cc) tb: %d/%d (1/=%2.2f)\n", state->encoder->time_base.num, state->encoder->time_base.den, (float)state->encoder->time_base.den/state->encoder->time_base.num);
                }
                
                if(initWithBytesThrowNonZero(avcodec_open2(state->encoder, encoder_codec, &copts), "Error opening encoder codec.", state, env)) return -1;
            }
            else
            {
                throw_new_exception(env, "Init failed - loading encoder AAC.");
                return err;
            }
            
            break;
        }
        default:
            throw_new_exception(env, "Init failed - invalid stream. Audio and video only.");
            return err;
    }
    
    // Choose the time base for the stream chunkpoints to use.
    AVRational chunk_time_base;
    switch (state->decoder->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
        {   
            chunk_time_base = state->encoder->time_base;
            break;
        }
        case AVMEDIA_TYPE_AUDIO:
        {   
            chunk_time_base = state->decoder->time_base;
            break;
        }
        default:
        {    
            chunk_time_base = state->input_tb;
            break;
        }
    }
    
    
    // Convert the chunkpoint timestamps to the same as the time base for the chunk.
    AVRational chunk_tb = {chunk_tb_num, chunk_tb_den};
    for(int i = 0; i < state->chunk_points_size; i++)
    {   
        if(DEBUG_PRINT)
        {
            fprintf(stderr, "Rescaling TS: %ld from %d/%d to %d/%d = ", state->chunk_points[i], chunk_tb.num, chunk_tb.den, chunk_time_base.num, chunk_time_base.den);
            
        }
        state->chunk_points[i] = av_rescale_q(state->chunk_points[i], chunk_tb, chunk_time_base);
        if(DEBUG_PRINT)
        {
            fprintf(stderr, "%ld\n", state->chunk_points[i]);
            
        }
    }
    
    // All done, add it to the register. 
    tracker.registerObjectState(env, obj, state);
    return 0;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    getNextPacket
 * Signature: ()Lcom/tstordyallison/ffmpegmr/DemuxPacket;
 * 
 * This method is main transcoder. It pulls frames through the decode, and from the TPL chunk buffer as it needs them and feeds them into the encoder 
 * to keep making frames to pass to Java. When it returns null, all of the frames have been encoded (or we have thrown an exception).
 *
 */
JNIEXPORT jobject JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_getNextPacket
(JNIEnv *env, jobject obj){
    
    TranscoderState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Make a new AVPacket for our output.
        AVPacket output_pkt; output_pkt.data = NULL;
        av_init_packet(&output_pkt);
        
        // Encode the raw_audio or raw_video and output to new AVPacket.
        switch (state->encoder->codec_type) {
            case AVMEDIA_TYPE_VIDEO:
            {
                int got_pkt = 0;
                while(!got_pkt)
                {
                    int ret = 0;
                    
                    // Advance the decoder/pull the next frame through.
                    if(decode_packet(env, state) != 0){
                        if(state->encoder->codec->capabilities & CODEC_CAP_DELAY){
                            av_freep(&state->raw_video); // This sets raw_video to NULL as well for the flush.
                        }
                        else
                        {
                            getNextPacket_tidy(state, 0);
                            return NULL;
                        }
                    }
                    
                    // Clearout the raw_video AVFrame to stop the decoder and encoder getting mixed up.
                    if(state->raw_video){
                        {
                            AVFrame *new_frame = avcodec_alloc_frame();

                            for(int i = 0; i < AV_NUM_DATA_POINTERS; i++)
                                new_frame->data[i] = state->raw_video->data[i];
                            for(int i = 0; i < AV_NUM_DATA_POINTERS; i++)
                                new_frame->linesize[i] = state->raw_video->linesize[i];
                            for(int i = 0; i < AV_NUM_DATA_POINTERS; i++)
                                new_frame->base[i] = state->raw_video->base[i];
                            
                            new_frame->width = state->raw_video->width;
                            new_frame->height = state->raw_video->height;
                            new_frame->format = state->raw_video->format;
                            new_frame->pts = state->raw_video->pts;
                            
                            av_freep(&state->raw_video); 
                            state->raw_video = new_frame;
                        }
                    
                        // Set the input PTS.
                        state->raw_video->pts = state->encoder_pts;
                        
                        // Check out the ts to see if we are on a defined chunkpoint (a forced keyframe). 
                        // If we are instruct the encoder to output an I-Frame;
                        if (state->chunk_points_cursor_enc < state->chunk_points_size &&
                            state->raw_video->pts >= state->chunk_points[state->chunk_points_cursor_enc]) {
                            state->raw_video->pict_type = AV_PICTURE_TYPE_I;
                            state->chunk_points_cursor_enc += 1;
                            if(DEBUG)
                                fprintf(stderr, "Chunk point marked with I frame at %lld.\n", state->raw_video->pts);
                        };  
                        
                        // Increment the PTS.
                        state->encoder_pts += 1;
                        
                        // Perform any required resampling (resizing of the frame resolution to match the encoder if needed.).
                        video_resample(state);
                    }
    
                    // Encode the new frame.
                    ret = avcodec_encode_video2(state->encoder, &output_pkt, state->raw_video, &got_pkt);
                
                    // Dealloc the data if it was a resample.
                    if(state->resample && state->raw_video)
                        avpicture_free((AVPicture *)state->raw_video);
                    
                    // Act on the return value.
                    if(ret == 0){
                        if(got_pkt)
                        {
                            state->encoder_frame_count += 1;
                            
                            if(state->encoder->coded_frame->key_frame)
                                output_pkt.flags |= AV_PKT_FLAG_KEY;
                            
                            output_pkt.stream_index = state->stream_index;
                            
                            if(DEBUG_PRINT_CRAZY)
                                fprintf(stderr, "Encoded frame: pts=%lld, type=%c\n", output_pkt.pts, av_get_picture_type_char(state->encoder->coded_frame->pict_type));
                        }
                        else
                        {
                            // Check if this is actually the end of the stream.
                            if(state->raw_video == NULL && (state->encoder->codec->capabilities & CODEC_CAP_DELAY))
                            {
                                getNextPacket_tidy(state, 0);
                                return NULL;
                            }
                        }
                    }
                    else // Error.
                    {
                        // This would be a cock up.
                        getNextPacket_tidy(state, -1);
                        fprintf(stderr, "Something went wrong encoding a video frame.\n");
                        if(DEBUG)
                        {
                            char buffer[50];
                            av_strerror(ret, buffer, 50);
                            fprintf(stderr, "ffmpeg Return Code: %d (%s)\n", ret, buffer);
                        }
                        throw_new_exception(env, "Encoding failed.");
                        return NULL;
                    }
                    
                    // Print some debug.
                    if(DEBUG_PRINT)
                    {
                        if(state->encoder_frame_count != 0 && (state->encoder_frame_count == 1 || state->encoder_frame_count % 250 == 0 || state->encoder_frame_count == state->image_list_size-1) )
                        {
                            fprintf(stderr, "%d of %lu frames encoded...\n", state->encoder_frame_count, state->image_list_size-1);
                        }
                    }
                }
                            
                break;
            }
            case AVMEDIA_TYPE_AUDIO:
            {
                
                int got_pkt = 0;
                while(!got_pkt)
                {
                
                    int ret = 0;
                    
                    // Advance the decoder/pull the next frame through.
                    if(decode_packet(env, state) != 0){
                        if(state->encoder->codec->capabilities & CODEC_CAP_DELAY)
                            av_freep(&state->raw_audio); // This sets raw_audio to NULL as well for the flush.
                        else
                        {
                            getNextPacket_tidy(state, 0) ;
                            return NULL;
                        }
                    }
                    
                    // Encode the new frame.
                    if(state->raw_audio)
                        state->encoder->reordered_opaque = state->raw_audio->nb_samples;
                    ret = avcodec_encode_audio2(state->encoder, &output_pkt, state->raw_audio, &got_pkt);
                    
                    // Free the raw audio - FIXME - There is probably a better way to free this. But for now - we just do it internally.
                    if(state->raw_audio)
                        av_freep(&(state->raw_audio->data[0]));
                    
                    if(ret == 0){
                        if(got_pkt)
                        {
                            state->encoder_frame_count += 1;
                            
                            int frame_size = state->encoder->frame_size;
                            
                            if(output_pkt.pts == AV_NOPTS_VALUE)
                                output_pkt.pts = state->encoder_pts;
                            
                            if(output_pkt.dts == AV_NOPTS_VALUE)
                                output_pkt.dts = state->encoder_pts;
                            
                            if(state->encoder->coded_frame->key_frame) 
                                output_pkt.flags |= AV_PKT_FLAG_KEY;
                            
                            output_pkt.duration = frame_size;
                            output_pkt.stream_index = state->stream_index;
                            state->encoder_pts += frame_size;
                            
                            if(DEBUG_PRINT_CRAZY)
                                fprintf(stderr, "Encoded frame: pts=%lld, nb_samples=%d, size=%d\n", output_pkt.pts, output_pkt.duration, output_pkt.size);
                        }
                        else
                        {
                            if(state->raw_audio == NULL && (state->encoder->codec->capabilities & CODEC_CAP_DELAY))
                            {
                                // This is the end of the stream.
                                getNextPacket_tidy(state, -1);
                                return NULL;
                            }
                        }
                    }
                    else // Error.
                    {
                        // This would be a cock up.
                        getNextPacket_tidy(state, -1);
                        fprintf(stderr, "Something went wrong encoding an audio frame.\n");
                        if(DEBUG)
                        {
                            char buffer[50];
                            av_strerror(ret, buffer, 50);
                            fprintf(stderr, "ffmpeg Return Code: %d (%s)\n", ret, buffer);
                        }
                        throw_new_exception(env, "Encoding failed.");
                        return NULL;
                    }
                }
                
                // Print some debug.
                if(DEBUG_PRINT)
                {
                    if(state->encoder_frame_count != 0 && (state->encoder_frame_count == 1 || state->encoder_frame_count % 5000 == 0 || state->encoder_frame_count == state->image_list_size-1) )
                    {
                        fprintf(stderr, "%d of %lu frame packets encoded...\n", state->encoder_frame_count, state->image_list_size-1);
                    }
                }
        
                break;
            }
            default:
            {
                // This would be an invalid stream.
                getNextPacket_tidy(state, -1);
                return NULL;
            }
                
        }
        
        // Convert the AVPacket to TPL, and free the AVPacket.
        uint8_t *pkt_tpl_data = NULL;
        int pkt_tpl_size = 0;
        write_avpacket_chunk_to_memory(&output_pkt, &pkt_tpl_data, &pkt_tpl_size);
        
        // Build a new DemuxPacket from the AVPacket and return it.
        jclass      dpkt_clazz = env->FindClass("com/tstordyallison/ffmpegmr/DemuxPacket");
        jmethodID   dpkt_ctr = env->GetMethodID(dpkt_clazz, "<init>", "()V");
        jobject     dpkt = env->NewObject(dpkt_clazz, dpkt_ctr);
        
        // Copy the TPL version to the dpkt (and fill in the other dpkt values).
        jfieldID streamID = env->GetFieldID(dpkt_clazz, "streamID", "I");
        jfieldID ts = env->GetFieldID(dpkt_clazz, "ts", "J");
        jfieldID tb_num = env->GetFieldID(dpkt_clazz, "tb_num", "J");
        jfieldID tb_den = env->GetFieldID(dpkt_clazz, "tb_den", "J");
        jfieldID duration = env->GetFieldID(dpkt_clazz, "duration", "J");
        jfieldID splitPoint = env->GetFieldID(dpkt_clazz, "splitPoint", "Z");
        jfieldID data = env->GetFieldID(dpkt_clazz, "data", "[B");
        
        env->SetIntField(dpkt, streamID, output_pkt.stream_index);
        env->SetLongField(dpkt, duration, av_rescale_q(output_pkt.duration, state->encoder->time_base, state->chunk_tb));
        env->SetLongField(dpkt, ts, av_rescale_q(output_pkt.pts, state->encoder->time_base, state->chunk_tb));
        
        env->SetLongField(dpkt, tb_num, state->chunk_tb.num);
        env->SetLongField(dpkt, tb_den, state->chunk_tb.den);
        
        // Set the split point if we forced this to be an I-frame/the packet after an audio split.
        if (state->chunk_points_cursor_out < state->chunk_points_size &&
            output_pkt.pts >= state->chunk_points[state->chunk_points_cursor_out] &&
            state->encoder_frame_count > 1) { // FIXME: This is a massive hack. The reducer/demuxer doesnt 
                                              // know about the expected streams, so we have to *ENSURE* that it 
                                              // always gets every stream. It would usually be valid to not allow this frame
                                              // as it is inaccurate, but we need to ensure the split.
            if(DEBUG)
                fprintf(stderr, "Marking split point: pkt.pts=%lld, chunkpoint=%ld\n", 
                            output_pkt.pts, 
                            state->chunk_points[state->chunk_points_cursor_out]);
            env->SetBooleanField(dpkt, splitPoint, JNI_TRUE);
            state->chunk_points_cursor_out += 1;
        }
        else
            env->SetBooleanField(dpkt, splitPoint, JNI_FALSE);
        
        // Free the packet now we are done with it.
        av_free_packet(&output_pkt); 
        
        // Copy the output data to the JVM.
        jbyteArray dataArray = env->NewByteArray(pkt_tpl_size);
        env->SetByteArrayRegion(dataArray, 0, pkt_tpl_size, (jbyte *)pkt_tpl_data);
        env->SetObjectField(dpkt, data, dataArray);
        free(pkt_tpl_data);
        
        return dpkt;
    }
    else{
        fprintf(stderr, "Warning: failed to find object for a getNextPacket() call.\n");
        return NULL;
    }
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    close
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_close
(JNIEnv *env, jobject obj){
    TranscoderState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Unregister the object state.
        tracker.unregisterObjectState(env, obj);
        
        return 0;
    }
    else
        return -1;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    getStreamData
 * Signature: (I)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_getStreamData
(JNIEnv *env, jobject obj){
    
    TranscoderState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        uint8_t *data = NULL;
        int data_size = 0;
        
        int err = write_avstream_chunk_as_cc_to_memory(state->encoder, state->encoder->time_base, state->input_frame_rate, state->input_aspect_ratio, &data, &data_size);
        
        if(err >= 0)
        {
            jbyteArray dataArray = env->NewByteArray(data_size);
            env->SetByteArrayRegion(dataArray, 0, data_size, (jbyte *)data);
            free(data); 
            return dataArray;
        }
        else{
            throw_new_exception(env, "Error serialising encoder information into header.");
            return NULL;
        }
        
    }
    else{
        throw_new_exception(env, "TranscoderState not found. This method cannot be called before the initWith* method.");
        return NULL;
    }
    
};
