#include <jni.h>
#include <string>
#include <cstdlib>
#include <ctime>
#include <fstream>
#include <sys/stat.h>
#include <stdint.h>
#include "android/log.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaFormat.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, __VA_ARGS__)

// Write C++ code here.

/*
 * 鉴于安卓系统日渐严格的文件读写权限，因此只能传递一个文件fd进来
 * fd的真实文件路径非常难知道，因此也很不方便调用ffmpeg
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_ltd_kevinc_kcos_KCosUtils_convertVideoWithOptions(JNIEnv *env, jobject thiz, jint file_fd,
                                                       jstring cache_dir, jint width, jint height) {
    int javaFileFd = file_fd;
    std::string cacheDirPathBase = env->GetStringUTFChars(cache_dir, nullptr);
    srand(time(nullptr));
    int fileNameRandInt = (random() % 900000) + 100000;
    std::string fileName = std::to_string(fileNameRandInt) + ".mp4";
    std::string internalFileName = cacheDirPathBase + "/KCosYUVCache/" + fileName;
    std::string outFileName = cacheDirPathBase + "/KCosConversionCache/" + fileName;

    // 此处开始转码，调用Android media codec
    // 转码后输出到outFileName,
    int32_t videoWidth = width;
    int32_t videoHeight = height;
    int64_t timeOutUs = 2000;

    // 解析媒体文件来获取decoder所需mime
    struct stat64 videoFileInfo;
    fstat64(javaFileFd, &videoFileInfo);
    AMediaCodec *decoder = nullptr;
    AMediaExtractor *inputVideoExtractor = AMediaExtractor_new();
    AMediaExtractor_setDataSourceFd(inputVideoExtractor, javaFileFd, 0, videoFileInfo.st_size);
    for (auto i = 0; i < AMediaExtractor_getTrackCount(inputVideoExtractor); ++i) {
        AMediaFormat *mediaFormat = AMediaExtractor_getTrackFormat(inputVideoExtractor, i);
        const char *mimeType;
        AMediaFormat_getString(mediaFormat, AMEDIAFORMAT_KEY_MIME, &mimeType);

        if (strncmp(mimeType, "video/", 6) == 0) {
            decoder = AMediaCodec_createDecoderByType(mimeType);
            AMediaCodec_configure(decoder, mediaFormat, nullptr, nullptr, 0);
            break;
        }
    }

    // 如果找不到合适的解码器，报错
    if (decoder == nullptr) {
        exit(-1);
    }

    // 配置所需的输出编码器，为帧率30，宽度为width参数，高度为height参数，编码器为h264
    AMediaFormat *outputFileFormat = AMediaFormat_new();
    AMediaFormat_setString(outputFileFormat, AMEDIAFORMAT_KEY_MIME, "video/avc");
    AMediaFormat_setInt32(outputFileFormat, AMEDIAFORMAT_KEY_WIDTH, videoWidth);
    AMediaFormat_setInt32(outputFileFormat, AMEDIAFORMAT_KEY_HEIGHT, videoHeight);
    AMediaFormat_setInt32(outputFileFormat, AMEDIAFORMAT_KEY_FRAME_RATE, 30);
    AMediaCodec *encoder = AMediaCodec_createEncoderByType("video/avc");
    AMediaCodec_configure(encoder, outputFileFormat, nullptr, nullptr,
                          AMEDIACODEC_CONFIGURE_FLAG_ENCODE);

    // 解码器开始工作，写入到一个中间文件中
    LOGI("KCos.NDK.Video.Decode", "Decoding video and writing to yuv cache file.");
    std::ofstream internalFileOutputStream;
    internalFileOutputStream.open(internalFileName);
    AMediaCodec_start(decoder);
    bool isDecodeInputEOF = false;
    bool isDecodeOutputEOF = false;

    while (!isDecodeOutputEOF) {
        if (!isDecodeInputEOF) {
            ssize_t inputBufferIndex = AMediaCodec_dequeueInputBuffer(decoder, timeOutUs);
            if (inputBufferIndex >= 0) {
                size_t inputBufferLength = 0;
                uint8_t *inputBuffer = AMediaCodec_getInputBuffer(decoder, inputBufferIndex,
                                                                  &inputBufferLength);
                ssize_t sampleSize = AMediaExtractor_readSampleData(inputVideoExtractor,
                                                                    inputBuffer, inputBufferLength);

                if (sampleSize <= 0) {
                    isDecodeInputEOF = true;
                    sampleSize = 0;
                }
                int64_t inputVideoSampleTime = AMediaExtractor_getSampleTime(inputVideoExtractor);
                AMediaCodec_queueInputBuffer(decoder, inputBufferIndex, 0, sampleSize,
                                             inputVideoSampleTime, isDecodeInputEOF
                                                                   ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM
                                                                   : 0);

                AMediaExtractor_advance(inputVideoExtractor);
            }
        }

        // 每次input，就把所有已解码的数据一次性全部取出，因为解码的时候，output很可能比input多
        ssize_t outputBufferIndex;
        AMediaCodecBufferInfo outputBufferInfo;
        do {
            outputBufferIndex = AMediaCodec_dequeueOutputBuffer(decoder, &outputBufferInfo,
                                                                timeOutUs);
            if (outputBufferIndex >= 0) {
                if (outputBufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                    isDecodeOutputEOF = true;
                }

                uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(decoder, outputBufferIndex,
                                                                    nullptr);
                internalFileOutputStream.write(
                        (const char *) (outputBuffer + outputBufferInfo.offset),
                        outputBufferInfo.size);
            }
        } while (outputBufferIndex >= 0 && !isDecodeOutputEOF);
    }

    // 结束解码
    AMediaCodec_stop(decoder);
    internalFileOutputStream.flush();
    internalFileOutputStream.close();
    LOGI("KCos.NDK.Video.Decode", "YUV cache file created.");

    // 编码器开始工作
    LOGI("KCos.NDK.Video.Encode", "Encoding from YUV to H264");
    std::ofstream convertedFileOutputStream;
    convertedFileOutputStream.open(outFileName, std::ofstream::out);
    AMediaExtractor *internalVideoExtractor = AMediaExtractor_new();
    AMediaExtractor_setDataSource(internalVideoExtractor, internalFileName.c_str());
    AMediaCodec_start(encoder);
    bool isEncodeInputEOF = false;
    bool isEncodeOutputEOF = false;

    while (!isEncodeOutputEOF) {
        if (!isEncodeInputEOF) {
            ssize_t inputBufferIndex = AMediaCodec_dequeueInputBuffer(encoder, timeOutUs);
            if (inputBufferIndex >= 0) {
                size_t inputBufferLength = 0;
                uint8_t *inputBuffer = AMediaCodec_getInputBuffer(encoder, inputBufferIndex,
                                                                  &inputBufferLength);
                ssize_t sampleSize = AMediaExtractor_readSampleData(internalVideoExtractor,
                                                                    inputBuffer,
                                                                    inputBufferLength);

                if (sampleSize <= 0) {
                    isEncodeInputEOF = true;
                    sampleSize = 0;
                }
                int64_t internalVideoSampleTime = AMediaExtractor_getSampleTime(
                        internalVideoExtractor);
                AMediaCodec_queueInputBuffer(encoder, inputBufferIndex, 0, sampleSize,
                                             internalVideoSampleTime, isEncodeInputEOF
                                                                      ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM
                                                                      : 0);

                AMediaExtractor_advance(inputVideoExtractor);
            }
        }

        // 因为编码过程中，产出速度大概率比不上输入速度，所以这里不需要还做一层while循环，直接取出即可
        AMediaCodecBufferInfo outputBufferInfo;
        ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(encoder, &outputBufferInfo,
                                                                    timeOutUs);
        if (outputBufferIndex >= 0) {
            if (outputBufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                isEncodeOutputEOF = true;
            }

            uint8_t *outputBuffer = AMediaCodec_getOutputBuffer(encoder, outputBufferIndex,
                                                                nullptr);
            convertedFileOutputStream.write(
                    (const char *) (outputBuffer + outputBufferInfo.offset),
                    outputBufferInfo.size);
        }
    }

    // 结束编码
    AMediaCodec_stop(encoder);
    convertedFileOutputStream.flush();
    convertedFileOutputStream.close();
    LOGI("KCos.NDK.Video.Encode", "Encode success.");

    remove(internalFileName.c_str());
    LOGI("KCos.NDK.Video.YUVCache", "internal yuv cache removed.");
    LOGI("KCos.NDK.Video.OutputFile", "%s", outFileName.c_str());
    return env->NewStringUTF(outFileName.c_str());
}