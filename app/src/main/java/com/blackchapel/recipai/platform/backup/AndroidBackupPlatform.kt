package com.blackchapel.recipai.platform.backup

import android.content.Context
import com.blackchapel.recipai.BuildConfig
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupStatusStore
import com.blackchapel.recipai.core.HttpTransport
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.RecipeJson
import com.blackchapel.recipai.core.UrlConnectionTransport
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.DataStoreSettings
import com.blackchapel.recipai.platform.storage.RecipeRuntime
import com.blackchapel.recipai.ui.backup.AuthOutcome
import com.blackchapel.recipai.ui.backup.BackupJobs
import com.blackchapel.recipai.ui.backup.BackupPlatform
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime
import kotlin.coroutines.resume

/** Google Drive access limited to the files this app creates (a non-sensitive scope). */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * One [BackupJobs] per process, shared by the activity's ViewModel and the
 * background [BackupWorker], so they never back up at the same time and
 * both see the same progress and status.
 */
object BackupRuntime {
    @Volatile
    private var instance: BackupJobs? = null

    fun jobs(context: Context): BackupJobs =
        instance ?: synchronized(this) {
            instance ?: BackupJobs(AndroidBackupPlatform(context.applicationContext)).also { instance = it }
        }
}

/**
 * Backups on Android: Google authorization through Play services'
 * AuthorizationClient (no sign-in SDK, no stored tokens), Drive over
 * HttpURLConnection, files in app-private storage, WorkManager schedule.
 */
class AndroidBackupPlatform(context: Context) : BackupPlatform {
    private val app: Context = context.applicationContext

    override val status = BackupStatusStore(File(app.filesDir, "backup-status.json"))
    override val http: HttpTransport = UrlConnectionTransport()
    override val photoDir = File(app.filesDir, "photos")
    override val workDir = File(app.cacheDir, "backup")
    override val appVersion: String = BuildConfig.VERSION_NAME

    override suspend fun authorize(): AuthOutcome = suspendCancellableCoroutine { cont ->
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            Identity.getAuthorizationClient(app)
                .authorize(request)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(outcomeOf(result)) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resume(failureOf(e)) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(failureOf(e))
        }
    }

    override suspend fun clearToken(token: String) {
        withContext(Dispatchers.IO) {
            try {
                // GoogleAuthUtil.clearToken(context, token), called by name: it lives in
                // play-services-auth-base, which play-services-auth always brings at
                // runtime, but whose compile-time visibility the build shouldn't rely on.
                Class.forName("com.google.android.gms.auth.GoogleAuthUtil")
                    .getMethod("clearToken", Context::class.java, String::class.java)
                    .invoke(null, app, token)
            } catch (e: Exception) {
                // Best effort: an expired token is replaced by Play services on its own.
            }
        }
    }

    override fun schedule(frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime) {
        BackupScheduler.schedule(app, frequency, isoWeekday, hour, wifiOnly, now)
    }

    override fun cancelSchedule() {
        BackupScheduler.cancel(app)
    }

    override fun runSoon(wifiOnly: Boolean) {
        BackupScheduler.runSoon(app, wifiOnly)
    }

    override suspend fun savedRecipes(): List<Recipe>? = withContext(Dispatchers.IO) {
        val file = File(app.filesDir, RecipeRuntime.FILE_NAME)
        if (!file.isFile) return@withContext null
        try {
            RecipeJson.decode(file.readText())
        } catch (e: Exception) {
            // An unreadable file is the repository's to set aside; never back it up.
            null
        }
    }

    override suspend fun savedSettings(): AppSettings = DataStoreSettings(app).settings.first()
}

/** A successful authorization: a token, or Google's screen to show first. */
internal fun outcomeOf(result: AuthorizationResult): AuthOutcome {
    val pending = result.pendingIntent
    if (result.hasResolution() && pending != null) return AuthOutcome.NeedsConsent(pending)
    val token = result.accessToken
    return if (token.isNullOrEmpty()) {
        AuthOutcome.Failed("Google didn't grant access to Drive. Try connecting again.")
    } else {
        AuthOutcome.Granted(token)
    }
}

/** A failed authorization as a user-facing outcome. */
internal fun failureOf(e: Exception): AuthOutcome = when (val code = (e as? ApiException)?.statusCode) {
    CommonStatusCodes.CANCELED -> AuthOutcome.Cancelled
    CommonStatusCodes.NETWORK_ERROR -> AuthOutcome.Failed("Couldn't reach Google. Check your connection and try again.")
    CommonStatusCodes.DEVELOPER_ERROR ->
        AuthOutcome.Failed("Google sign-in isn't set up for this build of Recipai yet (error 10: no OAuth client matches its package and signing key).")
    null -> AuthOutcome.Failed("Couldn't connect to Google. Try again.")
    else -> AuthOutcome.Failed("Couldn't connect to Google (error $code). Try again.")
}
