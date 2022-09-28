package ltd.kevinc.kcos.pocos

import kotlinx.serialization.Serializable

@Serializable
data class UploadReply(val nextRequestedFrame: Int = 0)