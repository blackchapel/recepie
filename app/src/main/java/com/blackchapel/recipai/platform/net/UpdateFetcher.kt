package com.blackchapel.recipai.platform.net

import android.net.TrafficStats
import com.blackchapel.recipai.core.UpdateChecker
import com.blackchapel.recipai.core.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the GitHub release check ([UpdateChecker], unit-tested in :core) off
 * the main thread, with our sockets tagged in network stats like the page
 * fetcher's.
 */
object UpdateFetcher {
    private const val SOCKET_TAG = 0x52435050

    private val checker = UpdateChecker()

    suspend fun check(installedVersionName: String): UpdateResult = withContext(Dispatchers.IO) {
        TrafficStats.setThreadStatsTag(SOCKET_TAG)
        try {
            checker.check(installedVersionName)
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }
}
