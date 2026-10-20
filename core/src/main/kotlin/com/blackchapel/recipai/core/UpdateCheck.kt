package com.blackchapel.recipai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * A release number as the Production Release workflow writes it:
 * `MAJOR.MINOR.PATCH`, tagged `v<number>`. Test builds are named like
 * `0.1-branch.sha`; their number is the part before the dash, with patch 0,
 * so a test build is only told about a release that is newer than its base.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        private val pattern = Regex("""^v?(\d{1,6})\.(\d{1,6})(?:\.(\d{1,6}))?(?![\d.]).*$""")

        /** Reads "0.1.3", "v0.1.3" or "0.1-feature-x.4296394"; null for anything else. */
        fun parse(text: String): AppVersion? {
            val m = pattern.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = m.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}

/** Why the check could not tell. */
enum class UpdateFailure { OFFLINE, RATE_LIMITED, FAILED }

sealed interface UpdateResult {
    /** The newest release is not newer than the installed version, or there is no release yet. */
    data object UpToDate : UpdateResult

    /** [name] is the release's number (its tag); [url] is its page on GitHub. */
    data class Available(val name: String, val url: String) : UpdateResult

    data class Failed(val reason: UpdateFailure) : UpdateResult
}

@Serializable
private data class ReleaseJson(
    @SerialName("tag_name") val tag: String = "",
    @SerialName("html_url") val url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * Asks GitHub for the newest release of [repo] ("owner/name") and compares it
 * with the installed version. Anonymous and read-only: it needs a public
 * repository, and GitHub allows 60 such requests an hour per network.
 * Blocking; the app calls it off the main thread.
 */
class UpdateChecker(
    private val transport: HttpTransport = UrlConnectionTransport(connectTimeoutMs = 10_000, readTimeoutMs = 15_000),
    private val repo: String = REPO,
) {
    fun check(installedVersionName: String): UpdateResult {
        val installed = AppVersion.parse(installedVersionName) ?: return UpdateResult.Failed(UpdateFailure.FAILED)
        val response = try {
            transport.execute(
                HttpRequest(
                    "GET",
                    "https://api.github.com/repos/$repo/releases/latest",
                    headers = mapOf(
                        "Accept" to "application/vnd.github+json",
                        "X-GitHub-Api-Version" to "2022-11-28",
                        "User-Agent" to "Recipai-update-check",
                    ),
                ),
            ) { it.code to if (it.isSuccess) it.text() else "" }
        } catch (e: IOException) {
            return UpdateResult.Failed(UpdateFailure.OFFLINE)
        }
        val (code, body) = response
        return when {
            code == 404 -> UpdateResult.UpToDate // no release published yet
            code == 403 || code == 429 -> UpdateResult.Failed(UpdateFailure.RATE_LIMITED)
            code !in 200..299 -> UpdateResult.Failed(UpdateFailure.FAILED)
            else -> decide(installed, body)
        }
    }

    private fun decide(installed: AppVersion, body: String): UpdateResult {
        val release = try {
            json.decodeFromString(ReleaseJson.serializer(), body)
        } catch (e: SerializationException) {
            return UpdateResult.Failed(UpdateFailure.FAILED)
        }
        if (release.draft || release.prerelease) return UpdateResult.UpToDate
        val latest = AppVersion.parse(release.tag) ?: return UpdateResult.Failed(UpdateFailure.FAILED)
        if (latest <= installed) return UpdateResult.UpToDate
        return UpdateResult.Available(release.tag.removePrefix("v"), releasePageUrl(release.url))
    }

    /** The release's own page, if GitHub sent a github.com address; else the releases page. */
    private fun releasePageUrl(sent: String): String =
        if (sent.startsWith("https://github.com/")) sent else "https://github.com/$repo/releases/latest"

    companion object {
        /** The public repository that publishes the signed APKs. */
        const val REPO = "blackchapel/recipai"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
