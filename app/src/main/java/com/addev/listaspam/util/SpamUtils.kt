package com.addev.listaspam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.addev.listaspam.ListaSpamApp
import com.addev.listaspam.R
import com.addev.listaspam.privateContact.ContactType
import com.addev.listaspam.privateContact.PrivateContact
import com.addev.listaspam.privateContact.normalizePhone
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class for handling spam number checks and notifications.
 */
class SpamUtils {

    companion object {
        object VerificationStatus {
            const val FAILED = 2
        }
    }

    private val phoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * Extracts the raw phone number from the call details.
     * @param details Details of the incoming call.
     * @return Raw phone number as a String.
     */
    private fun getRawPhoneNumber(details: Call.Details): String? {
        return when {
            details.handle != null -> details.handle.schemeSpecificPart
            details.gatewayInfo?.originalAddress != null -> details.gatewayInfo.originalAddress.schemeSpecificPart
            details.intentExtras != null -> {
                val uri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        details.intentExtras.getParcelable(
                            TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                            Uri::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        details.intentExtras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
                    }
                uri?.schemeSpecificPart
            }

            else -> null
        }
    }

    /**
     * Checks if a number matches a pattern that may contain wildcards.
     * Pattern examples:
     * - +33162* (starts with +33162)
     * - *98 (ends with 98)
     * - 213*134 (starts with 213 and ends with 134)
     * - *454* (contains 454 anywhere in between)
     *
     * @param number The phone number to check
     * @param pattern The pattern to match against
     * @return true if the number matches the pattern
     */
    private fun matchesPattern(number: String, pattern: String): Boolean {
        if (pattern.isEmpty() || number.isEmpty()) return false

        val parts = pattern.split("*")
        // If there are no wildcards, do exact match
        if (parts.size == 1) {
            return number == pattern
        }

        var currentIndex = 0

        // If pattern starts with wildcard, skip empty prefix
        if (parts.first().isNotEmpty()) {
            if (!number.startsWith(parts.first())) return false
            currentIndex += parts.first().length
        }

        // If pattern ends with wildcard, skip empty suffix
        val lastIndex = parts.size - 1
        if (parts.last().isNotEmpty()) {
            if (!number.endsWith(parts.last())) return false
        }

        // Check all middle parts (must appear in order)
        for (i in 1 until lastIndex) {
            val part = parts[i]
            if (part.isEmpty()) continue
            val foundIdx = number.indexOf(part, currentIndex)
            if (foundIdx == -1) return false
            currentIndex = foundIdx + part.length
        }

        return true
    }

    /**
     * Checks if a given phone number is spam by checking local blocklist and online databases.
     *
     * @param context The application context.
     * @param phoneNumber The phone number to check.
     * @param details Call details
     * @param callback A function to be called with the result (true if spam, false otherwise).
     */
    fun checkSpamNumber(
        context: Context,
        phoneNumber: String?,
        details: Call.Details?,
        callback: (isSpam: Boolean) -> Unit = {}
    ) {
        (context.applicationContext as? ListaSpamApp)?.let { app ->
            app.appScope.launch(Dispatchers.IO) {
                performSpamCheck(context, phoneNumber, details, callback)
            }
        } ?: run {
            callback(false)
        }
    }

    private suspend fun performSpamCheck(
        context: Context,
        phoneNumber: String?,
        details: Call.Details?,
        callback: (isSpam: Boolean) -> Unit
    ) {
        if (!isBlockingEnabled(context)) {
            showToast(context, context.getString(R.string.blocking_disabled), Toast.LENGTH_LONG)
            callback(false)
            return
        }

        val number = if (details != null) getRawPhoneNumber(details) else phoneNumber;

        if (number.isNullOrBlank()) {
            if (shouldBlockHiddenNumbers(context)) {
                handleSpamNumber(
                    context,
                    "",
                    false,
                    context.getString(R.string.block_hidden_number),
                    callback
                )
            } else {
                callback(false)
            }
            return
        }

        // Check whitelist first - if whitelisted, always allow
        if (isNumberWhitelisted(number)) {
            callback(false)
            return
        }

        // End call if the number is already blocked
        if (isNumberBlocked(number)) {
            handleSpamNumber(
                context,
                number,
                false,
                context.getString(R.string.block_already_blocked_number),
                callback
            )
            return
        }

        // Don't check number if is in contacts
        val isNumberInAgenda = isNumberInAgenda(context, number)
        if (isNumberInAgenda) {
            callback(false)
            return
        }

        if (shouldBlockNonContacts(context)) {
            handleSpamNumber(
                context,
                number,
                false,
                context.getString(R.string.block_non_contact),
                callback
            )
            return
        }

        if (isPatternBlockingEnabled(context)) {
            val patterns = getBlockedPatterns(context)
            if (patterns.any { matchesPattern(number, it) }) {
                handleSpamNumber(
                    context,
                    number,
                    false,
                    context.getString(R.string.block_pattern_match),
                    callback
                )
                return
            }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            shouldFilterWithStirShaken(context) &&
            details?.callerNumberVerificationStatus == VerificationStatus.FAILED
        ) {
            handleSpamNumber(
                context,
                number,
                false,
                context.getString(R.string.block_stir_shaken_risk),
                callback
            )
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            && shouldBlockInternationalNumbers(context)
            && isInternationalCall(context, number)
        ) {
            handleSpamNumber(
                context,
                number,
                false,
                context.getString(R.string.block_international_call),
                callback
            )
            return
        }

        val spamCheckers = buildSpamCheckers(context)
        if (spamCheckers.isNotEmpty()) {
            val isSpam = isSpamRace(spamCheckers, number, timeoutMs = 5000)
            if (isSpam) {
                handleSpamNumber(
                    context,
                    number,
                    true,
                    context.getString(R.string.block_spam_number),
                    callback
                )
                return
            }
        }
        callback(false)
    }

