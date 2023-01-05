package ltd.kevinc.kcos

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream
import ltd.kevinc.kcos.pocos.CreateFileEntryReply
import ltd.kevinc.kcos.pocos.CreateFileEntryRequest
import ltd.kevinc.kcos.pocos.UploadReply
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.lang.Integer.min
import java.security.MessageDigest

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalSerializationApi::class)
object KCosFileUploader {
    /**
     * 上传文件，有多个重载方便进行使用。上传不可以使用并行的手段，服务器中做出了限制，并行上传会扔出异常
     * 该方法不会阻塞，而是会使用一个Flow将数据持续抛出
     * @param createFileEntryRequest 请直接查看这个类的doc，有详细定义。调用该方法时暂时不需要计算SHA256，SDK会帮你自动计算
     * @return 返回文件的元数据，里面包含了文件ID，分块数。上传的进度则会以Flow返回，表示当前的包序号，除以总分块数即可得到进度百分比
     */
    suspend fun uploadData(
        uri: Uri,
        context: Context,
        createFileEntryRequest: CreateFileEntryRequest
    ): Pair<CreateFileEntryReply, Flow<Int>?> {
        context.contentResolver.openInputStream(uri)!!.use { stream ->
            createFileEntryRequest.sha256 = calculateSHA256(stream)
        }
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            createFileEntryRequest.fileSize = fd.statSize
        }

        val reply = withContext(Dispatchers.IO) {
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

            try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                KCosClient.jsonSerializer.decodeFromStream<CreateFileEntryReply>(resp.body!!.byteStream())
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }
        }

        if (reply.nextRequestedFrame == 0L) {
            Log.i(
                "KCos.createFileEntry",
                "file already existed a copy, you can use it without uploading."
            )

            return Pair(reply, null)
        }

        val dataFlow = flow {
            var nextFrame = 1
            val buffer = ByteArray(1048576)

            context.contentResolver.openInputStream(uri)!!.use { stream ->
                while (nextFrame > 0) {
                    val contentLength = stream.read(buffer)
                    val formerFrame = nextFrame

                    nextFrame = uploadPack(
                        buffer.sliceArray(0 until contentLength),
                        reply.id,
                        nextFrame
                    )

                    emit(formerFrame)
                }
            }
        }.flowOn(Dispatchers.IO)

        return Pair(reply, dataFlow)
    }

    /**
     * 为了性能考虑，建议不要用这个接口传输大文件，内存顶不住
     * 可以拿这个接口发点小图片、小文件等等
     * 这个方法会阻塞调用，不上传完不会退出
     * @throws okio.IOException 当上传失败时会扔出异常，调用方必须捕获它
     * @throws IndexOutOfBoundsException 当字节数组非常大的时候，出于性能考虑会扔出这个异常
     */
    suspend fun uploadData(
        content: ByteArray,
        createFileEntryRequest: CreateFileEntryRequest
    ): CreateFileEntryReply {
        if (content.size > 10485760)
            throw IndexOutOfBoundsException()

        createFileEntryRequest.sha256 = calculateSHA256(content)
        createFileEntryRequest.fileSize = content.size.toLong()

        return withContext(Dispatchers.IO) {
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

            val reply = try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                KCosClient.jsonSerializer.decodeFromStream<CreateFileEntryReply>(resp.body!!.byteStream())
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }

            if (reply.nextRequestedFrame == 0L) {
                Log.i(
                    "KCos.createFileEntry",
                    "file already existed a copy, you can use it without uploading."
                )

                return@withContext reply
            }

            var cnt = 1
            while (cnt <= reply.frames) {
                val slice = content.sliceArray(
                    (cnt - 1) * 1048576 until min(
                        cnt * 1048576,
                        reply.fileSize.toInt()
                    )
                )
                uploadPack(slice, reply.id, cnt)

                cnt++
            }

            reply
        }
    }

    /**
     * 注意，byteArray类型暂时没有做恢复上传，因为意义实在不大
     * @return 返回Flow告诉当前进行到第几个pack
     */
    suspend fun continueUpload(
        fileId: Long,
        uri: Uri,
        context: Context
    ): Flow<Int> {
        val lastFrameId = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${KCosClient.urlBase}/file/lastFrameSeqNumber?fileId=$fileId")
                .header("X-AppId", KCosClient.appId)
                .header("X-AppKey", KCosClient.appKey)
                .header("X-UserId", KCosClient.userId.toString())
                .get()
                .build()

            try {
                val resp = KCosClient.httpClient.newCall(request).execute()
                resp.body!!.string().toInt()
            } catch (e: Exception) {
                Log.e("KCos.continueUpload", "failed to fetch frames, cannot continue upload!")
                throw e
            }
        }

        return flow {
            var nextFrame = 1 + lastFrameId
            val buffer = ByteArray(1048576)

            context.contentResolver.openInputStream(uri)!!.use { stream ->
                stream.skip(lastFrameId * 1048576L)

                while (nextFrame > 0) {
                    val contentLength = stream.read(buffer)

                    val formerFrame = nextFrame
                    nextFrame = uploadPack(
                        buffer.sliceArray(0 until contentLength),
                        fileId,
                        nextFrame
                    )

                    emit(formerFrame)
                }
            }
        }.flowOn(Dispatchers.IO)
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
            .url("https://tcp-cos.kevinc.ltd:8080/file/upload?fileId=$fileId&seqNumber=$sequenceNumber")
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

    /**
     * 把线程切走避免在主线程计算SHA256，非常耗时
     */
    private suspend fun calculateSHA256(stream: InputStream): String {
        return withContext(Dispatchers.Default) {
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1048576)

            var byteLength: Int
            do {
                byteLength = stream.read(buffer)
                sha.update(buffer, 0, byteLength)
            } while (byteLength == 1048576)

            sha.digest().joinToString(separator = "") { b ->
                b.toUByte().toString(radix = 16).lowercase()
            }
        }
    }

    /**
     * 千万不要放很大的数组进来
     */
    private suspend fun calculateSHA256(content: ByteArray): String {
        return withContext(Dispatchers.Default) {
            val sha = MessageDigest.getInstance("SHA-256")
            sha.digest(content).joinToString(separator = "") { b ->
                b.toUByte().toString(radix = 16).lowercase()
            }
        }
    }
}