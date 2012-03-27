#include <jni.h>
#include "com_tstordyallison_ffmpegmr_Demuxer.h"
#include "SharedUtil.h"
#include <map>
#include <algorithm>
#include <cstdlib>

using namespace std;

extern "C" {
    #include "ffmpeg_tpl.h"
    #include "libavcodec/avcodec.h"
    #include "libavutil/mathematics.h"
    #include "libavutil/imgutils.h"
    #include "libavutil/dict.h"
    #include "libavformat/avformat.h"
}


#define STREAM_BUFFER_SIZE 4096
#define DEBUG_PRINT_CRAZY 0

struct InputStreamOpaque{
    JNIEnv *env;
    jobject obj;
    jobject stream;
    long length;
};

struct DemuxState {
    AVFormatContext *fmt_ctx;
    AVPacket pkt;
    
    int     stream_count;
    uint8_t **stream_data;
    int     *stream_data_sizes;
    
    jclass  dpkt_clazz;
    jmethodID dpkt_ctr;
    AVRational *common_tb;
    int tb_lcm;
    
    int stream_io; 
    uint8_t *stream_buffer;
    InputStreamOpaque stream_info;
   
    long pts_offset;
    long pts_last;
    
    DemuxState(){
        this->fmt_ctx = NULL;
        av_init_packet(&pkt);
        
        this->stream_count = -1;
        this->stream_data = NULL;
        this->stream_data_sizes = NULL;
        
        this->dpkt_clazz = NULL;
        this->dpkt_ctr = NULL;
        this->common_tb = NULL;
        this->tb_lcm = -1;
        this->pts_last = 0;
        
        this->stream_io = 0;
        this->stream_buffer = NULL;
        this->stream_info.stream = NULL;
    }
};

class DemuxTracker {
    
private:
    map<int, DemuxState*> objectRegister; // Object register.
    
public:
    DemuxState *getObjectState(JNIEnv *env, jobject obj)
    {
        int hashCode = getHashCode(env, obj);
        if(objectRegister.find(hashCode) != objectRegister.end())
        {
            DemuxState *state = objectRegister[hashCode];
            state->stream_info.env = env;
            state->stream_info.obj = obj;
            return state;
        }
        return NULL;
    };
    
    void registerObjectState(JNIEnv *env, jobject obj, DemuxState *objstate)
    {
        int hashCode = getHashCode(env, obj);
        objectRegister[hashCode] = objstate;
    };
    
    void unregisterObjectState(JNIEnv *env, jobject obj)
    {
        DemuxState *state = getObjectState(env, obj);
        if(state != NULL)
        {
            
            // Free up the streams data.
            if(state->stream_data != NULL)
            {
                for(int i = 0; i < state->stream_count; i++)
                {
                    if(state->stream_data[i] != NULL)
                        free(state->stream_data[i]); // free as these came from tpl.
                }
            }
            delete[] state->stream_data;
            
            // Free the stream size data.
            if(state->stream_data_sizes != NULL)
                delete[] state->stream_data_sizes;
            
            // Free streaming state.
            if(state->stream_io)
            {
                // IO Context.
                if(state->stream_buffer != NULL){
                    av_free(state->fmt_ctx->pb);
                }
                
                // Remove the stream ref
                if(state->stream_info.stream != NULL)
                    env->DeleteGlobalRef(state->stream_info.stream); 
            }
            
            // Close the format context
            if(state->fmt_ctx != NULL)
                avformat_close_input(&state->fmt_ctx);
            
            // Remove the state from the map.
            int hashCode = getHashCode(env, obj);
            objectRegister.erase(hashCode);
            
            // Free the state.
            delete state;
        }
    };
};

static DemuxTracker tracker;

/*
 * Set the state->fmt_ctx before calling this.
 *
 */
