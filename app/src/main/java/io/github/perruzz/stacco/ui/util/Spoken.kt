package io.github.perruzz.stacco.ui.util

import android.content.res.Resources
import io.github.perruzz.stacco.R
import kotlin.math.abs

/** A duration as TalkBack should say it: "1 ora e 30 minuti" rather than "1h 30m". */
fun spokenDuration(resources: Resources, minutes: Int, signed: Boolean = false): String {
    val total = abs(minutes)
    val h = total / 60
    val m = total % 60
    val hours = resources.getQuantityString(R.plurals.a11y_hours, h, h)
    val mins = resources.getQuantityString(R.plurals.a11y_minutes, m, m)
    val text = when {
        h > 0 && m > 0 -> resources.getString(R.string.a11y_and, hours, mins)
        h > 0 -> hours
        else -> mins
    }
    return when {
        !signed || minutes == 0 -> text
        minutes > 0 -> resources.getString(R.string.a11y_plus, text)
        else -> resources.getString(R.string.a11y_minus, text)
    }
}
