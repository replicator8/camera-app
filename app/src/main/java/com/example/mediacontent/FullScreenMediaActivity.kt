package com.example.mediacontent

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.example.mediacontent.databinding.ActivityFullScreenMediaBinding
import java.io.File

class FullScreenMediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenMediaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        val path = intent.getStringExtra("media_path") ?: finish().let { return }
        val isVideo = intent.getBooleanExtra("is_video", false)
        val file = File(path)

        if (!file.exists()) {
            finish()
            return
        }

        if (isVideo) {
            binding.videoView.visibility = View.VISIBLE
            binding.imageView.visibility = View.GONE
            binding.videoView.setVideoPath(file.absolutePath)
            binding.videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                binding.videoView.start()
            }
        } else {
            binding.imageView.visibility = View.VISIBLE
            binding.videoView.visibility = View.GONE
            binding.imageView.setImageURI(Uri.fromFile(file))
        }

        binding.btnDelete.setOnClickListener {
            if (file.delete()) {
                Toast.makeText(this, "Файл удален!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Ошибка удаления!", Toast.LENGTH_SHORT).show()
            }
        }

//        binding.root.setOnClickListener { finish() }
    }
}