package ltd.kevinc.cos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ltd.kevinc.kcos.KCosClient
import ltd.kevinc.kcos.KCosFileUploader
import ltd.kevinc.kcos.KCosProcessDelegate
import ltd.kevinc.kcos.R
import ltd.kevinc.kcos.pocos.CreateFileEntryRequest
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
                "20b332a9-35a9-0b09-0bf2-79421bde4a54",
                "e8c688ac-7a6e-1add-1a70-564a02fce624",
                "1919810"
            )

            println(userId)

            val uploader = KCosFileUploader()

            println(file.length())

            uploader.uploadData(
                file.toUri(),
                this@MainActivity,
                CreateFileEntryRequest(
                    path = "/czf0613/soft/",
                    fileNameWithExt = "fake.file",
                    fileSize = file.length(),
                    deadLine = "2022-12-31"
                ),
                object : KCosProcessDelegate {
                    override fun onUploadTick(currentStep: Long, totalSteps: Long) {
                        println("上传进度：$currentStep / $totalSteps")
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                    }
                })

//            uploader.continueUpload(36, file, object : KCosProcessDelegate {
//                override fun onContinueUploadTick(currentStep: Long) {
//                    println(currentStep)
//                }
//            })
        }
    }
}