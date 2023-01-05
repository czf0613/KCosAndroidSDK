package ltd.kevinc.kcos

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ltd.kevinc.kcos.pocos.DownloadMetadata
import okhttp3.Request
import okio.IOException
import kotlin.math.min

@Suppress("BlockingMethodInNonBlockingContext")
object KCosFileDownloader {
    private fun makeUrl(fileId: Long, password: String? = null): String =
        "${KCosClient.urlBase}/file/download?fileId=$fileId" + (if (password == null) "" else "&password=$password")

    /**
     * 获取下载文件的元信息，例如MimeType、文件大小、文件名等等
     * @throws okio.IOException 表示文件不允许下载
     */
    suspend fun getFileDownloadMeta(
        fileId: Long,
        password: String? = null
    ): DownloadMetadata {
        return withContext(Dispatchers.IO) {
            val url = makeUrl(fileId, password)
            val request = Request.Builder()
                .url(url)
                .header("X-AppId", KCosClient.appId)
                .header("X-AppKey", KCosClient.appKey)
                .header("X-UserId", KCosClient.userId.toString())
                .head()
                .build()

            val resp = KCosClient.httpClient.newCall(request).execute()

            if (resp.code >= 400)
                throw IOException("该文件不允许下载")

            val type = resp.header("Content-Type", "application/octet-stream")
            val length = resp.header("Content-Length", "0")
            val fileName = resp.header("Content-Disposition", "filename=unknown.file")
                ?: "filename=unknown.file"

            DownloadMetadata(
                contentLength = length?.toLong() ?: 0L,
                mimeType = type ?: "application/octet-stream",
                fileName = fileName.substring(9)
            )
        }
    }

    /**
     * 直接下载至ByteArray中，这个方法只允许下载不超过4MB的东西，大了容易炸内存
     * 如果需要下载大型数据，请使用基于flow的接口
     * @throws ArrayIndexOutOfBoundsException 文件太大，缓冲区的位置不够用
     * @throws okio.IOException 表示文件不允许下载
     */
    suspend fun downloadSimpleFile(
        fileId: Long,
        password: String? = null
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            val url = makeUrl(fileId, password)
            val meta = getFileDownloadMeta(fileId, password)

            if (meta.contentLength > 4 * 1048576L)
                throw ArrayIndexOutOfBoundsException("下载文件的大小超过4MB，请使用大文件下载接口")

            val request = Request.Builder()
                .url(url)
                .header("X-AppId", KCosClient.appId)
                .header("X-AppKey", KCosClient.appKey)
                .header("X-UserId", KCosClient.userId.toString())
                .get()
                .build()

            val resp = KCosClient.httpClient.newCall(request).execute()
            resp.body!!.bytes()
        }
    }

    /**
     * 请不要并行调用该方法，浪费流量
     * 下载大文件前可以调用 getFileDownloadMeta方法获取文件大小、文件名等等必要信息
     * Flow会不定大小地按顺序返回字节数组，将字节数组拼接起来，即可得到完整的文件
     * 这个方法不限制文件大小，并且可以断点下载
     * @param from 指定从哪个字节开始下载（包含该字节），用于断点续传
     * @param to 跟from同理，表示下载到哪个字节为止（包含该字节），传入负值表示全文件下载
     * @throws okio.IOException 表示文件不允许下载
     * @throws ArrayIndexOutOfBoundsException 起止点不合法
     */
    suspend fun downloadLargeFile(
        fileId: Long,
        password: String? = null,
        from: Long = 0L,
        to: Long = -1L
    ): Flow<ByteArray> {
        if (from < to) {
            throw ArrayIndexOutOfBoundsException("starting point is less than end point")
        }

        val url = makeUrl(fileId, password)
        val meta = getFileDownloadMeta(fileId, password)

        if (to >= meta.contentLength || from < 0) {
            throw ArrayIndexOutOfBoundsException()
        }
        val endPoint = if (to < 0) meta.contentLength - 1 else to

        return flow {
            var currentBytes = from
            while (currentBytes < endPoint) {
                val endBytes = min(currentBytes + 1048575, endPoint)
                val request = Request.Builder()
                    .url(url)
                    .header("X-AppId", KCosClient.appId)
                    .header("X-AppKey", KCosClient.appKey)
                    .header("X-UserId", KCosClient.userId.toString())
                    .header("Range", "bytes=${currentBytes}-$endBytes")
                    .get()
                    .build()

                val resp = KCosClient.httpClient.newCall(request).execute()

                emit(resp.body!!.bytes())
                currentBytes += 1048576
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * 直接拉起系统下载器完成下载，对于某些大文件非常合适
     * @param targetDestination 下载后的文件放在哪里
     * @return 下载任务的ID，由安卓系统提供
     * @throws okio.IOException 表示文件不允许下载
     */
    suspend fun downloadViaSystemDownloadManager(
        fileId: Long,
        context: Context,
        password: String? = null,
        targetDestination: Uri? = null
    ): Long {
        val url = makeUrl(fileId, password)
        val meta = getFileDownloadMeta(fileId, password)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType(meta.mimeType)
            addRequestHeader("X-AppId", KCosClient.appId)
            addRequestHeader("X-AppKey", KCosClient.appKey)
            addRequestHeader("X-UserId", KCosClient.userId.toString())
            setTitle("KCos正在下载云文件")
            setDescription(meta.fileName)
            if (targetDestination != null)
                setDestinationUri(targetDestination)
        }

        return downloadManager.enqueue(request)
    }
}