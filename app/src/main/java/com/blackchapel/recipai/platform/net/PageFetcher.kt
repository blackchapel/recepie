package com.blackchapel.recipai.platform.net

import android.net.TrafficStats
import com.blackchapel.recipai.core.FetchAbort
import com.blackchapel.recipai.core.FetchResult
import com.blackchapel.recipai.core.WebPageFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

    /**
     * A blocking socket read ignores coroutine cancellation, so when the
     * caller is cancelled (the link was cleared, or another one converted)
     * the fetch is aborted: that closes the socket and frees the IO thread
     * at once instead of leaving it on a slow server. The fetcher's own
     * overall deadline covers a server that never ends.
     */
    suspend fun fetch(url: String): FetchResult = coroutineScope {
        val abort = FetchAbort()
        val work = async(Dispatchers.IO) {
            TrafficStats.setThreadStatsTag(SOCKET_TAG)
            try {
                fetcher.fetch(url, abort)
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            abort.abort()
            throw e
        }
    }
}
