package com.addev.listaspam.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.addev.listaspam.util.SpamUtils
import com.addev.listaspam.util.shouldAnswerAndHangup
import com.addev.listaspam.util.shouldMuteInsteadOfBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Call screening service to identify and block spam calls.
 */
class CallScreeningService : CallScreeningService() {

    private val spamUtils = SpamUtils()

    // Not cancelled in onDestroy: the service is unbound right after responding to the call,
    // but the answer/hang up flow has to outlive it. It is bounded by ANSWER_TIMEOUT_MS.
    private val answerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Called when an incoming call is being screened.
     * @param details Details of the incoming call.
     */
    override fun onScreenCall(details: Call.Details) {
        // Only handle incoming calls
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        spamUtils.checkSpamNumber(this, null, details) { isSpam ->
            if (isSpam) {
                endCall(details)
            } else {
                // Allow the call to proceed normally
                respondToCall(
                    details, CallResponse.Builder()
                        .setDisallowCall(false)
                        .build()
                )
            }
        }
    }

    /**
     * Ends the call by either muting or blocking it based on user preferences.
     * @param details Details of the call to be ended.
     */
    private fun endCall(details: Call.Details) {
        val context = this
        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager

        if (shouldAnswerAndHangup(context) && canAnswerAndHangup()) {
            // Let the call through but silenced so the user is not interrupted. The call is not
            // ringing yet while it is being screened, so it can only be answered once Telecom
            // starts ringing it. Note: CallResponse throws IllegalStateException if an allowed
            // call also asks to skip the call log or the notification.
            respondToCall(
                details, CallResponse.Builder()
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .build()
            )
            answerAndHangUp(telecomManager)
            return
        }

        val shouldMute = shouldMuteInsteadOfBlocking(this)
        if (shouldMute) {
            respondToCall(
                details, CallResponse.Builder()
                    .setSilenceCall(true)
                    .build()
            )
        } else {
            respondToCall(
                details, CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build()
            )
        }
    }

    private fun canAnswerAndHangup(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /**
     * Waits for the silenced call to start ringing, answers it and hangs up as soon as it is
     * connected. Answering (instead of rejecting) prevents the caller from reaching the voicemail.
     */
    @Suppress("DEPRECATION")
    private fun answerAndHangUp(telecomManager: TelecomManager) {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        answerScope.launch {
            val deadline = SystemClock.elapsedRealtime() + ANSWER_TIMEOUT_MS
            var answered = false
            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    val state = telephonyManager.callState
                    if (!answered) {
                        if (state == TelephonyManager.CALL_STATE_RINGING) {
                            telecomManager.acceptRingingCall()
                            answered = true
                        }
                    } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                        telecomManager.endCall()
                        return@launch
                    } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                        // The caller hung up first
                        return@launch
                    }
                    delay(POLL_INTERVAL_MS)
                }
            } catch (_: SecurityException) {
                // Permission revoked while waiting: nothing else we can do for this call
            }
        }
    }

    private companion object {
        const val ANSWER_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
