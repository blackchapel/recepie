package com.blackchapel.recipai.platform.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.blackchapel.recipai.core.PhotoStore
import java.io.File

@Composable
fun rememberPhotoStore(): PhotoStore =
    remember { PhotoStore(File(System.getProperty("java.io.tmpdir"), "recipai-desktop-check/photos")) }
