#include <iostream>
#include "com_tstordyallison_ffmpegmr_Remuxer.h"
#include "SharedUtil.h"

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
    jmethodID   getIterator = env->GetMethodID(iterable_clazz, "iterator", "()Ljava/util/Iterator;");
    jobject     iterator    = env->CallObjectMethod(chunks, getIterator);
    
    jclass      iterator_clazz = env->GetObjectClass(iterator);
    jmethodID   hasNext     = env->GetMethodID(iterator_clazz, "hasNext", "()Z");
    jmethodID   next        = env->GetMethodID(iterator_clazz, "next", "()Ljava/lang/Object;");
    
    
    uint8_t **data_chunks = NULL; // Array of byte arrays containing each streams data. 
    int *data_chunks_size  = NULL;
    int nb_chunks = 0;
    
    while(env->CallBooleanMethod(iterator, hasNext))
    {
        // Increment and alloc.
        nb_chunks += 1;
        data_chunks = (uint8_t **)realloc(data_chunks, sizeof(uint8_t *) * nb_chunks);
        data_chunks_size = (int *)realloc(data_chunks_size, sizeof(uint8_t *) * nb_chunks);
        
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
    
    // ------
    // DO SOMETHING!
    // ------
    
    // Free up all the state.
    for(int i = 0; i < nb_chunks; i++)
    {
        free(data_chunks[i]);
        data_chunks[i] = NULL;
    }
    free(data_chunks_size);
    
    return env->NewByteArray(0);
};