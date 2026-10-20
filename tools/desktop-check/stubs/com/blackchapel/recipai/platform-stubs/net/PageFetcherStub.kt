package com.blackchapel.recipai.platform.net

import com.blackchapel.recipai.core.FetchResult
import com.blackchapel.recipai.core.WebPageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Same API as app/.../platform/net/PageFetcher.kt, without android.net.TrafficStats.
// Lives in "platform-stubs/", not "platform/": ui/build.gradle.kts excludes
// **/platform/** from every source dir, the stubs dir included.
object PageFetcher {
    private val fetcher = WebPageFetcher(upgradeHttp = true)

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) { fetcher.fetch(url) }
}
