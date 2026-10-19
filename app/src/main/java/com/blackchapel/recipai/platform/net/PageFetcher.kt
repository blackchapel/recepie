package com.blackchapel.recipai.platform.net

import android.net.TrafficStats
import com.blackchapel.recipai.core.FetchResult
import com.blackchapel.recipai.core.WebPageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches a recipe page for link import, off the main thread. The HTTP work
 * (timeouts, size cap, redirects, charset) is [WebPageFetcher] in :core,
 * which is unit-tested on the JVM; this adds the Android bits.
 */
object PageFetcher {
    /** Tags our sockets in network stats (and keeps StrictMode's untagged-socket check quiet). */
    private const val SOCKET_TAG = 0x52435049

    // https only: Android blocks cleartext traffic, so http links and redirects are upgraded.
    private val fetcher = WebPageFetcher(upgradeHttp = true)

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        TrafficStats.setThreadStatsTag(SOCKET_TAG)
        try {
            fetcher.fetch(url)
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }
}
