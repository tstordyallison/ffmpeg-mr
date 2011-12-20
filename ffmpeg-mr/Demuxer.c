#include <jni.h>
#include <stdint.h> // (For the C99 types).
#include "com_tstordyallison_ffmpegmr_Demuxer.h"

#include "ffmpeg_tpl.h"

#include "libavcodec/avcodec.h"
#include "libavutil/mathematics.h"
#include "libavutil/imgutils.h"
#include "libavutil/dict.h"
#include "libavformat/avformat.h"

// Main Demuxer State List.
// --------


typedef struct DemuxState {
    AVFormatContext *fmt_ctx;
    AVPacket pkt;
    int     stream_count;
    uint8_t **stream_data;
    int     *stream_data_sizes;
    jclass  dpkt_clazz;
    jmethodID dpkt_ctr;
    
} DemuxState;

typedef struct DemuxStateElement {
    jobject gobj;
    DemuxState *objstate;
    struct DemuxStateElement *next;
} DemuxStateElement;

static DemuxStateElement *object_state_head = NULL;

static DemuxStateElement *getObjectElement(JNIEnv *env, jobject obj)
{
    return object_state_head;
}

static DemuxState *getObjectState(JNIEnv *env, jobject obj)
{
    DemuxStateElement *state_el = getObjectElement(env, obj);
    if(state_el != NULL)
    {
        return state_el->objstate;
    }
    return NULL;
}

static DemuxState *unregisterObjectState(JNIEnv *env, jobject obj)
{
    DemuxStateElement *state_el = getObjectElement(env, obj);
    if(state_el != NULL)
    {
        // Delete the global reference. 
        (*env)->DeleteGlobalRef(env, state_el->gobj);
        
        free(object_state_head);
        object_state_head = NULL;
        
        return state_el->objstate;
    }
    else
        return NULL;
    
}

static void registerObjectState(JNIEnv *env, jobject obj, DemuxState *objstate)
{
    unregisterObjectState(env, NULL); // HACK.
    
    // Get a global reference to this object to play with for the duration of the Demux.
    jobject gobj = (*env)->NewGlobalRef(env, obj);
    
    object_state_head = malloc(sizeof(DemuxStateElement));
    
    object_state_head->gobj = gobj;
    object_state_head->objstate = objstate;
    
}


// Util methods.
// --------

static void print_file_error(const char *filename, int err)
{
    char errbuf[128];
    const char *errbuf_ptr = errbuf;
    
    if (av_strerror(err, errbuf, sizeof(errbuf)) < 0)
        errbuf_ptr = strerror(AVUNERROR(err));
    fprintf(stderr, "%s: %s\n", filename, errbuf_ptr);
}


// JNI Methods.
// --------

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved){
    // Load up FFmpeg.
    av_register_all();
    avcodec_register_all();
    
    // Return the JVM version we need to run.
    return (jint)JNI_VERSION_1_6;
}


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
    DemuxState *state = malloc(sizeof(DemuxState));
    state->fmt_ctx = NULL;
    registerObjectState(env, obj, state);
    
    // Opens the file, reads the streams, generates stream TPL, prepares for read calls.
    // ---------
    
    // Open the file for reading.
    filename = (*env)->GetStringUTFChars(env, jfilename, (jboolean *)&filename_copy);
    printf("File to demux: %s\n", filename);
    if ((err = avformat_open_input(&state->fmt_ctx, filename, NULL, NULL)) < 0) {
        print_file_error(filename, err);
        goto failure;
    }
    
    // Fill the streams in the format context
    if ((err = avformat_find_stream_info(state->fmt_ctx, NULL)) < 0) {
        printf("Failed to open streams in file %s, error %d\n", filename, err);
        goto failure;
    }
    
    // Bind a decoder to each input stream, and generate the TPL stream headers.
    state->stream_count      = state->fmt_ctx->nb_streams;
    state->stream_data       = malloc(sizeof(uint8_t*)*state->stream_count);
    state->stream_data_sizes = malloc(sizeof(int)*state->stream_count);
    
    for (i = 0; i < state->fmt_ctx->nb_streams; i++) {
        AVStream *stream = state->fmt_ctx->streams[i];
        AVCodec *codec;
        
        if (!(codec = avcodec_find_decoder(stream->codec->codec_id))) {
            fprintf(stderr, "Warning: Unsupported codec with id %d for input stream %d\n", stream->codec->codec_id, stream->index);
            state->stream_data[stream->index] = NULL;
            state->stream_data_sizes[stream->index] = 0;
            continue;
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n", stream->index);
            err = -1;
            goto failure;
        }
        
        // Generate the TPL stream headers.
        err = write_avstream_chunk_to_memory(stream, &((state->stream_data)[i]), &((state->stream_data_sizes)[i])); // I will be amazed if this works.
    }
    
    // Init the packet storage.
    av_init_packet(&state->pkt);
    
    // Do some once only init on the dpkt object.
    state->dpkt_clazz = (*env)->FindClass(env, "com/tstordyallison/ffmpegmr/Demuxer$DemuxPacket");
    if(state->dpkt_clazz == NULL) 
    {
        fprintf(stderr, "Could not find the com/tstordyallison/ffmpegmr/Demuxer$DemuxPacket class in the JVM.\n");
        err = -1;
        goto failure;
    }
    state->dpkt_ctr = (*env)->GetMethodID(env, state->dpkt_clazz, "<init>", "(Lcom/tstordyallison/ffmpegmr/Demuxer;)V");
    
