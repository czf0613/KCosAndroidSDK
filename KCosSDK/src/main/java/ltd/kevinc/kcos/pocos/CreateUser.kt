package ltd.kevinc.kcos.pocos

import kotlinx.serialization.Serializable

@Serializable
data class GetOrCreateUserRequest(val userTag: String)

@Serializable
data class GetOrCreateUserReply(val userTag: String, val userId: Int)