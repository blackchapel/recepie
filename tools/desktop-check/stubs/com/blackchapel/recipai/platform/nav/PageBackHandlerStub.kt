package com.blackchapel.recipai.platform.nav

import androidx.compose.runtime.Composable

@Composable
fun PageBackHandler(
    enabled: Boolean,
    onProgress: suspend (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: (swiped: Boolean) -> Unit,
) {}
