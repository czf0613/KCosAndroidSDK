package ltd.kevinc.kcos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream
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
}