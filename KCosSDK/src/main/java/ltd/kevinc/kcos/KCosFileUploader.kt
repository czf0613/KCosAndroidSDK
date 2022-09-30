package ltd.kevinc.kcos

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream
import ltd.kevinc.kcos.pocos.CreateFileEntryReply
import ltd.kevinc.kcos.pocos.CreateFileEntryRequest
import ltd.kevinc.kcos.pocos.UploadReply
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 这个类是轻对象，可以随便new，不会意外建立很多http client
 * 这个类线程不安全，就是为了专门拿来针对每个上传任务设立的
 * 每有一个上传任务，就new一个出来即可
 */
@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalSerializationApi::class)
class KCosFileUploader {
    private var uploadJob: Job? = null
    private lateinit var delegate: KCosProcessDelegate

    /**
     * 上传文件，有多个重载方便进行使用。上传不可以使用并行的手段，服务器中做出了限制，并行上传会扔出异常
     * 该方法不会阻塞，而是会使用一个后台Job持续进行上传任务并进行回调
     * @param createFileEntryRequest 请直接查看这个类的doc，有详细定义
     * @return 返回文件的Id，是无符号int64类型，用户保存这个值即可进行下载操作。
     */
    suspend fun uploadData(
        file: File,
        createFileEntryRequest: CreateFileEntryRequest,
        delegate: KCosProcessDelegate
    ): Long {
        this.delegate = delegate

        file.inputStream().use { stream ->
            createFileEntryRequest.sha256 = calculateSHA256(stream)
        }

        val requestBody =
            KCosClient.jsonSerializer.encodeToString(createFileEntryRequest)
                .toRequestBody(KCosClient.jsonContentType)

        val request = Request.Builder()
            .url("${KCosClient.urlBase}/file/createFileEntry")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            val (fileId, nextFrameId, total) = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                val reply =
                    KCosClient.jsonSerializer.decodeFromStream<CreateFileEntryReply>(resp.body!!.byteStream())

                Triple(reply.id, reply.nextRequestedFrame, reply.frames)
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }

            if (nextFrameId == 0L) {
                Log.i(
                    "KCos.createFileEntry",
                    "file already existed a copy, you can use it without uploading."
                )

                return@withContext fileId
            }

            uploadJob?.cancel()
            uploadJob = launch {
                var nextFrame = 1
                val buffer = ByteArray(1048576)

                RandomAccessFile(file, "r").use { rf ->
                    while (nextFrame > 0) {
                        rf.seek((nextFrame - 1) * 1048576L)
                        val contentLength = rf.read(buffer)

                        try {
                            val formerFrame = nextFrame

                            nextFrame = uploadPack(
                                buffer.sliceArray(0 until contentLength),
                                fileId,
                                nextFrame
                            )

                            this@KCosFileUploader.delegate.onUploadTick(
                                formerFrame.toLong(),
                                total
                            )
                        } catch (e: Exception) {
                            Log.e("KCos.upload", e.stackTraceToString())
                            this@KCosFileUploader.delegate.onError(e)
                            break
                        }
                    }
                }
            }

