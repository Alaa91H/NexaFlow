package com.nexaflow.core.execution

import android.media.AudioManager

/** Maps the user-facing audio stream name to the Android stream constant. */
object AudioStreams {

    /** All streams a task can control, in display order. */
    val streams = listOf(
        "MUSIC" to AudioManager.STREAM_MUSIC,
        "RING" to AudioManager.STREAM_RING,
        "NOTIFICATION" to AudioManager.STREAM_NOTIFICATION,
        "ALARM" to AudioManager.STREAM_ALARM,
        "VOICE_CALL" to AudioManager.STREAM_VOICE_CALL,
        "SYSTEM" to AudioManager.STREAM_SYSTEM,
        "DTMF" to AudioManager.STREAM_DTMF,
        "ACCESSIBILITY" to AudioManager.STREAM_ACCESSIBILITY
    )

    fun streamId(name: String): Int {
        return streams.firstOrNull { it.first == name }?.second ?: AudioManager.STREAM_MUSIC
    }

    fun streamName(id: Int): String {
        return streams.firstOrNull { it.second == id }?.first ?: "MUSIC"
    }
}
