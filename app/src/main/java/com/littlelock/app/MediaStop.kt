package com.littlelock.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * When the screen goes off, whatever was playing is stopped: apps like YouTube Kids keep playing with the screen dark.
 * Two quiet nudges that need no permission - the "pause" and "stop" media keys (what a headset button sends), and taking the
 * audio focus for a moment, which well-behaved players answer by stopping. Both are repeated once, a second later, for players that
 * were slow to notice.
 */
object MediaStop {

    private val main = Handler(Looper.getMainLooper())

    fun stopPlaying(context: Context) {
        val app = context.applicationContext
        nudge(app)
        main.postDelayed({ nudge(app) }, 1200)
    }

    private fun nudge(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        try {
            for (code in intArrayOf(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP)) {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        } catch (_: Exception) {
        }
        try {
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (am.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                main.postDelayed({ am.abandonAudioFocusRequest(focus) }, 1500)
            }
        } catch (_: Exception) {
        }
    }
}
