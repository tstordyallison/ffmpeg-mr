#include <jni.h>

extern "C" {
    #include "libavcodec/avcodec.h"
    #include "libavformat/avformat.h"
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved){
    // Set the log level.
    av_log_set_level(AV_LOG_ERROR);
    
    // Load up FFmpeg.
    av_register_all();
    avcodec_register_all();
    
    // Return the JVM version we need to run.
    return (jint)JNI_VERSION_1_6;
}