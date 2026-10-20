package com.blackchapel.recipai.platform.net

import com.blackchapel.recipai.core.UpdateChecker
import com.blackchapel.recipai.core.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Same API as app/.../platform/net/UpdateFetcher.kt, without android.net.TrafficStats.
object UpdateFetcher {
    private val checker = UpdateChecker()

    suspend fun check(installedVersionName: String): UpdateResult = withContext(Dispatchers.IO) { checker.check(installedVersionName) }
}
