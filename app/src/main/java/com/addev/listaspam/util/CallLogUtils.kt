// CallLogUtils.kt
package com.addev.listaspam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import java.util.Date

data class CallLogEntry(
    val number: String,
    val type: Int,
    val date: Date,
    val duration: Long,
    var name: String?
)

private var callLogsCache: List<CallLogEntry>? = null
private var lastCacheTime: Long = 0

fun getCallLogs(context: Context): List<CallLogEntry> {
    val callLogs = mutableListOf<CallLogEntry>()

    if (ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return callLogs
    }

    return try {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.CACHED_NAME
            ),
            "${CallLog.Calls.TYPE} IN (${CallLog.Calls.INCOMING_TYPE}, ${CallLog.Calls.REJECTED_TYPE}, ${CallLog.Calls.BLOCKED_TYPE}, ${CallLog.Calls.MISSED_TYPE})",
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use { c ->
            val numberIndex = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val cacheName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)

            while (c.moveToNext()) {
                try {
                    val number = c.getString(numberIndex)
                    val date = Date(c.getLong(dateIndex))
                    val duration = c.getLong(durationIndex)
                    val type = c.getInt(typeIndex)
                    val name = c.getString(cacheName)

                    callLogs.add(CallLogEntry(number, type, date, duration, name))
                } catch (e: Exception) {
                    continue
                }
            }
        }

        callLogs
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}