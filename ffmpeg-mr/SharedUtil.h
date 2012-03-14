#include <jni.h>
#include "libavutil/rational.h"

static const AVRational TS_BASE = {1, 1000000};

int getHashCode(JNIEnv* env, jobject obj);
void print_file_error(const char *filename, int err);
void throw_new_exception(JNIEnv *env, const char* msg);
int lcmf(int arr[], int size);
int gcdf(int a, int b);