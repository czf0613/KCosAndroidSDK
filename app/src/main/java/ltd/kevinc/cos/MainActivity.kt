package ltd.kevinc.cos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ltd.kevinc.kcos.KCosUtils
import ltd.kevinc.kcos.R
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    private lateinit var button: Button
    private val kcosUtils = KCosUtils()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoView = findViewById(R.id.video_player)
        button = findViewById(R.id.choose_file)
    }

    override fun onStart() {
        super.onStart()

        button.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "video/*"
            this.startActivityForResult(intent, 10086)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 10086 || resultCode != Activity.RESULT_OK)
            return

        lifecycleScope.launch {
            val videoUri = data!!.data!!
            val newVideoFilePath = kcosUtils.compressVideo(videoUri, this@MainActivity, 1920, 1080)
            val file = File(newVideoFilePath)
            videoView.setVideoURI(file.toUri())
            videoView.start()
        }
    }
}