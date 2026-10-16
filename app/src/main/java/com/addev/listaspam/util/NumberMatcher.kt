package com.addev.listaspam.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Matches phone numbers against stored numbers and wildcard patterns, taking into account that the
 * same number can be written in several ways (+39030389123, 0039030389123, 030389123...).
 *
 * This object has no Android dependencies so it can be unit tested on the JVM.
 */
object NumberMatcher {

    /** Keeps digits and wildcards, preserving a leading '+'. */
    fun clean(raw: String): String {
        val trimmed = raw.trim()
        val body = trimmed.filter { it.isDigit() || it == '*' }
        return if (trimmed.startsWith("+")) "+$body" else body
    }

    /**
     * Returns every equivalent writing of [number]: as typed, international (+CC...), with the
     * "00" prefix, and - if the number belongs to [region] - the national forms (without country
     * code, with and without trunk prefix).
     *
     * @param region ISO 3166-1 alpha-2 region of the user (SIM country). May be null.
     */
    fun variants(number: String, region: String?): Set<String> {
        val cleaned = clean(number)
        val digits = cleaned.removePrefix("+")
        if (digits.isEmpty()) return emptySet()

        val result = linkedSetOf(cleaned, digits)
        val phoneUtil = PhoneNumberUtil.getInstance()
        val userRegion = region?.uppercase()?.takeIf { it.length == 2 }

        val international = when {
            cleaned.startsWith("+") -> cleaned
            digits.startsWith("00") && digits.length > 2 -> "+" + digits.substring(2)
            else -> null
        }

        val parsed = try {
            when {
                international != null -> phoneUtil.parse(international, null)
                userRegion != null -> phoneUtil.parse(digits, userRegion)
                else -> null
            }
        } catch (_: NumberParseException) {
            null
        }

        if (international != null) {
            result += international
            result += international.removePrefix("+")
            result += "00" + international.removePrefix("+")
        }

        if (parsed != null) {
            val countryCode = parsed.countryCode
            val nsn = phoneUtil.getNationalSignificantNumber(parsed)
            result += "+$countryCode$nsn"
            result += "$countryCode$nsn"
            result += "00$countryCode$nsn"

            val userCountryCode = userRegion?.let { phoneUtil.getCountryCodeForRegion(it) } ?: 0
            if (userRegion == null || userCountryCode == 0 || userCountryCode == countryCode) {
                result += nsn
                result += phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                    .filter { it.isDigit() }
            }
        }
        return result
    }

    /** Matches [value] against [pattern], where '*' stands for any sequence of characters. */
    fun matchesWildcard(value: String, pattern: String): Boolean {
        if (!pattern.contains('*')) return value == pattern
        val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex(regex).matches(value)
    }

    /**
     * Checks if [number] matches [pattern]. Patterns may contain '*' wildcards, e.g. `+33162*`,
     * `*98`, `213*134`, `*454*`. The pattern is compared against every equivalent writing of the
     * number, so `030389*` also blocks `+39030389123` for an Italian user.
     */
    fun matchesPattern(number: String, pattern: String, region: String?): Boolean {
        val cleanedPattern = clean(pattern)
        if (cleanedPattern.none { it.isDigit() }) return false
        return variants(number, region).any { matchesWildcard(it, cleanedPattern) }
    }

    /**
     * Checks if [entry] (a stored number, or a pattern if it contains '*') refers to [number].
     */
    fun numbersMatch(entry: String, number: String, region: String?): Boolean {
        if (entry.contains('*')) return matchesPattern(number, entry, region)
        val entryVariants = variants(entry, region)
        if (entryVariants.isEmpty()) return false
        return variants(number, region).any { it in entryVariants }
    }
}
