package io.github.perruzz.stacco.ui.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Holiday
import io.github.perruzz.stacco.domain.StampSlot

val StampSlot.icon: ImageVector
    get() = when (this) {
        StampSlot.ENTRY -> Icons.AutoMirrored.Rounded.Login
        StampSlot.BREAK_OUT -> Icons.Rounded.Restaurant
        StampSlot.BREAK_IN -> Icons.Rounded.WorkHistory
        StampSlot.EXIT -> Icons.AutoMirrored.Rounded.Logout
    }

val StampSlot.label: Int
    get() = when (this) {
        StampSlot.ENTRY -> R.string.slot_entry
        StampSlot.BREAK_OUT -> R.string.slot_break_out
        StampSlot.BREAK_IN -> R.string.slot_break_in
        StampSlot.EXIT -> R.string.slot_exit
    }

val StampSlot.action: Int
    get() = when (this) {
        StampSlot.ENTRY -> R.string.stamp_entry
        StampSlot.BREAK_OUT -> R.string.stamp_break_out
        StampSlot.BREAK_IN -> R.string.stamp_break_in
        StampSlot.EXIT -> R.string.stamp_exit
    }

val StampSlot.stampedMessage: Int
    get() = when (this) {
        StampSlot.ENTRY -> R.string.toast_stamped_entry
        StampSlot.BREAK_OUT -> R.string.toast_stamped_break_out
        StampSlot.BREAK_IN -> R.string.toast_stamped_break_in
        StampSlot.EXIT -> R.string.toast_stamped_exit
    }

val DayType?.icon: ImageVector
    get() = when (this) {
        DayType.OFFICE -> Icons.Rounded.Apartment
        DayType.SMART -> Icons.Rounded.Home
        DayType.TRAVEL -> Icons.Rounded.Luggage
        DayType.VACATION -> Icons.Rounded.BeachAccess
        DayType.SICK -> Icons.Rounded.Healing
        DayType.HOLIDAY -> Icons.Rounded.Celebration
        null -> Icons.Rounded.SelfImprovement
    }

val DayType?.label: Int
    get() = when (this) {
        DayType.OFFICE -> R.string.type_office
        DayType.SMART -> R.string.type_smart
        DayType.TRAVEL -> R.string.type_travel
        DayType.VACATION -> R.string.type_vacation
        DayType.SICK -> R.string.type_sick
        DayType.HOLIDAY -> R.string.type_holiday
        null -> R.string.type_rest
    }

val AutoTypeIcon: ImageVector get() = Icons.Rounded.AutoMode

val Holiday.label: Int
    get() = when (this) {
        Holiday.NEW_YEAR -> R.string.holiday_new_year
        Holiday.EPIPHANY -> R.string.holiday_epiphany
        Holiday.EASTER -> R.string.holiday_easter
        Holiday.EASTER_MONDAY -> R.string.holiday_easter_monday
        Holiday.LIBERATION -> R.string.holiday_liberation
        Holiday.LABOUR -> R.string.holiday_labour
        Holiday.REPUBLIC -> R.string.holiday_republic
        Holiday.ASSUMPTION -> R.string.holiday_assumption
        Holiday.ST_FRANCIS -> R.string.holiday_st_francis
        Holiday.ALL_SAINTS -> R.string.holiday_all_saints
        Holiday.IMMACULATE -> R.string.holiday_immaculate
        Holiday.CHRISTMAS -> R.string.holiday_christmas
        Holiday.ST_STEPHEN -> R.string.holiday_st_stephen
        Holiday.PATRON -> R.string.holiday_patron
    }
