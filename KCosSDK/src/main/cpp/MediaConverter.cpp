#include <jni.h>

// Write C++ code here.

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_ltd_kevinc_kcos_KCosUtils_convertImageWithOptions(JNIEnv *env, jobject thiz, jstring file_path,
                                                       jint width, jint height, jint jpeg_quality) {
    // TODO: implement convertImageWithOptions()
}
extern "C"
JNIEXPORT jstring JNICALL
Java_ltd_kevinc_kcos_KCosUtils_convertVideoWithOptions(JNIEnv *env, jobject thiz, jstring file_path,
                                                       jint width, jint height) {
    // TODO: implement convertVideoWithOptions()
}