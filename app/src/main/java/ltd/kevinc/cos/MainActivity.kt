package ltd.kevinc.cos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ltd.kevinc.kcos.KCosClient
import ltd.kevinc.kcos.KCosFileUploader
import ltd.kevinc.kcos.KCosProcessDelegate
import ltd.kevinc.kcos.R
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
            println(file.length())

            val userId = KCosClient.initializeKCosService(
                "f92b2839-73de-498b-93c1-c70f07d8345b",
                "c89a8ab5-14b0-4653-8260-1233d6c042b8",
                "114514"
            )

            println(userId)

            val uploader = KCosFileUploader()

            println(file.length())

//            uploader.uploadData(file,
//                CreateFileEntryRequest(
//                    path = "/czf0613/soft/",
//                    fileNameWithExt = "fake.file",
//                    fileSize = file.length(),
//                    sha256 = "b9af94e6c2a399db5934f8f8e53757a7cffab8ed1a7207e97f4bee1aa02076b6"
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
            uploader.continueUpload(36, file, object : KCosProcessDelegate {
                override fun onContinueUploadTick(currentStep: Long) {
                    println(currentStep)
                }
            })
        }
    }
}