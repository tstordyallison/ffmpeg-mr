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
};
const struct DemuxState DEMUXSTATE_DEFAULT = {NULL, NULL, -1, NULL, NULL, NULL, NULL};

class DemuxTracker {
    
private:
    map<int, DemuxState*> objectRegister; // Object register.
    
public:
    DemuxState *getObjectState(JNIEnv *env, jobject obj)
    {
        int hashCode = getHashCode(env, obj);
        if(objectRegister.find(hashCode) != objectRegister.end())
        {
            return objectRegister[hashCode];
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
            
            // Close the format context (TODO: think about what to do here if we are stream).
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
    int i, filename_copy;
    const char *filename;
    
    // Init state and add them to the object register.
    DemuxState *state = new DemuxState; 
    *state = DEMUXSTATE_DEFAULT;
    
    // Opens the file, reads the streams, generates stream TPL, prepares for read calls.
    // ---------
    
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
    
    // Bind a decoder to each input stream, and generate the TPL stream headers.
    state->stream_count      = state->fmt_ctx->nb_streams;
    state->stream_data       = new uint8_t*[state->stream_count]; // list of lists
    state->stream_data_sizes = new int[state->stream_count];
    state->common_tb         = new AVRational[state->stream_count];
    
    for (i = 0; i < state->fmt_ctx->nb_streams; i++) {
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

    for (i = 0; i < state->stream_count; i++){
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
    for (i = 0; i < state->stream_count; i++){
        if(state->common_tb[i].den > 0)
        {
            if(lcm > state->common_tb[i].den) {
                int mul = lcm / state->common_tb[i].den;
                state->common_tb[i].num *= mul;
                state->common_tb[i].den = lcm;
            }
            else if(lcm < state->common_tb[i].den)
            {
                // Can this ever happen? 
                fprintf(stderr, "LCM calc cockup.\n");
            }
            
            if(DEBUG)
                fprintf(stderr, "Output tb (%d): %d/%d (1/=%2.2f)\n", i, state->common_tb[i].num, state->common_tb[i].den, (float)state->common_tb[i].den/state->common_tb[i].num);
        
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
    // Deallocs
    if(filename_copy == JNI_TRUE)
        env->ReleaseStringUTFChars(jfilename, filename);
    
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

typedef struct StreamRead {
    int stream_idx;
    DemuxState *objstate;
} StreamRead;

/*
 * Custom read function for TPL stream data.
 */
static int StreamData_Read(/*StreamRead*/ void *stream_choice, uint8_t *buf, int buf_size){
    return -1;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    initDemuxWithStream
 * Signature: (Ljava/io/InputStream;)V
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_initDemuxWithStream
(JNIEnv *env, jobject obj, jobject stream){
    // Not yet implemented - "Use av_open_input_stream() instead of av_open_input_file() to open your stream".
    // Define a bunch of functions that can then read the stream.
    // Going to be a bit messy.
    return -1;
}


/*
 * FFmpeg custom IO read function - for use with JNI InputStream.
 */
static int Java_InputStream_Read(/*jobject*/ void *jni_input_stream, uint8_t *buf, int buf_size){
    return -1;
}

/*
 * FFmpeg custom IO seek function - for use with JNI InputStream.
 */

static int64_t Java_InputStream_Seek(/*jobject*/ void *jni_input_stream, int64_t offset, int whence){
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
        
        // Skip over any invalid streams.
        while(state->stream_data_sizes[state->pkt.stream_index] == 0){
            ret = av_read_frame(state->fmt_ctx, &state->pkt);
            if(ret != 0) return NULL;
        }
        
        // Temp for the AVPacket TPL.
        uint8_t *pkt_tpl_data;
        int pkt_tpl_size;                               
        
        // Generate the TPL.
        write_avpacket_chunk_to_memory(&state->pkt, &pkt_tpl_data, &pkt_tpl_size);
        av_free(state->pkt.data);       state->pkt.data = NULL;
        av_free(state->pkt.side_data);  state->pkt.side_data = NULL;
                                       
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