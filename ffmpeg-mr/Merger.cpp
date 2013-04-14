#include <jni.h>
#include "com_tstordyallison_ffmpegmr_Merger.h"
#include "SharedUtil.h"
#include <map>

#define DEBUG 1
#define DEBUG_PRINT 0
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
#ifndef AWSBUILD
#include "libavutil/timestamp.h"
#endif
}

struct RawInputStreamOpaque{
    uint8_t *data;
    size_t length;
    size_t pos;
};

struct OutputStreamOpaque{
    JNIEnv *env;
    jobject obj;
    jobject stream;
};

#define STREAM_BUFFER_SIZE 4096

typedef struct MergerState {
    
    int segment_count;
    
    AVFormatContext *output_format_context;
    
    uint8_t *stream_buffer;
    
    OutputStreamOpaque stream_info;
    
    const char *filename;
    int filename_copy;
    jstring jfilename;
    
    long *last_dts;
    long *last_duration;

    MergerState(){
        this->segment_count = 0;
        
        this->output_format_context = NULL;
        
        this->stream_buffer = (uint8_t *)malloc(STREAM_BUFFER_SIZE);
        
        this->stream_info.env = NULL;
        this->stream_info.obj = NULL;
        this->stream_info.stream = NULL;
        
        this->filename = NULL;
        this->filename_copy = 0;
        this->jfilename = NULL;
    };
    
    ~MergerState(){

    }
    
    
} MergerState;

class MergerTracker {
    
private:
    map<int, MergerState*> objectRegister;
    
public:
    MergerState *getObjectState(JNIEnv *env, jobject obj)
    {
        int hashCode = getHashCode(env, obj);
        if(objectRegister.find(hashCode) != objectRegister.end())
        {
            MergerState *state = objectRegister[hashCode];
            state->stream_info.env = env;
            state->stream_info.obj = obj;
            return state;
        }
        return NULL;
    };
    
    void registerObjectState(JNIEnv *env, jobject obj, MergerState *objstate)
    {
        int hashCode = getHashCode(env, obj);
        objectRegister[hashCode] = objstate;
    };
    
