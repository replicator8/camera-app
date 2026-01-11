package com.example.mediacontent.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mediacontent.FullScreenMediaActivity
import com.example.mediacontent.MediaAdapter
import com.example.mediacontent.MediaItem
import com.example.mediacontent.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MediaAdapter
    private val mediaList = mutableListOf<MediaItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadMediaFiles()
    }

    private fun setupRecyclerView() {
        adapter = MediaAdapter(requireContext(), mediaList) { mediaItem ->
            val intent = Intent(requireContext(), FullScreenMediaActivity::class.java).apply {
                putExtra("media_path", mediaItem.file.absolutePath)
                putExtra("is_video", mediaItem.isVideo)
            }
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter
    }

    private fun loadMediaFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val newMediaList = mutableListOf<MediaItem>()

            val photoDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val videoDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES)

            val allFiles = mutableListOf<File>()
            photoDir?.listFiles { file -> file.isFile && file.extension.equals("jpg", ignoreCase = true) }
                ?.forEach { allFiles.add(it) }
            videoDir?.listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
                ?.forEach { allFiles.add(it) }

            val sortedFiles = allFiles.sortedByDescending { it.lastModified() }
            for (file in sortedFiles) {
                val isVideo = file.extension.equals("mp4", ignoreCase = true)
                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(file.lastModified()))
                newMediaList.add(MediaItem(file, isVideo, date))
            }

            withContext(Dispatchers.Main) {
                mediaList.clear()
                mediaList.addAll(newMediaList)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadMediaFiles()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
