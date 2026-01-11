package com.example.mediacontent

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mediacontent.databinding.ItemMediaBinding

class MediaAdapter(
    private val context: Context,
    private val mediaList: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    class MediaViewHolder(val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = mediaList[position]
        val binding = holder.binding

        binding.typeIcon.setImageResource(
            if (item.isVideo) R.drawable.ic_video_gallery else R.drawable.ic_photo_gallery
        )
        binding.dateText.text = item.date

        if (item.isVideo) {
            Glide.with(binding.mediaThumbnail)
                .load(item.file)
                .frame(1_000_000)
                .centerCrop()
                .placeholder(R.drawable.ic_video_null)
                .error(R.drawable.ic_video_null)
                .into(binding.mediaThumbnail)
        } else {
            Glide.with(binding.mediaThumbnail)
                .load(item.file)
                .centerCrop()
                .placeholder(R.drawable.ic_video_null)
                .error(R.drawable.ic_video_null)
                .into(binding.mediaThumbnail)
        }

        binding.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = mediaList.size
}