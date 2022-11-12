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
                                                       jstring cache_dir, jint width, jint height) {
    int javaFileFd = file_fd;
    std::string cacheDirPathBase = env->GetStringUTFChars(cache_dir, nullptr);
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
    // 把inputFileCopyName转码后输出到outFileName,
    // 转码：帧率30，宽度为width参数，高度为height参数，编码器为h264
    int videoWidth = width;
    int videoHeight = height;
    // 调用Android media codec
    // 一、初始化解码器
    MediaCodec decoder;
    MediaExtractor extractor = new MediaExtractor(); //此类可分离视频文件的音轨和视频轨道
    extractor.setDataSource(inputFileCopyName); //媒体文件的位置
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < extractor.getTrackCount(); i++) {//遍历媒体轨道
        MediaFormat decodeFormat = extractor.getTrackFormat(i); //获取解码MediaFormat实体
        String mime = decodeFormat.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("video/")) {
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(decodeFormat, null, null, 0);
        }
    }
    if (decoder == null) {
        Log.e(TAG, "create decoder failed");
        return;
    }
    decoder.start();//启动MediaCodec ，等待传入数据
//    decodeInputBuffers=decoder.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
//    decodeOutputBuffers=decoder.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到PCM数据
    decodeBufferInfo=new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息

    // 二、初始化编码器
    MediaCodec encoder = MediaCodec.createEncoderByType("video/avc");
    MediaFormat encodeFormat = MediaFormat.createAudioFormat("video/avc", videoWidth, videoHeight);
    encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    encoder.start();
//    encodeInputBuffers=encoder.getInputBuffers();
//    encodeOutputBuffers=encoder.getOutputBuffers();
    encodeBufferInfo=new MediaCodec.BufferInfo();
    FileOutputStream* fileOutputStream = file.createOutputStream();

    //三、解码
    bool isOutPutBufferEOS = false;
    while (!isOutPutBufferEOS){
         if (!isInputBufferEOS){
            int inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US);// 请求是否有可用的input buffer，-1代表一直等待，0表示不等待 建议-1,避免丢帧
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId); // 获取input buffer
                inputBuffer.clear();//清空之前传入inputBuffer内的数据
                int sampleSize = extractor.readSampleData(inputBuffer, 0);//extractor读取数据到inputBuffer中
                //3. 如果读取不到数据，则认为是EOS。把数据生产端的buffer 送回到code的inputbuffer
                if (sampleSize < 0) {
                    isInputBufferEOS = true;
                    decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    // 提交数据给Codec
                    decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.getSampleTime(), 0);
                    //读取下一帧
                    extractor.advance();
                }
            }
         }
         //4. 数据消费端Client 拿到一个有数据的outputbuffer的index
         int index = decodec.dequeueOutputBuffer(encodeBufferInfo, TIMEOUT_US);
         if (index < 0) {
             continue;
         }
         //5. 通过index获取到outputBuffer
         ByteBuffer outputBuffer = decodec.getOutputBuffer(index);

        //把数据写入到FileOutputStream
        byte[] bytes = new byte[decodeBufferInfo.size];
        outputBuffer.get(bytes);
        try {
            fileOutputStream.write(bytes);
            fileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //6. 然后清空outputbuffer，再释放给codec的outputbuffer
        decodec.releaseOutputBuffer(index, false);
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isOutPutBufferEOS = true;
        }
    }
    decodec.stop();
    decodec.release(); //释放资源

    // 四、编码


    // 打扫战场
    remove(inputFileCopyName.c_str());
    LOGI("KCos.NDK.Video.InputFileCopy", "file copy cache removed.");
    return env->NewStringUTF(outFileName.c_str());
}