static int init_demux(JNIEnv *env, jobject obj, DemuxState *state){
    
    int err = 0;
    
    // Bind a decoder to each input stream, and generate the TPL stream headers.
    state->stream_count      = state->fmt_ctx->nb_streams;
    state->stream_data       = new uint8_t*[state->stream_count]; // list of lists
    state->stream_data_sizes = new int[state->stream_count];
    state->common_tb         = new AVRational[state->stream_count];
    
    // Go thorugh each of the codec so we can bin the ones we don't support.
    for (int i = 0; i < state->fmt_ctx->nb_streams; i++) {
        AVStream *stream = state->fmt_ctx->streams[i];
        AVCodec *codec;
        
        if (!(codec = avcodec_find_decoder(stream->codec->codec_id))) {
            fprintf(stderr, "Warning: Unsupported codec with id %d for input stream %d\n", stream->codec->codec_id, stream->index);
            state->stream_data[stream->index] = NULL;
            state->stream_data_sizes[stream->index] = 0;
            state->common_tb[i].num = -1;
            state->common_tb[i].den = -1;
            continue;
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n", stream->index);
            err = -1;
            return -1;
        }
        
        // Generate the TPL stream headers.
        err = write_avstream_chunk_to_memory(stream, &((state->stream_data)[i]), &((state->stream_data_sizes)[i]));
        
        // Add to the common output timebase.
        if(codec->type == AVMEDIA_TYPE_VIDEO){
            fprintf(stderr, "Packet  (%d) tb: %d/%d (1/=%2.2f)\n", i, stream->time_base.num, stream->time_base.den, (float)stream->time_base.den/stream->time_base.num);
            state->common_tb[i] = stream->time_base;
        }
        if(codec->type == AVMEDIA_TYPE_AUDIO){
            fprintf(stderr, "Packet  (%d) tb: %d/%d (1/=%2.2f)\n", i, stream->codec->time_base.num, stream->codec->time_base.den, (float)stream->codec->time_base.den/stream->codec->time_base.num);
            state->common_tb[i] = stream->codec->time_base;
        }
    }
    
    
    // Use the least common multiple algorithm to adjust the timebases to have the same denominator.
    int *denoms = NULL;
    int denoms_size = 0;
    
    for (int i = 0; i < state->stream_count; i++){
        if(state->common_tb[i].den > 0){
            // Check to see if this value is already there.
            int contains = 0;
            for (int j = 0; j < denoms_size; j++){
                if(denoms[j] == state->common_tb[i].den)
                {
                    contains = 1;
                    break;
                }
            }
            
            if(!contains) {
                denoms_size += 1;
                denoms = (int *)realloc(denoms, sizeof(int) * denoms_size);
                denoms[i] = state->common_tb[i].den;
            }
        }
    }
    
    int lcm = state->tb_lcm = lcmf(denoms, denoms_size);
    
    // Set the new den and num for each of the tbs.
    for (int i = 0; i < state->stream_count; i++){
        if(state->common_tb[i].den > 0)
        {
            if(lcm > state->common_tb[i].den) {
                int mul = lcm / state->common_tb[i].den;
                state->common_tb[i].num *= mul;
                state->common_tb[i].den = lcm;
            }
            else if(lcm < state->common_tb[i].den)
            {
                // This usually means we've had an overflow and the time bases are too big.
                // FIXME: As a bit of a hack we will just return MICROSECONDS.
                int mul = (AV_TIME_BASE * 1000) / state->common_tb[i].den;
                state->common_tb[i].num *= mul;
                state->common_tb[i].den = AV_TIME_BASE * 1000;
            }
            
            if(DEBUG)
                fprintf(stderr, "Modified tb (%d): %d/%d (1/=%2.2f)\n", i, state->common_tb[i].num, state->common_tb[i].den, (float)state->common_tb[i].den/state->common_tb[i].num);
            
        }
        
    }
    
    free(denoms);
    
    // Init the packet storage.
    av_init_packet(&state->pkt);
    
    // Do some once only init on the dpkt object.
    state->dpkt_clazz = env->FindClass("com/tstordyallison/ffmpegmr/DemuxPacket");
    if(state->dpkt_clazz == NULL) 
    {
        fprintf(stderr, "Could not find the com/tstordyallison/ffmpegmr/DemuxPacket class in the JVM.\n");
        err = -1;
        goto failure;
    }
    
    state->dpkt_ctr = env->GetMethodID(state->dpkt_clazz, "<init>", "()V");
    
    
failure:
    // Either error, or return and register.
    if(err != 0)
    {
        return err;
    }
    else
    {
        tracker.registerObjectState(env, obj, state);
        return 0;
    }

    
}


// JNI Methods.
// --------

/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    initDemuxWithFile
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_initDemuxWithFile
(JNIEnv *env, jobject obj, jstring jfilename){

    // Local vars.
    int err = 0;
    int filename_copy;
    const char *filename;
    
    // Init state and add them to the object register.
    DemuxState *state = new DemuxState; 
    
    // Open the file for reading.
    filename = env->GetStringUTFChars(jfilename, (jboolean *)&filename_copy);
    if ((err = avformat_open_input(&state->fmt_ctx, filename, NULL, NULL)) < 0) {
        print_file_error(filename, err);
        return -1;
    }
    
    // Fill the streams in the format context
    if ((err = avformat_find_stream_info(state->fmt_ctx, NULL)) < 0) {
        printf("Failed to open streams in file %s, error %d\n", filename, err);
        return -1;
    }
    
    // Run the actual init.
    int ret = init_demux(env, obj, state);
    
    // Deallocs
    if(filename_copy == JNI_TRUE)
        env->ReleaseStringUTFChars(jfilename, filename);
    
    return ret;
}

