package ltd.kevinc.kcos.pocos

import kotlinx.serialization.Serializable

/**
 * @param showSubDirectory 目前这个接口默认false，因为一些技术原因导致列出子目录的效率非常之差，暂时先不提供
 */
@Serializable
data class ListDirectoryRequest(val path: String, val showSubDirectory: Boolean)

@Serializable
data class ListDirectoryReply(
    val fileEntries: List<FileDownloadEntry>,
    val directories: List<String>
)

@Serializable
data class FileDownloadEntry(
    val id: Long,
    val fileName: String,
    val fileSize: Long,
    val downloadUrl: String
)