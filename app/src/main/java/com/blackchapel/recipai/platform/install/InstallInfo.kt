package com.blackchapel.recipai.platform.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Whether this launch is the first one after a brand-new install, as
 * opposed to an update or a phone that already has Recipai data. The
 * welcome tour is only for new installs.
 */
object InstallInfo {
    /** An update is always much later than the install; this only absorbs clock rounding. */
    private const val SAME_INSTANT_MS = 5_000L

    /**
     * True when the package was never updated since it was installed
     * (`firstInstallTime == lastUpdateTime`; every update moves
     * `lastUpdateTime`) and no recipe file exists yet. Both must hold, so
     * a phone that updates from an older build, or one that already has
     * recipes, never sees the tour. Any doubt (the package can't be read)
     * answers false: not showing the tour is the safe mistake.
     */
    fun isFreshInstall(context: Context, recipeFileName: String): Boolean {
        val untouched = try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.lastUpdateTime - info.firstInstallTime < SAME_INSTANT_MS
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: RuntimeException) {
            false
        }
        return untouched && !File(context.applicationContext.filesDir, recipeFileName).exists()
    }
}
