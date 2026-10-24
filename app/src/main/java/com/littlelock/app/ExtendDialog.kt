package com.littlelock.app

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

/**
 * "Extend playtime": the parent picks how much longer the tablet stays open (after the PIN). Used on the lock screen and in parent
 * settings. [onPick] gets the moment the tablet locks again.
 */
object ExtendDialog {

    /** The minutes offered, shortest first. */
    val MINUTES = intArrayOf(15, 30, 45, 60, 90, 120, 180)

    fun show(context: Context, onPick: (until: Long) -> Unit) {
        val labels = MINUTES.map { context.getString(R.string.time_extend_option, TimeLimit.durationText(it * 60_000L)) } +
            context.getString(R.string.time_extend_until_option)
        AlertDialog.Builder(context)
            .setTitle(R.string.time_extend_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < MINUTES.size) onPick(System.currentTimeMillis() + MINUTES[which] * 60_000L) else pickClock(context, onPick)
            }
            .setNegativeButton(R.string.setup_cancel, null)
            .show()
    }

    private fun pickClock(context: Context, onPick: (Long) -> Unit) {
        val soon = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
        TimePickerDialog(context, { _, h, m -> onPick(TimeLimit.nextOccurrence(System.currentTimeMillis(), h, m)) },
            soon.get(Calendar.HOUR_OF_DAY), soon.get(Calendar.MINUTE), false).apply {
            setTitle(R.string.time_extend_until_title)
            show()
        }
    }
}
