package ltd.kevinc.cos

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ltd.kevinc.kcos.KCosClient
import ltd.kevinc.kcos.KCosProcessDelegate
import ltd.kevinc.kcos.KCosUtils
import ltd.kevinc.kcos.R
import java.io.File

class MainActivity : AppCompatActivity() {
    private var cnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val userId = KCosClient.initializeKCosService(
                "20b332a9-35a9-0b09-0bf2-79421bde4a54",
                "e8c688ac-7a6e-1add-1a70-564a02fce624",
                "1919810"
            )

            println(userId)

//            downloader.downloadLargeFile(44)
//                .onStart {
//                    println("开始下载")
//                }
//                .onCompletion {
//                    println("下载完成")
//                }
//                .catch { err ->
//                    println("下载出错")
//                    err.printStackTrace()
//                }
//                .collect { item ->
//                    println(item.size)
//                    cnt += item.size
//                    println("已接受$cnt 字节")
//                }
//            val bin = KCosFileDownloader.downloadSimpleFile(8)
//            println(bin.size)
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            val file = File("${applicationContext.dataDir.path}/fake.dat")
//            file.createNewFile()
//
//            for (i in 0..10240) {
//                file.appendBytes(ByteArray(1023))
//
//                println("writing $i KB")
//            }
//            println(file.length())

//            val stream = assets.open("test_pic.heif")
//            val bitmap = BitmapFactory.decodeStream(stream)
//            println(bitmap.byteCount)
//
            val decoder = KCosUtils()
            Log.i("开始转码", file.path)
            decoder.compressVideo(file.toUri(), this@MainActivity, -1, -1, object :
                KCosProcessDelegate {
                override fun onUploadTick(currentStep: Long, totalSteps: Long) {
                    println("上传进度：$currentStep / $totalSteps")
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }

                override fun onConversionSuccess(targetFile: File) {

                }
            })

//            val uploader = KCosFileUploader()
//
//            println(file.length())

//            uploader.uploadData(
//                file.toUri(),
//                this@MainActivity,
//                CreateFileEntryRequest(
//                    path = "/czf0613/soft/",
//                    fileNameWithExt = "fake.file",
//                    fileSize = file.length(),
//                    deadLine = "2022-12-31"
//                ),
//                object : KCosProcessDelegate {
//                    override fun onUploadTick(currentStep: Long, totalSteps: Long) {
//                        println("上传进度：$currentStep / $totalSteps")
//                    }
//
//                    override fun onError(e: Throwable) {
//                        e.printStackTrace()
//                    }
//                })

//            uploader.continueUpload(36, file, object : KCosProcessDelegate {
//                override fun onContinueUploadTick(currentStep: Long) {
//                    println(currentStep)
//                }
//            })
        }
    }
}