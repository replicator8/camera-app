package com.example.mediacontent

import java.io.File

data class MediaItem(
    val file: File,
    val isVideo: Boolean,
    val date: String
)