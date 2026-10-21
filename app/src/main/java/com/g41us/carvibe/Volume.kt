package com.g41us.carvibe

import android.content.Context
import android.media.AudioManager

/**
 * One place for volume, used by the home screen and the floating controls.
 * Today it drives Android's media volume. Some head units route sound through
 * their own amplifier chip instead; if this unit does, the vendor's method
 * goes here once the diagnostics report shows what it is.
 */
object Volume {
    data class Level(val level: Int, val max: Int, val muted: Boolean) {
        val fraction get() = if (max <= 0) 0f else level.toFloat() / max
    }

    private fun audio(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun read(ctx: Context): Level {
        val a = audio(ctx)
        return Level(
            a.getStreamVolume(AudioManager.STREAM_MUSIC),
            a.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            a.isStreamMute(AudioManager.STREAM_MUSIC),
        )
    }

    /** [direction] is AudioManager.ADJUST_RAISE, ADJUST_LOWER or ADJUST_TOGGLE_MUTE. */
    fun change(ctx: Context, direction: Int) {
        val before = read(ctx)
        try {
            audio(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Diag.log("volume: ${e.javaClass.simpleName}: ${e.message}")
        }
        val after = read(ctx)
        Diag.log("volume ${name(direction)}: ${before.level}/${before.max}${if (before.muted) " muted" else ""} -> ${after.level}/${after.max}${if (after.muted) " muted" else ""}")
    }

    private fun name(d: Int) = when (d) {
        AudioManager.ADJUST_RAISE -> "up"
        AudioManager.ADJUST_LOWER -> "down"
        AudioManager.ADJUST_TOGGLE_MUTE -> "mute"
        else -> d.toString()
    }
}