    private fun isNumberBlocked(number: String): Boolean {
        return ListaSpamApp.get().contactsCache.value[normalize(number)]?.type == ContactType.BLOCK.value
    }

    private fun isNumberWhitelisted(number: String): Boolean {
        return ListaSpamApp.get().contactsCache.value[normalize(number)]?.type == ContactType.WHITELIST.value
    }

    fun normalize(number: String): String {
        return number.normalizePhone()
    }
    /**
     * Performs a "race" among multiple spam checkers to determine if a phone number is spam.
     *
     * Each checker is a suspend function that returns `true` if the number is spam.
     * The function returns `true` as soon as the first checker reports spam.
     * If all checkers finish and none report spam, it returns `false`.
     * A timeout can be provided to handle long-running or stuck checkers.
     *
     * This function launches all checkers concurrently and cancels remaining jobs
     * as soon as a result is determined, to save resources.
     *
     * @param spamCheckers A list of suspend functions that each take a phone number
     *                     and return `true` if it is spam.
     * @param number The phone number to check for spam.
     * @param timeoutMs Maximum time in milliseconds to wait for a result before returning `false`.
     *                  Default is 5000ms.
     *
     * @return `true` if any checker reports spam, `false` if none report spam or timeout occurs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun isSpamRace(
        spamCheckers: List<suspend (String) -> Boolean>,
        number: String,
        timeoutMs: Long = 5000
    ): Boolean = coroutineScope {
        if (spamCheckers.isEmpty()) return@coroutineScope false

        val resultChannel = Channel<Boolean>(capacity = Channel.UNLIMITED)
        val remaining = AtomicInteger(spamCheckers.size)

        val jobs = spamCheckers.map { checker ->
            launch {
                val result = runCatching { checker(number) }.getOrDefault(false)

                if (result) {
                    resultChannel.send(true)
                } else if (remaining.decrementAndGet() == 0) {
                    resultChannel.close()
                }
            }
        }

        val isSpam = try {
            select {
                resultChannel.onReceiveCatching { result ->
                    result.getOrNull() ?: false
                }
                onTimeout(timeoutMs) {
                    false
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
            resultChannel.cancel()
        }

        isSpam
    }

    private fun buildSpamCheckers(context: Context): List<suspend (String) -> Boolean> {
        val spamCheckers = mutableListOf<suspend (String) -> Boolean>()

        val listaSpamApi = shouldFilterWithListaSpamApi(context)
        if (listaSpamApi) {
            spamCheckers.add { number ->
                ApiUtils.checkListaSpamApi(number, getListaSpamApiLang(context) ?: "EN")
            }
        }
        val tellowsApi = shouldFilterWithTellowsApi(context)
        if (tellowsApi) {
            spamCheckers.add { number ->
                ApiUtils.checkTellowsSpamApi(number, getTellowsApiCountry(context) ?: "us")
            }
        }
        val truecallerApi = shouldFilterWithTruecallerApi(context)
        if (truecallerApi) {
            spamCheckers.add { number ->
                ApiUtils.checkTruecallerSpamApi(number, getTruecallerApiCountry(context) ?: "US")
            }
        }
        return spamCheckers
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun isInternationalCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val parsedNumber = phoneNumberUtil.parse(phoneNumber, null) // Safe parsing

            val simCountry = CountryLanguageUtils.getSimCountry(context).uppercase()

            val countryCode = phoneNumberUtil.getCountryCodeForRegion(simCountry)
            parsedNumber.countryCode != countryCode // True if international

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if a phone number exists in the device's contact agenda.
     *
     * This function determines whether a given phone number is associated with
     * a contact stored in the user's address book by querying the contacts database.
     *
     * @param context Context for accessing content resolver
     * @param phoneNumber The phone number to check
     * @return true if the number is found in the contacts, false otherwise
     */
    private fun isNumberInAgenda(context: Context, phoneNumber: String): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ...scraper logic removed...

    /**
     * Handles the scenario when a phone number is identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as spam.
     * @param callback Callback function to handle the result.
     */
   private fun handleSpamNumber(
       context: Context,
       number: String,
       reason: String,
       callback: (isSpam: Boolean) -> Unit
   ) {
       handleSpamNumber(context, number, true, reason, callback)
   }

    /**
     * Handles the scenario when a phone number is identified as spam.
     * @param context Context for accessing resources.
     * @param number Phone number identified as spam.
     * @param callback Callback function to handle the result.
     */
    private fun handleSpamNumber(
        context: Context,
        number: String,
        saveNumber: Boolean,
        reason: String,
        callback: (isSpam: Boolean) -> Unit
    ) {
        showToast(
            context,
            context.getString(R.string.block_reason_long) + " " + reason,
            Toast.LENGTH_LONG
        )

        if (saveNumber) {
            saveSpamNumber(number)
        }
        sendBlockedCallNotification(context, number, reason)
        callback(true)
    }

    private fun saveSpamNumber(number: String) {
        (ListaSpamApp.get().appScope).launch {
            ListaSpamApp.get().repository.insert(
                PrivateContact(
                    name = number,
                    number = number,
                    type = 1
                )
            )
        }
    }

    /**
     * Displays a toast message.
     * @param context Context for displaying the toast.
     * @param message Message to display.
     * @param duration Duration of the toast display.
     */
    private fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }
}