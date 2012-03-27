#include <jni.h>
#include "libavutil/rational.h"

void print_av_error(int err);
int getHashCode(JNIEnv* env, jobject obj);
void print_file_error(const char *filename, int err);
void throw_new_exception(JNIEnv *env, const char* msg);
int lcmf(int arr[], int size);
void dump_buffer(uint8_t *data, int N);