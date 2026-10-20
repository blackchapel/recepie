package com.blackchapel.recipai.platform.timers

import androidx.compose.runtime.Composable
import com.blackchapel.recipai.ui.timers.TimerStore

// Same API as app/.../platform/timers/TimerSystem.kt, without the Android
// service, notifications, audio and window flags.
// Lives in "platform-stubs/", not "platform/": ui/build.gradle.kts excludes
// **/platform/** from every source dir, the stubs dir included.

@Composable
fun TimerSystem(store: TimerStore) {}

@Composable
fun KeepScreenOn(enabled: Boolean) {}