/*
 * FFmpeg custom IO read function - for use with JNI InputStream.
 */
static int Java_InputStream_Read(/*InputStreamOpaque*/ void *jni_input_stream, uint8_t *buf, int buf_size){
    InputStreamOpaque *info = (InputStreamOpaque *)jni_input_stream;
    JNIEnv *env = info->env;
    jclass      inputstream_clazz   = env->GetObjectClass(info->stream);
    jmethodID   read                = env->GetMethodID(inputstream_clazz, "read", "([B)I");
    
    jbyteArray dataArray = env->NewByteArray(buf_size);
    int bytes_read = env->CallIntMethod(info->stream, read, dataArray);
    
    if(bytes_read > 0 && !env->ExceptionCheck()){
        env->GetByteArrayRegion(dataArray, 0, bytes_read, (jbyte *)buf);
        env->DeleteLocalRef(dataArray);
        return bytes_read;
    }
    else
        return -1;
}

static int64_t Java_InputStream_Seek(/*InputStreamOpaque*/ void *jni_input_stream, int64_t offset, int whence)
{
    InputStreamOpaque *info = (InputStreamOpaque *)jni_input_stream;
    JNIEnv *env = info->env;
    jclass inputstream_clazz = env->GetObjectClass(info->stream);
    jmethodID seek = env->GetMethodID(inputstream_clazz, "seek", "(J)V");
    jmethodID getpos = env->GetMethodID(inputstream_clazz, "getPos", "()J");
    
    switch (whence) {
        case SEEK_SET: // Offset is relative to the start of the file.
        {
            if(DEBUG_PRINT_CRAZY)
                fprintf(stderr, "Seeking to (SEEK_SET): %lld\n", offset);
            if(offset < info->length)
                env->CallVoidMethod(info->stream, seek, offset);
            break;
        }
        case SEEK_CUR: // Offset is relative to the current postion.
        {
            long current_pos = env->CallLongMethod(info->stream, getpos);
            if(DEBUG_PRINT_CRAZY)
                fprintf(stderr, "Seeking to (SEEK_CUR): %lld\n", offset+current_pos);
            if(offset+current_pos < info->length)
                env->CallVoidMethod(info->stream, seek, offset+current_pos);
            break;
        }
        case SEEK_END: // Offset is relative to the end of the file.
        {
            if(DEBUG_PRINT_CRAZY)
                fprintf(stderr, "Seeking to (SEEK_END): %lld\n", offset+info->length);
            if(offset+info->length < info->length)
                env->CallVoidMethod(info->stream, seek, offset+info->length);
            break;
        }
        case AVSEEK_SIZE: // Return the size.
        {
            return info->length;
        }
    }

    return env->CallLongMethod(info->stream, getpos);
}


/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    initDemuxWithStream
 * Signature: (Ljava/io/InputStream;)V
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_initDemuxWithStream
(JNIEnv *env, jobject obj, jobject stream, jlong length){

    // Init state and add them to the object register.
    int err = 0;
    DemuxState *state = new DemuxState; 
    state->stream_io = 1;
    
    // Ask the JVM to give us a long reference to this Stream.
    jobject stream_global = env->NewGlobalRef(stream);
    
    // Setup the IO context and init the format context.
    state->stream_buffer = (uint8_t*)malloc(STREAM_BUFFER_SIZE); 
    state->stream_info = (InputStreamOpaque){env, obj, stream_global, (long)length};
    AVIOContext *input_stream = avio_alloc_context(state->stream_buffer, STREAM_BUFFER_SIZE, 0, &state->stream_info, Java_InputStream_Read, NULL, Java_InputStream_Seek);
    
    // Set and open the format context.
    state->fmt_ctx = avformat_alloc_context();
    state->fmt_ctx->pb = input_stream;
    
    if ((err = avformat_open_input(&state->fmt_ctx, "", NULL, NULL)) < 0) {
        print_file_error("Java stream read error:", err);
        return -1;
    }

    // Do some more probing.
    if ((err = avformat_find_stream_info(state->fmt_ctx, NULL)) < 0) {
        printf("Failed to open streams in java input stream, error %d\n",  err);
        return -1;
    }
    
    // Run the actual init.
    int ret = init_demux(env, obj, state);

    // TODO: Clean up?    
    return ret;
}