            return@withContext fileId
        }
    }

    /**
     * 当使用文件选择器或者图片选择器的时候，会返回一个Uri给到我们，这个时候是非常难将这个Uri转换成File对象的
     * 因此我们直接见招拆招，直接将Uri上传就好了
     * @param createFileEntryRequest 请直接查看这个类的doc，有详细定义
     * @return 返回文件的Id，是无符号int64类型，用户保存这个值即可进行下载操作。
     */
    suspend fun uploadData(
        uri: Uri,
        context: Context,
        createFileEntryRequest: CreateFileEntryRequest,
        delegate: KCosProcessDelegate
    ): Long {
        this.delegate = delegate

        context.contentResolver.openInputStream(uri)!!.use { stream ->
            createFileEntryRequest.sha256 = calculateSHA256(stream)
        }

        val requestBody =
            KCosClient.jsonSerializer.encodeToString(createFileEntryRequest)
                .toRequestBody(KCosClient.jsonContentType)

        val request = Request.Builder()
            .url("${KCosClient.urlBase}/file/createFileEntry")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            val (fileId, nextFrameId, total) = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                val reply =
                    KCosClient.jsonSerializer.decodeFromStream<CreateFileEntryReply>(resp.body!!.byteStream())

                Triple(reply.id, reply.nextRequestedFrame, reply.frames)
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }

            if (nextFrameId == 0L) {
                Log.i(
                    "KCos.createFileEntry",
                    "file already existed a copy, you can use it without uploading."
                )

                return@withContext fileId
            }

            uploadJob?.cancel()
            uploadJob = launch {
                var nextFrame = 1
                val buffer = ByteArray(1048576)

                context.contentResolver.openInputStream(uri)!!.use { stream ->
                    while (nextFrame > 0) {
                        val contentLength = stream.read(buffer)

                        try {
                            val formerFrame = nextFrame

                            nextFrame = uploadPack(
                                buffer.sliceArray(0 until contentLength),
                                fileId,
                                nextFrame
                            )

                            this@KCosFileUploader.delegate.onUploadTick(
                                formerFrame.toLong(),
                                total
                            )
                        } catch (e: Exception) {
                            Log.e("KCos.upload", e.stackTraceToString())
                            this@KCosFileUploader.delegate.onError(e)
                            break
                        }
                    }
                }
            }

            return@withContext fileId
        }
    }

    /**
     * 此方法只能上传小于1MB的文件
     * @throws okio.IOException 当上传失败时会扔出异常，调用方必须捕获它
     */
    suspend fun uploadData(
        content: ByteArray,
        createFileEntryRequest: CreateFileEntryRequest
    ): Long {
        if (content.count() > 1048576 || createFileEntryRequest.fileSize > 1048576L)
            throw IllegalArgumentException("request body too large!")

        createFileEntryRequest.sha256 = calculateSHA256(content)

        val requestBody =
            KCosClient.jsonSerializer.encodeToString(createFileEntryRequest)
                .toRequestBody(KCosClient.jsonContentType)

        val request = Request.Builder()
            .url("${KCosClient.urlBase}/file/createFileEntry")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            val fileId = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                val reply =
                    KCosClient.jsonSerializer.decodeFromStream<CreateFileEntryReply>(resp.body!!.byteStream())

                reply.id
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }

            uploadPack(content, fileId, 1)
            fileId
        }
    }

    /**
     * 从中断处自动开始重新上传
     * @param fileId 在开始上传时，就已经返回过给调用方了，这个是决定文件的唯一Key
     * @param delegate 注册回调函数，覆写里面的onContinueUploadTick即可获取上传动态
     */
    suspend fun continueUpload(fileId: Long, file: File, delegate: KCosProcessDelegate) {
        this.delegate = delegate

        val request = Request.Builder()
            .url("${KCosClient.urlBase}/file/lastFrameSeqNumber?fileId=$fileId")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .get()
            .build()

        withContext(Dispatchers.IO) {
            val lastFrameId = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                resp.body!!.string().toInt()
            } catch (e: Exception) {
                Log.e("KCos.continueUpload", "failed to fetch frames, cannot continue upload!")
                this@KCosFileUploader.delegate.onError(e)
                return@withContext
            }

            uploadJob?.cancel()
            uploadJob = launch {
                var nextFrame = 1 + lastFrameId
                val buffer = ByteArray(1048576)

                RandomAccessFile(file, "r").use { rf ->
                    while (nextFrame > 0) {
                        rf.seek((nextFrame - 1) * 1048576L)
                        val contentLength = rf.read(buffer)

                        try {
                            val formerFrame = nextFrame
                            nextFrame = uploadPack(
                                buffer.sliceArray(0 until contentLength),
                                fileId,
                                nextFrame
                            )

                            this@KCosFileUploader.delegate.onContinueUploadTick(formerFrame.toLong())
                        } catch (e: Exception) {
                            Log.e("KCos.upload", e.stackTraceToString())
                            this@KCosFileUploader.delegate.onError(e)
                            break
                        }
                    }
                }
            }
        }
    }

    suspend fun continueUpload(
        fileId: Long,
        uri: Uri,
        context: Context,
        delegate: KCosProcessDelegate
    ) {
        this.delegate = delegate

        val request = Request.Builder()
            .url("${KCosClient.urlBase}/file/lastFrameSeqNumber?fileId=$fileId")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .get()
            .build()

        withContext(Dispatchers.IO) {
            val lastFrameId = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                resp.body!!.string().toInt()
            } catch (e: Exception) {
                Log.e("KCos.continueUpload", "failed to fetch frames, cannot continue upload!")
                this@KCosFileUploader.delegate.onError(e)
                return@withContext
            }

            uploadJob?.cancel()
            uploadJob = launch {
                var nextFrame = 1 + lastFrameId
                val buffer = ByteArray(1048576)

                context.contentResolver.openInputStream(uri)!!.use { stream ->
                    stream.skip(lastFrameId * 1048576L)

                    while (nextFrame > 0) {
                        val contentLength = stream.read(buffer)

                        try {
                            val formerFrame = nextFrame
                            nextFrame = uploadPack(
                                buffer.sliceArray(0 until contentLength),
                                fileId,
                                nextFrame
                            )

                            this@KCosFileUploader.delegate.onContinueUploadTick(formerFrame.toLong())
                        } catch (e: Exception) {
                            Log.e("KCos.upload", e.stackTraceToString())
                            this@KCosFileUploader.delegate.onError(e)
                            break
                        }
                    }
                }
            }
        }
    }

    /**
     * 不要在主线程调这个方法，会crash，用withContext切一下
     * @return 下一帧需要传输的序列Id
     */
    private fun uploadPack(
        content: ByteArray,
        fileId: Long,
        sequenceNumber: Int
    ): Int {
        val binaryRequestBody = content.toRequestBody(KCosClient.binaryContentType)

        val binaryRequest = Request.Builder()
            .url("${KCosClient.urlBase}/file/upload?fileId=$fileId&seqNumber=$sequenceNumber")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .put(binaryRequestBody)
            .build()

        val resp = KCosClient.httpClient.newCall(binaryRequest).execute()

        if (resp.code >= 400) {
            // 上传过程中出错
            Log.e("KCos.uploadPack", resp.body!!.string())
            throw IllegalArgumentException(resp.body!!.string())
        }

        val reply =
            KCosClient.jsonSerializer.decodeFromStream<UploadReply>(resp.body!!.byteStream())
        return reply.nextRequestedFrame
    }

    private fun calculateSHA256(stream: InputStream): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1048576)

        var byteLength: Int
        do {
            byteLength = stream.read(buffer)
            sha.update(buffer, 0, byteLength)
        } while (byteLength == 1048576)

        return sha.digest().joinToString(separator = "") { b ->
            b.toUByte().toString(radix = 16).lowercase()
        }
    }

    private fun calculateSHA256(content: ByteArray): String {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(content).joinToString(separator = "") { b ->
            b.toUByte().toString(radix = 16).lowercase()
        }
    }
}