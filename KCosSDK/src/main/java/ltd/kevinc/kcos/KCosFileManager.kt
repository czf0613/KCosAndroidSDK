package ltd.kevinc.kcos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream
import ltd.kevinc.kcos.pocos.CreateFileEntryReply
import ltd.kevinc.kcos.pocos.CreateFileEntryRequest
import ltd.kevinc.kcos.pocos.ListDirectoryReply
import ltd.kevinc.kcos.pocos.ListDirectoryRequest
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalSerializationApi::class)
object KCosFileManager {
    suspend fun listDirectory(path: String, showSubDirectory: Boolean = false): ListDirectoryReply {
        val requestBody = ListDirectoryRequest(path, showSubDirectory)
        val request = Request.Builder()
            .url("${KCosClient.urlBase}/fileManager/listDirectory")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .post(
                KCosClient.jsonSerializer.encodeToString(requestBody)
                    .toRequestBody(KCosClient.jsonContentType)
            )
            .build()

        return withContext(Dispatchers.IO) {
            try {
                KCosClient.httpClient.newCall(request).execute().use { resp ->
                    KCosClient.jsonSerializer.decodeFromStream(resp.body!!.byteStream())
                }
            } catch (e: Exception) {
                Log.e("KCos.listDirectory", e.stackTraceToString())
                throw e
            }
        }
    }

    suspend fun removeFile(fileId: Long) {
        val request = Request.Builder()
            .url("${KCosClient.urlBase}/fileManager/removeFile?fileId=$fileId")
            .header("X-AppId", KCosClient.appId)
            .header("X-AppKey", KCosClient.appKey)
            .header("X-UserId", KCosClient.userId.toString())
            .delete()
            .build()

        withContext(Dispatchers.IO) {
            try {
                KCosClient.httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("KCos.listDirectory", e.stackTraceToString())
                throw e
            }
        }
    }

    /**
     * 将他人上传的文件复制到自己的空间下，需要提供必要的凭据，否则是不允许随便进行拷贝的
     * 拷贝过程中，可以通过createFileEntryRequest来指定拷贝过来之后的那个文件的文件名以及存放路径，以及它的可访问性
     * @see CreateFileEntryRequest
     */
    suspend fun copyFileToMySpace(
        fileId: Long,
        password: String? = null,
        createFileEntryRequest: CreateFileEntryRequest
    ): CreateFileEntryReply {
        return withContext(Dispatchers.IO) {
            val requestBody =
                KCosClient.jsonSerializer.encodeToString(createFileEntryRequest)
                    .toRequestBody(KCosClient.jsonContentType)

            val request = Request.Builder()
                .url("${KCosClient.urlBase}/file/copyFileEntry?fileId=$fileId" + (if (password == null) "" else "&password=$password"))
                .header("X-AppId", KCosClient.appId)
                .header("X-AppKey", KCosClient.appKey)
                .header("X-UserId", KCosClient.userId.toString())
                .post(requestBody)
                .build()

            try {
                KCosClient.httpClient.newCall(request).execute().use { resp ->
                    KCosClient.jsonSerializer.decodeFromStream(resp.body!!.byteStream())
                }
            } catch (e: Exception) {
                Log.e("KCos.createFileEntry", e.stackTraceToString())
                throw e
            }
        }
    }
}