/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    getMediaType
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getStreamMediaTypeRaw
(JNIEnv *env, jobject obj, jint streamID){
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        return state->fmt_ctx->streams[(int)streamID]->codec->codec_type;
    }
    else
        return -1;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    getStreamData
 * Signature: (I)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getStreamData
(JNIEnv * env, jobject obj, jint stream_idx){
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        jbyteArray output = env->NewByteArray(state->stream_data_sizes[stream_idx]);
        env->SetByteArrayRegion(output, 0, state->stream_data_sizes[stream_idx], (jbyte*)(state->stream_data[stream_idx]));
        return output;
    }
    else
        return NULL;
}



/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    getStreamCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getStreamCount
(JNIEnv * env, jobject obj){
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        return state->stream_count;
    }
    else
        return -1;
}


/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    getDurationMs
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getDurationMs
(JNIEnv *env, jobject obj){
    
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        return av_rescale_q(state->fmt_ctx->duration, AV_TIME_BASE_Q, (AVRational){1, 1000});
    }
    else
        return -1;
}



/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    getNextChunk
 * Signature: ()Lcom/tstordyallison/ffmpegmr/Demuxer/DemuxPacket;
 *
 * Hopefully this method won't slow the whole thing down too much. If it does, we are screwed.
 */
JNIEXPORT jobject JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getNextChunkImpl
(JNIEnv * env, jobject obj){
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Read the next AVPacket from the input.
        int ret = 0;
        ret = av_read_frame(state->fmt_ctx, &state->pkt);
        if(ret != 0) return NULL;
        
        // Skip over any invalid streams/ones that appeared out of the blue (this can sometime be subtitle etc).
        while(state->pkt.stream_index >= state->stream_count || state->stream_data_sizes[state->pkt.stream_index] == 0){
            ret = av_read_frame(state->fmt_ctx, &state->pkt);
            if(ret != 0) return NULL;
        }
        
        // Make sure that this isn't a VOB that has reset its TS back to 0.
        //if(pts_last < 
        
        // Temp for the AVPacket TPL.
        uint8_t *pkt_tpl_data;
        int pkt_tpl_size;                               
        
        // Generate the TPL.
        write_avpacket_chunk_to_memory(&state->pkt, &pkt_tpl_data, &pkt_tpl_size);
                                       
        // Create the DemuxPacket object.
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

        env->SetIntField(dpkt, streamID, state->pkt.stream_index);
        env->SetLongField(dpkt, tb_num, 1);
        env->SetLongField(dpkt, tb_den, state->tb_lcm);
        
        if(state->pkt.pts != AV_NOPTS_VALUE)
            env->SetLongField(dpkt, ts, av_rescale_q(state->pkt.pts, state->fmt_ctx->streams[state->pkt.stream_index]->time_base, (AVRational){1, state->tb_lcm}));
        else if(state->pkt.dts != AV_NOPTS_VALUE){
            // TODO: Sort out a properly delay here to synthesise the PTS.
            env->SetLongField(dpkt, ts, av_rescale_q(state->pkt.dts, state->fmt_ctx->streams[state->pkt.stream_index]->time_base, (AVRational){1, state->tb_lcm}));
        }
        
        if(state->pkt.duration != 0)
            env->SetLongField(dpkt, duration,av_rescale_q(state->pkt.duration, state->fmt_ctx->streams[state->pkt.stream_index]->time_base, (AVRational){1, state->tb_lcm}));
        
        if(state->pkt.flags & AV_PKT_FLAG_KEY)
            env->SetBooleanField(dpkt, splitPoint, JNI_TRUE);
        else
            env->SetBooleanField(dpkt, splitPoint, JNI_FALSE);
        
        jbyteArray dataArray = env->NewByteArray(pkt_tpl_size);
        env->SetByteArrayRegion(dataArray, 0, pkt_tpl_size, (jbyte *)pkt_tpl_data);
        env->SetObjectField(dpkt, data, dataArray);
        
        // Clean up.
        av_free_packet(&state->pkt);
        av_init_packet(&state->pkt);
        free(pkt_tpl_data); pkt_tpl_data = NULL;
        
        // Done.
        return dpkt;
    }
    else{
        fprintf(stderr, "Warning: failed to find object for a getNextChunk() call.\n");
        return NULL;
    }
}


/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    close
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_close
(JNIEnv * env, jobject obj){
    DemuxState *state = tracker.getObjectState(env, obj);
    if(state != NULL)
    {
        // Unregister the object state.
        tracker.unregisterObjectState(env, obj);

        return 0;
    }
    else
        return -1;
}