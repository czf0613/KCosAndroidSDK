package ltd.kevinc.kcos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/**
 * 媒体压缩工具，可以将各种媒体素材进行压缩然后进行上传
 * 这个类线程不安全，哪里用，哪里直接new，也是一个轻对象
 * 1，图片会被转换成jpeg
 * 2，视频会被转换成h264，帧率被限30
 * 3，音频会被转换为aac
 */
@Suppress("BlockingMethodInNonBlockingContext")
class KCosUtils {
    init {
        System.loadLibrary("MediaConverter")
    }

    private lateinit var delegate: KCosProcessDelegate
    private var compressVideoJob: Job? = null

    companion object {
        // 这个值可以改，默认是30，可以改成1-100
        var jpegQuality = 30

        /**
         * 删除KCos产生的转码缓存文件
         * 别在转码过程中清除缓存，搞事情！
         */
        suspend fun clearCache(context: Context) {
            val copyCacheDir = File("${context.cacheDir.path}/KCosCopyCache/")
            val outputCacheDir = File("${context.cacheDir.path}/KCosConversionCache/")

            withContext(Dispatchers.IO) {
                if (copyCacheDir.exists()) {
                    copyCacheDir.deleteRecursively()
                    Log.i("KCos.Cache", "Copy Cache已清除")
                } else {
                    Log.i("KCos.Cache", "Copy Cache为空")
                }

                if (outputCacheDir.exists()) {
                    outputCacheDir.deleteRecursively()
                    Log.i("KCos.Cache", "Output Cache已清除")
                } else {
                    Log.i("KCos.Cache", "Output Cache为空")
                }
            }
        }
    }

    /**
     * 特别小心，视频一旦被这里处理，会限制帧率为30
     * @param fileFd 输入文件的描述符，由于android系统限制，非常难直接使用文件路径进行处理
     * @return 输出文件的路径，这个文件本质上是放在了app内的Cache dir里面，这样不需要权限也可以进行读取
     */
    private external fun convertVideoWithOptions(
        fileFd: Int,
        cacheDir: String,
        width: Int,
        height: Int
    ): String

    /**
     * 压缩Bitmap，注意，压缩后原有的bitmap会被recycle
     * @param width 图片宽度，设置为非正数的时候，表示维持原图的宽度
     * @param height 图片高度，设置为非正数的时候，表示维持原图的高度
     * @return 返回jpegData，可以直接进行网络发送，或者重新使用Image进行decode
     * @throws Exception 利用Bitmap的原生转码能力，可能由于安卓平台的实现问题，可能会出现各种各样的问题
     */
    suspend fun compressBitmap(bitmap: Bitmap, width: Int = -1, height: Int = -1): ByteArray {
        val expectedWidth = if (width <= 0) bitmap.width else width
        val expectedHeight = if (height <= 0) bitmap.height else height

        val scaleMatrix = Matrix().apply {
            postScale(
                expectedWidth.toFloat() / bitmap.width,
                expectedHeight.toFloat() / bitmap.height
            )
        }

        return withContext(Dispatchers.Default) {
            val tempBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, scaleMatrix, true)

            ByteArrayOutputStream().use { stream ->
                tempBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                stream.flush()
                tempBitmap.recycle()
                bitmap.recycle()

                stream.toByteArray()
            }
        }
    }

    /**
     * 获取Uri对应的资源并进行压缩。注意，对应资源必须是受支持的图片类型，否则无法压缩
     * 受支持的图片类型为png, jpg, webp和HEIC（不稳定）
     * heic的解码不一定能够成功，受制于硬件编码器的限制
     * @see compressBitmap 参数定义看此处定义
     * @throws java.io.FileNotFoundException 当文件的Uri无法被查询到的时候，会扔出一个异常，通常情况下不会发生这样的事情
     * @throws Exception 解码或转码过程出现错误（一般是本地的native库出错，SDK暂无办法解决）
     */
    suspend fun compressPicture(
        uri: Uri,
        context: Context,
        width: Int = -1,
        height: Int = -1
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            compressBitmap(bitmap, width, height)
        }
    }

    /**
     * 将手机内的视频压缩成h264编码，
     * 绝大部分的错误，会在delegate的OnError方法中扔出，但不意味着这个方法很安全，可能是一些JNI方法级别的错误
     * @see compressPicture 参数类型基本一致
     * @throws Exception 解码或转码过程出现错误（一般是本地的native库出错，SDK暂无办法解决）
     */
    suspend fun compressVideo(
        uri: Uri,
        context: Context,
        width: Int = -1,
        height: Int = -1,
        delegate: KCosProcessDelegate
    ) {
        this.delegate = delegate

        // 在计算核心上面跑转码运算性能会更好
        withContext(Dispatchers.Default) {
            compressVideoJob?.cancel()

            compressVideoJob = launch {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        val output = convertVideoWithOptions(
                            fd.detachFd(),
                            context.cacheDir.path,
                            width,
                            height
                        )
                        delegate.onConversionSuccess(File(output))
                    } ?: throw FileNotFoundException()
                } catch (e: Exception) {
                    delegate.onError(e)
                    Log.e("KCos.video.convert", "native code error!")
                }
            }
        }
    }

    /**
     * 将MP3、FLAC等音频转化为aac格式编码的音频
     * 原理是源文件 -> PCM -> aac音频
     * 请不要放进来非常长的声音文件！爆内存！
     * @param input 原始音频素材
     * @param bitRate 输出的比特率（自带单位K），要求高一点就256，如果仅仅是发送语音消息的，设置为64就够了
     * @return 返回被编码完成的aac音频
     */
    suspend fun compressAudioData(input: ByteArray, bitRate: Int = 256): ByteArray {
        TODO()
    }

    /**
     * 编码PCM数据到aac格式
     */
    suspend fun encodePCMAudioData(pcm: ByteArray, bitRate: Int = 64): ByteArray {
        TODO()
    }
}