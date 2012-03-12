#include <jni.h>
#include "com_tstordyallison_ffmpegmr_Transcoder.h"
#include "SharedUtil.h"
#include <map>

#define DEBUG 1
#define DEBUG_PRINT 1

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
}

/*
 * Abstraction of an 'Audio frame' - a number of samples of raw audio that we can use to pass around between functions.
 */
typedef struct AVAudioFrame
{
    int16_t *data;
    int data_size;
    
    enum AVSampleFormat sample_fmt;
    int channels;
    int sample_rate;
    int pts;
    
    AVAudioFrame(AVCodecContext *decoder)
    {
        this->data = (int16_t *)malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
        this->data_size = AVCODEC_MAX_AUDIO_FRAME_SIZE;
        this->sample_fmt = decoder->sample_fmt;
        this->channels = decoder->channels;
        this->sample_rate = decoder->sample_rate;
    }
    
    ~AVAudioFrame()
    {
        if(data != NULL)
            free(data);
    }
    
} AVAudioFrame;


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
    
    AVRational input_tb;
    AVRational input_frame_rate;

    AVCodecContext  *decoder;
    AVCodecContext  *encoder;
    
    int encoder_frame_count;
    int decoder_frame_count;
    
    int64_t output_pts;
    
    AVPacket *input_packet;
    
    AVFrame *raw_video; // A decoded picture.
    AVFrame *raw_audio; // A decoded number of audio samples
    
    AVFifoBuffer *fifo; // Circular buffer for storing audio samples and adjusting their size for the encoder. 

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
        
        input_tb = (AVRational){0,1};
        
        decoder = NULL;
        encoder = NULL;
        
        encoder_frame_count = 0;
        decoder_frame_count = 0;
        
        output_pts = 0;
        
        input_packet = (AVPacket *)malloc(sizeof(AVPacket));
        av_init_packet(this->input_packet);
        
        raw_audio = avcodec_alloc_frame(); avcodec_get_frame_defaults(raw_audio);
        raw_video = avcodec_alloc_frame(); avcodec_get_frame_defaults(raw_video);
        
        fifo = av_fifo_alloc(1024);
    };
    
    ~TranscoderState(){
        // Free some stuff.
        if(data != NULL)
        {
            free(data); // Allocated by JNI.
            data = NULL;
        }
        
        if(image_list != NULL)
        {
            free(image_list); // Allocated by ffmpeg_tpl.c
            image_list = NULL;
        }
        
        if(chunk_points != NULL)
        {
            free(chunk_points); // Allocated by JNI.
            chunk_points = NULL;
        }
        
        if(decoder != NULL)
        {
            if(this->decoder->extradata)
                free(this->decoder->extradata);
            avcodec_close(decoder);
            av_free(decoder);
            decoder = NULL;
        }
        
        if(encoder != NULL)
        {
            if(this->encoder->extradata)
                free(this->encoder->extradata);
            avcodec_close(encoder);
            av_free(encoder);
            encoder = NULL;
        }
        
        if(raw_audio != NULL)
        {
            // TODO: free internal frame contents?
            av_free(raw_audio);
            raw_audio = NULL;
        }
        
        if(raw_video != NULL)
        {
            // TODO: free internal frame contents?
            av_free(raw_video);
            raw_video = NULL;
        }
        
        if(input_packet)
            av_free_packet(input_packet);
        
        if(fifo)
            av_fifo_free(fifo);
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


/*
 * Performs an image resample.
 * Takes the input AVFrame with the data, and an output AVFrame with no data but the desired settings.
 */
static void video_resample(AVFrame *input_frame, AVFrame *output_frame)
{

}


/*
 * Performs an audio resample.
 * Takes the input AVAudioFrame with the data, and an output AVAudioFrame with no data but the desired settings.
 */
static void audio_resample(AVAudioFrame *input_frame, AVAudioFrame *output_frame)
{
    
}


// JNI Methods.
// --------

static void initWithBytes_tidy(TranscoderState *state, int err)
{
    // Deallocs
    // JNI strings?
    // JNI memory regions?
}

static bool initWithBytesThrowNonZero(int err, const char *msg, TranscoderState *state, JNIEnv *env){
    
    // Do error catch.
    if(err != 0)
    {
        initWithBytes_tidy(state, err);
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

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    initWithBytes
 * Signature: ([J[B)I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_initWithBytes
(JNIEnv *env, jobject obj, jlongArray chunk_points, jbyteArray data){
    
    // Init state;
    int err = 0;
    TranscoderState *state = new TranscoderState;
    
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
    
    // Go through the data and identify the TPL images that we are going to read.
    tpl_gather_image_list(state->data, state->data_size, &(state->image_list), &(state->image_list_size));
    
    // Get the decoder info from the chunk header.
    TPLImageRef *header = &(state->image_list[state->image_cursor]);
    if((err = read_avstream_chunk_as_cc_from_memory(header->data, header->size, &(state->decoder), &(state->input_tb), &(state->input_frame_rate))) != 0)
    {
        initWithBytes_tidy(state, err);
        throw_new_exception(env, "Init failed - reading header TPL image from data.");
        return err;
    }
    state->image_cursor += 1;
    
    // Convert the chunkpoint timestamps to the same as the time base for the chunk.
    for(int i = 0; i < state->chunk_points_size; i++)
    {   
        if(DEBUG_PRINT)
        {
            fprintf(stderr, "Rescaling TS: %ld from %d/%d to %d/%d = ", state->chunk_points[i], TS_BASE.num, TS_BASE.den, state->input_tb.num, state->input_tb.den);
            
        }
        state->chunk_points[i] = av_rescale_q(state->chunk_points[i], TS_BASE, state->input_tb);
        if(DEBUG_PRINT)
        {
            fprintf(stderr, "%ld\n", state->chunk_points[i]);
            
        }
    }
    
    // Setup the decoder (we just get raw data from the TPL read).
    AVCodec *decoder_codec = avcodec_find_decoder(state->decoder->codec_id);
    if(decoder_codec != NULL)
    {
        AVDictionary *copts = NULL;
        if(initWithBytesThrowNonZero(avcodec_open2(state->decoder, decoder_codec, &copts), "Failed to open decoder codec.", state, env)) return -1;
    }
    else
    {
        initWithBytes_tidy(state, -1);
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
                
                state->encoder->gop_size = 250;
                state->encoder->keyint_min = 2;
                state->encoder->max_b_frames = 16;
                //state->encoder->bit_rate = 2048000;
                av_dict_set(&copts, "crf", "20", 0);
                
                state->encoder->width = state->decoder->width;
                state->encoder->height = state->decoder->height;
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
                initWithBytes_tidy(state, -1);
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
                
                state->encoder->bit_rate = 64000; // 64kbit audio.
                state->encoder->sample_fmt = state->decoder->sample_fmt;
                state->encoder->sample_rate = state->decoder->sample_rate;
                state->encoder->channels = state->decoder->channels;
                
                if(initWithBytesThrowNonZero(avcodec_open2(state->encoder, encoder_codec, &copts), "Error opening encoder codec.", state, env)) return -1;
            }
            else
            {
                initWithBytes_tidy(state, -1);
                throw_new_exception(env, "Init failed - loading encoder AAC.");
                return err;
            }
            
            break;
        }
        default:
            initWithBytes_tidy(state, -1);
            throw_new_exception(env, "Init failed - invalid stream. Audio and video only.");
            return err;
    }
    
    // All done, add it to the register. 
    tracker.registerObjectState(env, obj, state);
    initWithBytes_tidy(state, err);
    return 0;
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
        fprintf(stderr, "Number of packets:  %lu\n", state->image_list_size-1);
        fprintf(stderr, "Number of frames input:  %d\n", state->decoder_frame_count);
        fprintf(stderr, "Number of frames output: %d\n", state->encoder_frame_count);
    }
}

static int read_avpacket(TranscoderState *state)
{
    // Get the next packet from the data stream.
    int err = 0;
    if(state->image_cursor + 1 != state->image_list_size)
    {
        TPLImageRef *packet = &(state->image_list[state->image_cursor]);
        err = read_avpacket_chunk_from_memory(packet->data, packet->size, state->input_packet);
        
        if(err == 0){
            state->image_cursor += 1;
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
                        state->input_packet->data = NULL;
                        state->input_packet->size = 0;
                    }
                    else
                        return -1;
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
                if(ret >= 0)
                {
                    if(!got_picture && (state->decoder->codec->capabilities & CODEC_CAP_DELAY) && state->input_packet->data == NULL && state->input_packet->size == 0)
                        return -1; // End of the stream.
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
            
            // Counter.
            state->decoder_frame_count += 1;
            
            // If there is no PTS, calculate it ourselves.
            if(state->raw_video->pts == AV_NOPTS_VALUE)
                state->raw_video->pts = state->raw_video->pkt_pts;
            if(state->raw_video->pts == AV_NOPTS_VALUE)
                state->raw_video->pts = state->decoder->reordered_opaque;
            
            // Print the frame details
            if(DEBUG_PRINT)
                fprintf(stderr, "Decoded frame: pts=%lld, type=%d\n", state->raw_video->pts, state->raw_video->pict_type);
            
            break;
        }
        case AVMEDIA_TYPE_AUDIO:
        {

            // Audio is segmented into frames that are <= encoder->frame_size, so we might not always ask for new packet. 
            // If the fifo queue still has some data in it that we can encode, we pass that to the caller instead. 
            // Otherwise we get a new packet from the TPL images. 
            
            int frame_bytes = enc->frame_size * av_get_bytes_per_sample(enc->sample_fmt) * enc->channels;
            
            if (!(enc->codec->capabilities & CODEC_CAP_VARIABLE_FRAME_SIZE) && 
                av_fifo_size(state->fifo) >= frame_bytes)
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
                
                // Calculate the number of samples.
                frame->nb_samples  = frame_bytes / (enc->channels * av_get_bytes_per_sample(enc->sample_fmt));
                avcodec_fill_audio_frame(frame, enc->channels, enc->sample_fmt, audio_buf, frame_bytes, 1);
                
                // Increment out pts counter. 
                state->output_pts += frame->nb_samples;
                
            }
            else
            {
                // Do a normal read from the TPL images and decode the packet into the fifo or state->raw_audio.
                int got_samples = 0;
                AVFrame *decoded_frame = avcodec_alloc_frame(); avcodec_get_frame_defaults(decoded_frame);
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
                        int fifo_bytes = av_fifo_size(state->fifo);;
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
                            
                            // Calculate the number of samples.
                            frame->nb_samples  = frame_bytes / (enc->channels * av_get_bytes_per_sample(enc->sample_fmt));
                            avcodec_fill_audio_frame(frame, enc->channels, enc->sample_fmt, audio_buf, frame_bytes, 1);
                            
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
                                return -1;
                            
                        }
                    }
                    
                    // Store away the PTS value incase FFmpeg tries to lose it.
                    state->decoder->reordered_opaque = state->input_packet->pts;
                    ret = avcodec_decode_audio4(state->decoder, decoded_frame, &got_samples, state->input_packet);
                    
                    // Act on the return value.
                    if(ret >= 0)
                    {
                        if(!got_samples && (state->decoder->codec->capabilities & CODEC_CAP_DELAY) && state->input_packet->data == NULL && state->input_packet->size == 0)
                        {
                            return -1; // End of the stream.
                        }
                        
                        // Counter.
                        state->decoder_frame_count += 1;
                        
                        
                        // Eietehr queue up the frames, or set state->raw_audio.
                        if (!(enc->codec->capabilities & CODEC_CAP_VARIABLE_FRAME_SIZE)){
                            // Raw input buffers.
                            uint8_t *input_audio_buf    = decoded_frame->data[0];
                            int      input_audio_size   = decoded_frame->nb_samples * dec->channels * av_get_bytes_per_sample(enc->sample_fmt);
                            
                            // Place on the fifo queue.
                            av_fifo_realloc2(state->fifo, av_fifo_size(state->fifo) + input_audio_size);
                            av_fifo_generic_write(state->fifo, input_audio_buf, input_audio_size, NULL);
                            
                            // This pulls the next frame from the fifo queue.
                            decode_packet(env, state);
                        }
                        else
                        {
                            // The codec has a fixed frame size, so don't use the queue.
                            state->raw_audio = decoded_frame;
                            
                            // Sort out some time issues.
                            if(state->raw_audio->pts == AV_NOPTS_VALUE)
                                state->raw_audio->pts = state->raw_audio->pkt_pts;
                            if(state->raw_audio->pts == AV_NOPTS_VALUE)
                                state->raw_audio->pts = state->decoder->reordered_opaque;
                        }
                    }
                    else{
                        throw_new_exception(env, "Read failed - decoder failed to decode audio packet.");
                        return -1;
                    }

                };
                
            }
            
            break;
        }
        default:
        {
            // This would be an invalid stream.
            return -1;
        }
            
    }
    
    return 0;
}


