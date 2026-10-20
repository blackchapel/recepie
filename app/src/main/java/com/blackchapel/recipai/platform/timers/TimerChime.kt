package com.blackchapel.recipai.platform.timers

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.blackchapel.recipai.core.Chime

/**
 * Plays the prototype's chime ([Chime], synthesized in :core) on the alarm
 * stream, like a kitchen timer: audible in silent mode, at alarm volume.
 * Only called when Settings > Timer sound is on.
 */
internal object TimerChime {
    private const val RATE = 44_100
    private const val REPEATS = 2
    private val pcm: ShortArray by lazy { Chime.pcm(RATE, REPEATS) }

    fun play() {
        try {
            val data = pcm
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val millis = (Chime.durationSeconds(REPEATS) * 1000).toLong() + 500
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { track.release() } }, millis)
        } catch (e: Exception) {
            // No audio device, or the system refused a track: the
            // notification and the in-app alert still fire.
        }
    }
}
