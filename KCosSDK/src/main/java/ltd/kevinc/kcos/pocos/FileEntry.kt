package ltd.kevinc.kcos.pocos

import kotlinx.serialization.Serializable

/**
 * 关于文件上传必须的元信息，大部分值都有默认，可按需进行设置
 * @param deadLine 表示文件的过期时间，使用ISO8601格式的时间戳
 * @param protection 表示文件的保护等级。0，文件可公有读；1，域内可见（同一App下可见）；2，文件仅上传者自己可看；3，提供访问密码后可见；4，向指定地址构造请求后可见，具体参见文档
 * @param securityPayload 对应protection里面的3，4情况，如果需要密码，就把密码写在这里，需要构造请求，就写url在这里
 */
@Serializable
data class CreateFileEntryRequest(
    val path: String = "/",
    val fileNameWithExt: String = "file.unknown",
    val fileSize: Long,
    val sha256: String,
    val mimeType: String = "application/octet-stream",
    val deadLine: String = "9999-12-31T23:59:59.999+08:00",
    val protection: Int = 0,
    val securityPayload: String = ""
)

@Serializable
data class CreateFileEntryReply(
    val id: Long = 0L,
    val fileSize: Long = 0L,
    val frames: Long = 0L,
    val nextRequestedFrame: Long = 0L
)