failure:
    // Deallocs
    if(filename_copy == JNI_TRUE)
        (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    
    if(err != 0){
        unregisterObjectState(env, obj);
        return err;
    }else
        return 0;

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
    DemuxState *state = getObjectState(env, obj);
    if(state != NULL)
    {
        jbyteArray output = (*env)->NewByteArray(env, state->stream_data_sizes[stream_idx]);
        (*env)->SetByteArrayRegion(env, output, 0, state->stream_data_sizes[stream_idx], (jbyte*)(state->stream_data[stream_idx]));
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
    DemuxState *state = getObjectState(env, obj);
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
JNIEXPORT jobject JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_getNextChunk
(JNIEnv * env, jobject obj){
    DemuxState *state = getObjectState(env, obj);
    if(state != NULL)
    {
        // Read the next AVPacket from the input.
        int ret = 0;
        ret = av_read_frame(state->fmt_ctx, &state->pkt);
        if(ret == 0) return NULL;
        
        // Skip over any invalid streams.
        while(state->stream_data_sizes[state->pkt.stream_index] == 0){
            ret = av_read_frame(state->fmt_ctx, &state->pkt);
            if(ret == 0) return NULL;
        }
                                       
        // Create the DemuxPacket object.
        jobject dpkt = (*env)->NewObject(env, state->dpkt_clazz, state->dpkt_ctr, obj);
       
        // Temp for the AVPacket TPL.
        uint8_t *pkt_tpl_data;
        int pkt_tpl_size;                               
        
        // Generate the TPL.
        write_avpacket_chunk_to_memory(&state->pkt, &pkt_tpl_data, &pkt_tpl_size);
        
        // Copy the TPL version to the dpkt (and fill in the other dpkt values).
        jfieldID streamID = (*env)->GetFieldID(env, dpkt, "streamID", "I");
        jfieldID splitPoint = (*env)->GetFieldID(env, dpkt, "splitPoint", "Z");
        jfieldID data = (*env)->GetFieldID(env, dpkt, "data", "[B");
        
        (*env)->SetIntField(env, dpkt, streamID, state->pkt.stream_index);
        if(state->pkt.flags & AV_PKT_FLAG_KEY)
            (*env)->SetBooleanField(env, dpkt, splitPoint, JNI_TRUE);
        else
            (*env)->SetBooleanField(env, dpkt, splitPoint, JNI_FALSE);
        
        jbyteArray output_data = (*env)->NewByteArray(env, pkt_tpl_size);
        (*env)->SetByteArrayRegion(env, output_data, 0, pkt_tpl_size, (jbyte*)pkt_tpl_data);
        (*env)->SetObjectField(env, dpkt, data, output_data);
        
        // Release the temps.
        av_free_packet(&state->pkt);
        free(pkt_tpl_data);
        
        // Done.
        return dpkt;
    }
    else
        return NULL;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Demuxer
 * Method:    close
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Demuxer_close
(JNIEnv * env, jobject obj){
    DemuxState *state = getObjectState(env, obj);
    if(state != NULL)
    {
        // Unregister the object state from the linked list.
        unregisterObjectState(env, obj);
        
        // Free up the streams data.
        int i;
        for(i = 0; i < state->stream_count; i++)
            free(state->stream_data[i]);
        
        // Free the stream size data.
        free(state->stream_data_sizes);
        
        // Close the format context (TODO: think about what to do here if we are stream).
        av_close_input_file(state->fmt_ctx);
        
        // Free the state struct.
        free(state);
        
        return 0;
    }
    else
        return -1;
}