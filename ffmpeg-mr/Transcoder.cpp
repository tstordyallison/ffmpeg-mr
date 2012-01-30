#include <jni.h>
#include "com_tstordyallison_ffmpegmr_Transcoder.h"
#include "SharedUtil.h"
#include <map>

using namespace std;

extern "C" {
    #include "ffmpeg_tpl.h"
        
    #include "libavcodec/avcodec.h"
    #include "libavutil/mathematics.h"
    #include "libavutil/imgutils.h"
    #include "libavutil/dict.h"
    #include "libavformat/avformat.h"
    #include "libavutil/mathematics.h"
}

struct TranscoderState {
    
};
const struct TranscoderState TRANSSTATE_DEFAULT = {};

class TranscoderTracker {
    
    private:
        map<int, TranscoderState*> objectRegister; // Object register.
        
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
                
                // --- FREE STATE STUFF --- //
                
                // Remove the state from the map.
                int hashCode = getHashCode(env, obj);
                objectRegister.erase(hashCode);
                
                // Free the state.
                delete state;
            }
        };
};

static TranscoderTracker tracker;

// JNI Methods.
// --------


/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    initWithBytes
 * Signature: ([J[B)I
 */
JNIEXPORT jint JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_initWithBytes
(JNIEnv *env, jobject obj, jlongArray chunkPoints, jbyteArray data){
    
    
    return 0;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    hasMoreData
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_hasMoreData
(JNIEnv *env, jobject obj){
    
    
    return false;
}

/*
 * Class:     com_tstordyallison_ffmpegmr_Transcoder
 * Method:    getNextPacket
 * Signature: ()Lcom/tstordyallison/ffmpegmr/DemuxPacket;
 */
JNIEXPORT jobject JNICALL Java_com_tstordyallison_ffmpegmr_Transcoder_getNextPacket
(JNIEnv *env, jobject obj){
    
    
    return NULL;
}