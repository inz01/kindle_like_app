// Book.kt

package com.weproz.superreader

import android.graphics.Bitmap
import android.net.Uri

data class Book(
    val uri: Uri,
    val name: String,
    val type: BookType,
    var thumbnail: Bitmap? = null,
    var pageCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val lastOpened: Long = System.currentTimeMillis(),
)