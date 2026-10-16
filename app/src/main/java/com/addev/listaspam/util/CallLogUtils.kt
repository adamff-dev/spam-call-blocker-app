// CallLogUtils.kt
package com.addev.listaspam.util

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import java.util.*

data class CallLogEntry(
    val number: String,
    val type: Int,
    val date: Date,
    val duration: Long
)

fun getCallLogs(context: Context): List<CallLogEntry> {
    val callLogs = mutableListOf<CallLogEntry>()

    val selection = "${CallLog.Calls.TYPE} IN (${CallLog.Calls.INCOMING_TYPE}, ${CallLog.Calls.OUTGOING_TYPE}, ${CallLog.Calls.REJECTED_TYPE}, ${CallLog.Calls.BLOCKED_TYPE}, ${CallLog.Calls.MISSED_TYPE})"

    val cursor: Cursor? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        null,
        selection,
        null,
        CallLog.Calls.DATE + " DESC"
    )

    cursor?.use {
        val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
        val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
        val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
        val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
        if (typeIndex < 0 || dateIndex < 0) return callLogs

        while (it.moveToNext()) {
            // Hidden/private callers are stored with a null number on many devices
            val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
            val type = it.getInt(typeIndex)
            val date = Date(it.getLong(dateIndex))
            val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
            callLogs.add(CallLogEntry(number, type, date, duration))
        }
    }

    return callLogs
}
