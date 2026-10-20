package com.blackchapel.recipai.platform.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.blackchapel.recipai.ui.backup.AuthOutcome
import com.blackchapel.recipai.ui.backup.BackupController
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

/**
 * Shows Google's own account and consent screen when a backup asks for it
 * ([BackupController.consent]) and reports the result back. The launcher
 * survives rotation; a request is launched only once.
 */
@Composable
fun BackupConsentHost(backup: BackupController) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        backup.consentFinished(consentResult(context, result.resultCode, result.data))
    }
    val request = backup.consent
    LaunchedEffect(request) {
        if (request == null || request.launched) return@LaunchedEffect
        val pending = request.payload as? PendingIntent
        if (pending == null) {
            backup.consentFinished(AuthOutcome.Failed("Couldn't open Google sign-in. Try again."))
            return@LaunchedEffect
        }
        request.launched = true
        try {
            launcher.launch(IntentSenderRequest.Builder(pending).build())
        } catch (e: Exception) {
            backup.consentFinished(AuthOutcome.Failed("Couldn't open Google sign-in. Try again."))
        }
    }
}

private fun consentResult(context: Context, resultCode: Int, data: Intent?): AuthOutcome {
    if (resultCode != Activity.RESULT_OK || data == null) return AuthOutcome.Cancelled
    return try {
        outcomeOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        failureOf(e)
    }
}
