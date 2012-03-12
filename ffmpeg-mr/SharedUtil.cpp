#include "SharedUtil.h"
#include <jni.h>

extern "C" {
    #include "libavcodec/avcodec.h"
    #include "libavformat/avformat.h"
}

// Util methods.
// --------

int getHashCode(JNIEnv* env, jobject obj)
{
    jclass clazz  = env->GetObjectClass(obj);
    jmethodID mid = env->GetMethodID(clazz, "hashCode", "()I");
    jint hashCode = env->CallIntMethod(obj, mid);
    return (int)hashCode;
}

void print_file_error(const char *filename, int err)
{
    char errbuf[128];
    const char *errbuf_ptr = errbuf;
    
    if (av_strerror(err, errbuf, sizeof(errbuf)) < 0)
        errbuf_ptr = strerror(AVUNERROR(err));
    fprintf(stderr, "%s: %s\n", filename, errbuf_ptr);
}

void throw_new_exception(JNIEnv *env, const char* msg){
    jclass err_clazz = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(err_clazz, msg);
}