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

int gcdf(int a, int b)
{ 
    if (b==0) return a;
    return gcdf(b, a % b);
} 

static int lcmf2(int a, int b)
{ 
    return a * b / gcdf(a,b);
} 

int lcmf(int arr[], int size)
{ 
    if (size < 2) return arr[0];
    if (size == 2) return lcmf2(arr[0], arr[1]);
    int arr2[2];
    arr2[0] = lcmf( arr, size / 2 );
    arr2[1] = lcmf( arr + size / 2, size - (size / 2));
    return lcmf(arr2, 2);
} 

static void printdataline(uint8_t *data, int N)
{
    int i;
    
    for(i=0; i<N; i++)
    {
        printf("%02x ", data[i]);
        printf(" ");
    }
    for(i=0;i<N;i++)
    {
        if(isgraph((unsigned) data[i]))
            printf("%c", data[i]);
        else
            printf(".");
    }
    printf("\n");
}

void dump_buffer(uint8_t *data, int N)
{
    int i;
    
    for(i=0; i<N; i+=16)
    {
        if(i + 16 <= N)
            printdataline((uint8_t *)data+i, 16);
        else
            printdataline((uint8_t *)data+1, N - i);
    }
}
