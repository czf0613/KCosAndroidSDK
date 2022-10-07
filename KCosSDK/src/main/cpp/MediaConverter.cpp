#include <jni.h>
#include <string>
#include <cstdlib>
#include <ctime>
#include <fstream>
#include <unistd.h>
#include "android/log.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, __VA_ARGS__)

// Write C++ code here.

/*
 * 鉴于安卓系统日渐严格的文件读写权限，因此只能传递一个文件fd进来
 * fd的真实文件路径非常难知道，因此也很不方便调用ffmpeg
 * 因此需要先把fd代表的真实文件拷贝进来Cache文件夹，然后就想干嘛干嘛了
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_ltd_kevinc_kcos_KCosUtils_convertVideoWithOptions(JNIEnv *env, jobject thiz, jint file_fd,
                                                       jstring cacheDir, jint width, jint height) {
    int javaFileFd = file_fd;
    std::string cacheDirPathBase = env->GetStringUTFChars(cacheDir, nullptr);
    srand(time(nullptr));
    int fileNameRandInt = (random() % 900000) + 100000;
    std::string fileName = std::to_string(fileNameRandInt) + ".mp4";
    std::string inputFileCopyName = cacheDirPathBase + "/KCosCopyCache/" + fileName;
    std::string outFileName = cacheDirPathBase + "/KCosConversionCache/" + fileName;

    // 先实现文件拷贝
    std::ofstream copyOutputStream;
    copyOutputStream.open(inputFileCopyName, std::ofstream::out);
    long contentLength;
    long size = 0;
    char *buffer = (char *) malloc(1048576);
    do {
        contentLength = read(javaFileFd, buffer, 1048576);
        copyOutputStream.write(buffer, contentLength);
        size += contentLength;
    } while (contentLength > 0);
    copyOutputStream.close();
    free(buffer);
    LOGI("KCos.NDK.Video.InputFileCopy", "%s, length: %ld", inputFileCopyName.c_str(), size);
    LOGI("KCos.NDK.Video.OutputFile", "%s", outFileName.c_str());

    // 此处开始转码

    return env->NewStringUTF(outFileName.c_str());
}