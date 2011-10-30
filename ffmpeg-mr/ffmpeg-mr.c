#include "Transcoder.h"
#include <jni.h>
#include "avcodec.h"
#include "avformat.h"

JNIEXPORT jboolean JNICALL Java_Transcoder_verifyCodecs(JNIEnv * env, jclass class, jint input_codec, jint output_codec)
{
    AVCodec *decoder;
    AVCodec *encoder;
    
    av_register_all();
    
    decoder = avcodec_find_decoder(input_codec);
    encoder = avcodec_find_encoder(output_codec);
    
    // Also needs to check that the codec are of the same type.

    if(decoder != NULL && encoder != NULL)
    {
        printf("Decoder: %s\n", decoder->name);
        printf("Encoder: %s\n", encoder->name);
        return JNI_TRUE;
    }
    else
    {
        printf("Codec verification failed.");
        return JNI_FALSE;
    }
}