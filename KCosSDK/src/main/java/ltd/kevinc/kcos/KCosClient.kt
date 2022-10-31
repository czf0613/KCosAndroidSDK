package ltd.kevinc.kcos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import ltd.kevinc.kcos.pocos.GetOrCreateUserReply
import ltd.kevinc.kcos.pocos.GetOrCreateUserRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Proxy
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
object KCosClient {
    internal val jsonContentType = "application/json;charset=utf-8".toMediaType()
    internal val binaryContentType = "application/octet-stream".toMediaType()
    internal const val urlBase = "https://cos.kevinc.ltd"
    internal val httpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    internal lateinit var appId: String
    internal lateinit var appKey: String
    internal var userId: Int = 0
    internal val jsonSerializer = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 注册SDK的必要信息，返回在KCosService中对应的UserId，此后发起请求都需要依靠它
     * 注意，由于http的反序列化使用了一个实验性API，需要有一个OptIn的注解，日后会逐渐移除
     * @param appId
     * @param appKey
     * @param userTag 用于标识这一个App下的某个用户，只要保证在这个用户下唯一即可
     * @return 返回在SDKService中的UserId，这个UserId是无符号的int32
     */
    suspend fun initializeKCosService(appId: String, appKey: String, userTag: String): Int {
        this.appId = appId
        this.appKey = appKey

        val content = GetOrCreateUserRequest(userTag = userTag)
        val requestBody = jsonSerializer.encodeToString(content).toRequestBody(jsonContentType)
        val request = Request.Builder()
            .url("${urlBase}/user/createAppUser")
            .header("X-AppId", this.appId)
            .header("X-AppKey", this.appKey)
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val resp = httpClient.newCall(request).execute()
                val reply = jsonSerializer.decodeFromStream<GetOrCreateUserReply>(resp.body!!.byteStream())

                this@KCosClient.userId = reply.userId

                reply.userId
            } catch (e: Exception) {
                Log.e("KCos.initSDK", e.stackTraceToString())

                throw e
            }
        }
    }
}