    void unregisterObjectState(JNIEnv *env, jobject obj)
    {
        MergerState *state = getObjectState(env, obj);
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

static MergerTracker tracker;

static bool throwNonZero(int err, const char *msg, JNIEnv *env){
    
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

static int RawBuffer_Read(/*RawInputStreamOpaque*/ void *opaque, uint8_t *buf, int buf_size){
    // This whole thing isn't eactly great from a performance POV, but it will do for now.
    RawInputStreamOpaque *info = (RawInputStreamOpaque *)opaque;
    
    long bytes_to_read = 0;
    long target_pos = info->pos+buf_size;
    
    if(target_pos == info->length)
        bytes_to_read = 0;
    else if(target_pos < info->length)
        bytes_to_read = buf_size;
    else
        bytes_to_read = buf_size - (target_pos - info->length);
    
    bytes_to_read = abs(bytes_to_read);
    
    if(bytes_to_read > 0){
        //if(DEBUG_PRINT_CRAZY) 
            //fprintf(stderr, "Reading: %ld bytes, Current Position: %lu bytes, Remaining: %lu, Remaining after: %lu\n", 
              //              bytes_to_read, info->pos, info->length-info->pos, info->length-(info->pos+bytes_to_read));
        memcpy(buf, &(info->data[info->pos]), bytes_to_read);
        info->pos += bytes_to_read;
        return bytes_to_read;
    }
    else
        return -1;
}

static int64_t RawBuffer_Seek(/*RawInputStreamOpaque*/ void *opaque, int64_t offset, int whence){
    RawInputStreamOpaque *info = (RawInputStreamOpaque *)opaque;
    
    switch (whence) {
        case SEEK_SET: // Offset is relative to the start of the file.
        {
            if(offset < info->length)
                info->pos = offset;
            
            break;
        }
        case SEEK_CUR: // Offset is relative to the current postion.
        {
            if(offset+info->pos < info->length)
                info->pos = offset+info->pos;
            break;
        }
        case SEEK_END: // Offset is relative to the end of the file.
        {
            if(offset+info->length < info->length)
                info->pos = offset+info->length;
            break;
        }
        case AVSEEK_SIZE: // Return the size.
        {
            return info->length;
        }
    }
    
    return info->pos;
}


static int Java_OutputStream_Write(/*OutputStreamOpaque*/ void *jni_output_stream, uint8_t *buf, int buf_size){
    OutputStreamOpaque *info = (OutputStreamOpaque *)jni_output_stream;
    JNIEnv *env = info->env;
    jclass      output_stream_clazz  = env->GetObjectClass(info->stream);
    jmethodID   write                = env->GetMethodID(output_stream_clazz, "write", "([B)V");
    
    jbyteArray dataArray = env->NewByteArray(buf_size);
    env->SetByteArrayRegion(dataArray, 0, buf_size, (jbyte *)buf);
    env->CallVoidMethod(info->stream, write, dataArray);
    
    return buf_size;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Merger
 * Method:    initWithOutputStream
 * Signature: (Lorg/apache/hadoop/fs/FSDataOutputStream;)V
 */
JNIEXPORT void JNICALL Java_com_tstordyallison_ffmpegmr_Merger_initWithOutputStream
(JNIEnv *env, jobject obj, jobject stream){
    // Init some new state.
    MergerState *state = new MergerState;
    
    state->stream_info.env = env;
    state->stream_info.obj = obj;
    state->stream_info.stream = env->NewGlobalRef(stream);

    // Add this state to the register.
    tracker.registerObjectState(env, obj, state);
};

/*
 * Class:     com_tstordyallison_ffmpegmr_Merger
 * Method:    initWithFile
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_tstordyallison_ffmpegmr_Merger_initWithFile
(JNIEnv *env, jobject obj, jstring filename){
    // Init some new state.
    MergerState *state = new MergerState;
    
    state->filename = env->GetStringUTFChars(filename, (jboolean *)&state->filename_copy);
    
    // Add this state to the register.
    tracker.registerObjectState(env, obj, state);
}


static void process_segment(JNIEnv *env, MergerState *state, AVFormatContext *input_format_ctx)
{
    
    // If this is our first segment, copy the stream data into the output format and write our header.
    if(state->segment_count == 0)
    {
        // Set the output format and setup the IO.
        AVOutputFormat *output_format = av_guess_format(NULL, "filename.mkv", NULL); // FIXME!
        
        // Create an output file AVFormatContext and open the file for writing to the filesystem.
        avformat_alloc_output_context2(&state->output_format_context, output_format, NULL, NULL);
        if (!&state->output_format_context) {
            throw_new_exception(env, "Unable to create output context.");
            return;
        }
        
        if(state->stream_info.stream != NULL)
        {
            // Setup the custom IO.
            state->output_format_context->pb = avio_alloc_context(state->stream_buffer, STREAM_BUFFER_SIZE, 1, &state->stream_info, NULL, Java_OutputStream_Write, NULL);
        }
        else
        {
            if (avio_open(&state->output_format_context->pb, state->filename, AVIO_FLAG_WRITE) < 0) {
                fprintf(stderr, "Could not open '%s'\n", state->filename);
                return;
            }
        }
        
        // Init the pts counter.
        state->last_dts = (long *)malloc(sizeof(long *) * input_format_ctx->nb_streams);
        state->last_duration = (long *)malloc(sizeof(long *) * input_format_ctx->nb_streams);
        
        for(int i = 0; i < input_format_ctx->nb_streams; i++)
        {
            AVStream *old_stream = input_format_ctx->streams[i];
            AVStream *new_stream = avformat_new_stream(state->output_format_context, old_stream->codec->codec);
            
            // This is probabaly overkill - I'm just copying everything over to be sure it works.
            new_stream->time_base = old_stream->time_base;
            new_stream->sample_aspect_ratio = old_stream->sample_aspect_ratio;
            new_stream->r_frame_rate = old_stream->r_frame_rate;
            new_stream->codec->flags = old_stream->codec->flags;
            new_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
            new_stream->codec->bits_per_raw_sample = old_stream->codec->bits_per_raw_sample;
            new_stream->codec->chroma_sample_location = old_stream->codec->chroma_sample_location;
            new_stream->codec->codec_id = old_stream->codec->codec_id;
            new_stream->codec->codec_type = old_stream->codec->codec_type;
            new_stream->codec->bit_rate = old_stream->codec->bit_rate;
            new_stream->codec->time_base = old_stream->codec->time_base;
            new_stream->codec->channel_layout = old_stream->codec->channel_layout;
            new_stream->codec->sample_rate =  old_stream->codec->sample_rate;
            new_stream->codec->channels = old_stream->codec->channels;
            new_stream->codec->sample_fmt = old_stream->codec->sample_fmt;
            new_stream->codec->frame_size = old_stream->codec->frame_size;
            new_stream->codec->audio_service_type = old_stream->codec->audio_service_type;
            new_stream->codec->block_align = old_stream->codec->block_align;
            new_stream->codec->pix_fmt = old_stream->codec->pix_fmt;
            new_stream->codec->width = old_stream->codec->width;
            new_stream->codec->height = old_stream->codec->height;
            new_stream->codec->has_b_frames = old_stream->codec->has_b_frames;
            new_stream->codec->sample_aspect_ratio = old_stream->sample_aspect_ratio;
            //new_stream->codec->ticks_per_frame = old_stream->codec->ticks_per_frame;
            new_stream->codec->extradata_size = old_stream->codec->extradata_size;
            new_stream->codec->extradata = (uint8_t *)malloc(old_stream->codec->extradata_size); 
            memcpy(new_stream->codec->extradata, old_stream->codec->extradata, old_stream->codec->extradata_size);
            new_stream->codec->codec_tag = 0;
            
            state->last_dts[i] = 0;
            state->last_duration[i] = 0;
        }
        
        // Write the header to the file now we have our stream info.
        if(throwNonZero(avformat_write_header(state->output_format_context, NULL), "Writing header to file.", env)!=0)
            return;
        
        
        
    }
    
    AVPacket *pkt = (AVPacket *)malloc(sizeof(AVPacket));
    
    // Read and then write each of the frames to the new file.
    while(!av_read_frame(input_format_ctx, pkt))
    {
        if(DEBUG_PRINT_CRAZY){
            fprintf(stderr, "Reading packet to merge for stream %d (dts=%lld, pts=%lld, duration=%d, size=%d)\n", pkt->stream_index, pkt->dts, pkt->pts, pkt->duration, pkt->size);
        }

        if (pkt->dts != AV_NOPTS_VALUE)
            state->last_dts[pkt->stream_index] = pkt->dts = av_rescale_q(pkt->dts, input_format_ctx->streams[pkt->stream_index]->time_base, state->output_format_context->streams[pkt->stream_index]->time_base);
        else
            if (state->last_duration[pkt->stream_index] > 0)
                state->last_dts[pkt->stream_index] = pkt->dts = state->last_dts[pkt->stream_index] + state->last_duration[pkt->stream_index];
        
        if (pkt->pts != AV_NOPTS_VALUE)
            pkt->pts = av_rescale_q(pkt->pts, input_format_ctx->streams[pkt->stream_index]->time_base, state->output_format_context->streams[pkt->stream_index]->time_base);

        if (pkt->duration != AV_NOPTS_VALUE){
            pkt->duration = av_rescale_q(pkt->duration, input_format_ctx->streams[pkt->stream_index]->time_base,  state->output_format_context->streams[pkt->stream_index]->time_base);
            if(pkt->duration > 0)
                state->last_duration[pkt->stream_index] = pkt->duration;
        else
            if(state->last_duration[pkt->stream_index]> 0)
                pkt->duration = state->last_duration[pkt->stream_index];
        }
        
        if(DEBUG_PRINT_CRAZY){
            fprintf(stderr, "Writing packet to merge for stream %d (dts=%lld, pts=%lld, duration=%d, size=%d)\n", pkt->stream_index, pkt->dts, pkt->pts, pkt->duration, pkt->size);
        }
        
        int ret = av_interleaved_write_frame(state->output_format_context, pkt);
        av_free_packet(pkt); av_init_packet(pkt);
        
        if(DEBUG_PRINT && ret != 0){
            fprintf(stderr, "Error writing frame (skippped): ret=%d\n", ret);
        }
    }
    
    av_freep(&pkt);
    
    // Increment the segment counter.
    state->segment_count += 1;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Merger
 * Method:    addSegment
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_tstordyallison_ffmpegmr_Merger_addSegment__Ljava_lang_String_2
(JNIEnv *env, jobject obj, jstring path){
    
    MergerState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Get a copy of the string.
        int filename_copy = 0;
        char *filename = (char *)env->GetStringUTFChars(path, (jboolean *)&filename_copy);
        
        // Set and open the format context and probe.
        AVFormatContext *input_format_ctx = avformat_alloc_context();

        // Open the file.
        if(throwNonZero(avformat_open_input(&input_format_ctx, filename, NULL, NULL), "Opening input chunk.", env)!=0)
            return;
        
        // Probe the streams.
        avformat_find_stream_info(input_format_ctx, NULL);
        
        // Process the segment.
        process_segment(env, state, input_format_ctx);
        
        // Dealloc the input data and close the AVIOContext.
        avformat_close_input(&input_format_ctx);
        
        // Dealloc the JNI string.
        if(filename_copy)
            env->ReleaseStringUTFChars(path, filename);
    }
    else
        throw_new_exception(env, "Merger not available.");
    
    
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Merger
 * Method:    addSegment
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_com_tstordyallison_ffmpegmr_Merger_addSegment___3BJJ
(JNIEnv *env, jobject obj, jbyteArray data, jlong off, jlong len){
    MergerState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Copy over the data using JNI and delete the local ref.
        size_t data_size = env->GetArrayLength(data);
        if(off+len > data_size){
            throw_new_exception(env, "Invalid off+len");
            return;
        }
        
        uint8_t *data_raw = (uint8_t *)malloc(sizeof(jbyte) * len);
        env->GetByteArrayRegion(data, off, (jint)len, (jbyte *)data_raw);
        env->DeleteLocalRef(data);
        
        // Setup a format context to read this file segment using custom RawBuffer* functions.
        RawInputStreamOpaque io_info = (RawInputStreamOpaque){data_raw, len, 0};
        uint8_t *raw_buffer = (uint8_t *)malloc((STREAM_BUFFER_SIZE + FF_INPUT_BUFFER_PADDING_SIZE) + sizeof(uint8_t));
        AVIOContext *input_stream = avio_alloc_context(raw_buffer, STREAM_BUFFER_SIZE, 0, &io_info, RawBuffer_Read, NULL, RawBuffer_Seek);
        
        // Set and open the format context and probe.
        AVFormatContext *input_format_ctx = avformat_alloc_context();
        input_format_ctx->pb = input_stream;
        if(throwNonZero(avformat_open_input(&input_format_ctx, "", NULL, NULL), "Opening input chunk.", env)!=0)
            return;
        
        // Probe the streams.
        avformat_find_stream_info(input_format_ctx, NULL);
    
        // Process the segment.
        process_segment(env, state, input_format_ctx);
        
        // Dealloc the input data and close the AVIOContext.
        av_free(input_format_ctx->pb);
        avformat_close_input(&input_format_ctx);
        av_free(data_raw);
        
    }
    else
        throw_new_exception(env, "Merger not available.");
};

/*
 * Class:     com_tstordyallison_ffmpegmr_Merger
 * Method:    closeOutput
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_tstordyallison_ffmpegmr_Merger_closeOutput
(JNIEnv *env, jobject obj){
    MergerState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        if(state->segment_count > 0)
        {
            // Write the trailer for the file.
            av_write_trailer(state->output_format_context);
            
            // Dealloc any state.
            //avio_close(state->output_format_context->pb);
            avformat_close_input(&state->output_format_context);
            
            free(state->last_dts);
            free(state->last_duration);
        }
        else
        {
            fprintf(stderr, "WARNING: No segments processed.\n");
        }
        
        // TODO: Set the closed boolean to true on the calling object.
        
        // Remove the stream ref
        if(state->stream_info.stream != NULL)
            env->DeleteGlobalRef(state->stream_info.stream);
        
        // Relase the filename
        if(state->filename != NULL)
        {
            if(state->filename_copy)
                env->ReleaseStringUTFChars(state->jfilename, state->filename);
        }
        
        // Remove the object from the tracker.
        tracker.unregisterObjectState(env, obj);
        
    }
    else
        throw_new_exception(env, "Merger not available.");

};