static void do_audio_out(TranscoderState *state, AVFrame *decoded_frame)
{
    // Shorthand.
    AVCodecContext *enc = state->encoder;
    AVCodecContext *dec = state->decoder;
    
    // Per sample input and output sizes.
    int osize = av_get_bytes_per_sample(enc->sample_fmt);
    int isize = av_get_bytes_per_sample(dec->sample_fmt);
    
    // Raw input buffers.
    uint8_t *input_audio_buf    = decoded_frame->data[0];
    int      input_audio_size   = decoded_frame->nb_samples * dec->channels * isize;
    
    int frame_bytes;
         
    // Put the input frame into a circular buffer, and read out the frame_size chunks for the encoder.
    // Place each chunk into state->audio_buf. 
    if (!(enc->codec->capabilities & CODEC_CAP_VARIABLE_FRAME_SIZE)) {
        av_fifo_realloc2(state->fifo, av_fifo_size(state->fifo) + input_audio_size);
        av_fifo_generic_write(state->fifo, input_audio_buf, input_audio_size, NULL);
        
        frame_bytes = enc->frame_size * osize * enc->channels;
        
        while (av_fifo_size(state->fifo) >= frame_bytes) {
            //av_fifo_generic_read(state->fifo, state->audio_buf, frame_bytes, NULL);
            // Encode this mini frame.
        }
    } else {
        // Encode normally. 
    }
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
                
                return NULL;
                
                // Check out the ts to see if we are on a defined chunkpoint (a forced keyframe). 
                // If we are instruct the encoder to output an I-Frame;
                if(state->raw_video)
                    if (state->chunk_points_cursor_enc < state->chunk_points_size &&
                        state->raw_video->pts >= state->chunk_points[state->chunk_points_cursor_enc]) {
                        state->raw_video->pict_type = AV_PICTURE_TYPE_I;
                        state->chunk_points_cursor_enc += 1;
                        if(DEBUG)
                            fprintf(stderr, "Chunk point marked with I frame at %lld.\n", state->raw_video->pts);
                    };                
                
                int got_pkt = 0;
                while(!got_pkt)
                {
                    int ret = 0;
                    
                    // Advance the decoder/pull the next frame through.
                    if(decode_packet(env, state) != 0){
                        if(state->encoder->codec->capabilities & CODEC_CAP_DELAY)
                            state->raw_video = NULL;
                        else
                        {
                            getNextPacket_tidy(state, 0);
                            return NULL;
                        }
                    }
                    
                    // Rescale the PTS to the encoder time_base.
                    if(state->raw_video)
                        state->raw_video->pts = av_rescale_q(state->raw_video->pts, state->input_tb, state->encoder->time_base);
                    
                    // Encode the new frame.
                    ret = avcodec_encode_video2(state->encoder, &output_pkt, state->raw_video, &got_pkt);
                
                    // Act on the return value.
                    if(ret == 0){
                        if(got_pkt)
                        {
                            state->encoder_frame_count += 1;
                            
                            if(state->encoder->coded_frame->pts != AV_NOPTS_VALUE)
                                output_pkt.pts = state->encoder->coded_frame->pts;
                            
                            if(state->encoder->coded_frame->key_frame)
                                output_pkt.flags |= AV_PKT_FLAG_KEY;
                            
                            if(DEBUG_PRINT)
                                fprintf(stderr, "Encoded frame: pts=%lld, type=%s\n", output_pkt.pts, state->encoder->coded_frame->key_frame ? "intra" : "inter");
                            
                            // TODO: Duration?
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
                            state->raw_audio = NULL;
                        else
                        {
                            getNextPacket_tidy(state, 0);
                            return NULL;
                        }
                    }
                    
                    // Encode the new frame.
                    ret = avcodec_encode_audio2(state->encoder, &output_pkt, state->raw_audio, &got_pkt);
                    
                    if(ret == 0){
                        if(got_pkt)
                        {
                            state->encoder_frame_count += 1;
                            
                            if(state->encoder->coded_frame->pts != AV_NOPTS_VALUE)
                                output_pkt.pts = state->encoder->coded_frame->pts;
                            
                            if(state->encoder->coded_frame->key_frame)
                                output_pkt.flags |= AV_PKT_FLAG_KEY;
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
                    if(state->encoder_frame_count != 0 && (state->encoder_frame_count == 1 || state->encoder_frame_count % 1000 == 0 || state->encoder_frame_count == state->image_list_size-1) )
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
        jfieldID duration = env->GetFieldID(dpkt_clazz, "duration", "J");
        jfieldID splitPoint = env->GetFieldID(dpkt_clazz, "splitPoint", "Z");
        jfieldID data = env->GetFieldID(dpkt_clazz, "data", "[B");
        
        env->SetIntField(dpkt, streamID, output_pkt.stream_index);
        env->SetLongField(dpkt, duration, av_rescale_q(output_pkt.duration, state->encoder->time_base, TS_BASE));
        env->SetLongField(dpkt, ts, av_rescale_q(output_pkt.pts, state->encoder->time_base, TS_BASE));
        
        // Set the split point if we forced this to be an I-frame.
        if (state->chunk_points_cursor_out < state->chunk_points_size &&
            output_pkt.pts >= state->chunk_points[state->chunk_points_cursor_out]) {
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
