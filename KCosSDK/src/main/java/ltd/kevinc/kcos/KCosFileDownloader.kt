package ltd.kevinc.kcos

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import ltd.kevinc.kcos.pocos.DownloadMetadata

@Deprecated(message = "此方法暂未完成开发，请勿调用！", level = DeprecationLevel.ERROR)
class KCosFileDownloader {
    private var downloadJob: Job? = null
    private lateinit var delegate: KCosProcessDelegate

    suspend fun getFileDownloadMeta(
        fileId: ULong,
        password: String? = null
    ): DownloadMetadata {
        TODO()
    }

    /**
     * 直接下载至ByteArray中，这个方法只允许下载不超过4MB的东西，大了容易炸内存
     * 如果需要下载大型数据，请使用基于flow的接口
     */
    suspend fun downloadSimpleFile(
        fileId: ULong,
        password: String? = null
    ): ByteArray {
        TODO()
    }

    /**
     * 请不要并行调用该方法，会成倍消耗流量
     * Flow会不定大小地按顺序返回字节数组，将字节数组拼接起来，即可得到完整的文件
     * 这个方法不限制文件大小，并且可以断点下载
     * @param since 指定从哪个字节开始下载（包含该字节），用于断点续传
     */
    suspend fun downloadLargeFile(
        fileId: ULong,
        password: String? = null,
        since: Long = 0L
    ): Flow<ByteArray> {
        TODO()
    }

    /**
     * 直接拉起系统下载器完成下载，对于某些大文件非常合适
     */
    suspend fun downloadViaSystemDownloadManager(
        fileId: ULong,
        password: String? = null
    ) {

    }
}