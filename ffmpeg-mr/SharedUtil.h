#include <jni.h>

int getHashCode(JNIEnv* env, jobject obj);
void print_file_error(const char *filename, int err);