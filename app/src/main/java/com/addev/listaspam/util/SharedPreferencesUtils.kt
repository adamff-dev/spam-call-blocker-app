package com.addev.listaspam.util

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.core.content.edit

private fun getPrefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

private fun getBooleanPref(context: Context, key: String, defaultValue: Boolean): Boolean =
    getPrefs(context).getBoolean(key, defaultValue)

private fun getStringPref(context: Context, key: String): String? =
    getPrefs(context).getString(key, null)

private fun setStringPref(context: Context, key: String, value: String) {
    getPrefs(context).edit { putString(key, value) }
}

fun isBlockingEnabled(context: Context): Boolean =
    getBooleanPref(context, "pref_enable_blocking", true)

fun shouldBlockHiddenNumbers(context: Context): Boolean =
    getBooleanPref(context, "pref_block_hidden_numbers", true)

fun shouldBlockInternationalNumbers(context: Context): Boolean =
    getBooleanPref(context, "pref_block_international_numbers", false)

fun shouldFilterWithListaSpamApi(context: Context): Boolean =
    getBooleanPref(context, "pref_filter_lista_spam_api", true)

fun getListaSpamApiLang(context: Context): String? =
    getStringPref(context, "pref_language")?.uppercase()

fun setListaSpamApiLang(context: Context, languageCode: String) =
    setStringPref(context, "pref_language", languageCode.uppercase())

fun shouldFilterWithTellowsApi(context: Context): Boolean =
    getBooleanPref(context, "pref_filter_tellows_api", true)

fun shouldFilterWithTruecallerApi(context: Context): Boolean =
    getBooleanPref(context, "pref_truecaller_api", true)

fun getTellowsApiCountry(context: Context): String? =
    getStringPref(context, "pref_tellows_country")?.uppercase()

fun setTellowsApiCountry(context: Context, countryCode: String) =
    setStringPref(context, "pref_tellows_country", countryCode.lowercase())

fun getTruecallerApiCountry(context: Context): String? =
    getStringPref(context, "pref_truecaller_country")?.uppercase()

fun setTruecallerApiCountry(context: Context, countryCode: String) =
    setStringPref(context, "pref_truecaller_country", countryCode.uppercase())

// ...scraper-related preferences removed...

fun shouldBlockNonContacts(context: Context): Boolean =
    getBooleanPref(context, "pref_block_non_contacts", false)

fun shouldShowNotification(context: Context): Boolean =
    getBooleanPref(context, "pref_show_notification", true)

fun shouldFilterWithStirShaken(context: Context): Boolean =
    getBooleanPref(context, "pref_block_stir_shaken_risk", false)

fun shouldMuteInsteadOfBlocking(context: Context): Boolean =
    getBooleanPref(context, "pref_mute_instead_of_block", false)

fun isPatternBlockingEnabled(context: Context): Boolean =
    getBooleanPref(context, "pref_enable_pattern_blocking", false)

fun getBlockedPatterns(context: Context): Set<String> {
    val patternsStr = getStringPref(context, "pref_pattern_list") ?: ""
    return patternsStr.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

fun isUpdateCheckEnabled(context: Context): Boolean =
    getBooleanPref(context, "pref_enable_update_check", true)


