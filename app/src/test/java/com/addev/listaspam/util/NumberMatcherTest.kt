package com.addev.listaspam.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberMatcherTest {

    @Test
    fun nationalPatternMatchesInternationalNumber() {
        // Issue #60: pattern 030389* must block +39030389...
        assertTrue(NumberMatcher.matchesPattern("+39030389123", "030389*", "IT"))
        assertTrue(NumberMatcher.matchesPattern("+39030389123", "+39030389*", "IT"))
        assertTrue(NumberMatcher.matchesPattern("0039030389123", "+39030389*", "IT"))
    }

    @Test
    fun nationalPatternDoesNotMatchForeignNumbers() {
        assertFalse(NumberMatcher.matchesPattern("+44030389123", "030389*", "IT"))
        assertFalse(NumberMatcher.matchesPattern("+39030399123", "030389*", "IT"))
    }

    @Test
    fun patternsWithWildcardsInAnyPosition() {
        assertTrue(NumberMatcher.matchesPattern("+34912345698", "*98", "ES"))
        assertTrue(NumberMatcher.matchesPattern("+34912345698", "*4569*", "ES"))
        assertTrue(NumberMatcher.matchesPattern("+34912345698", "+349*98", "ES"))
        assertFalse(NumberMatcher.matchesPattern("+34912345698", "+349*99", "ES"))
    }

    @Test
    fun prefixAndSuffixCannotOverlap() {
        assertFalse(NumberMatcher.matchesPattern("+34121", "+3412*21", "ES"))
        assertTrue(NumberMatcher.matchesPattern("+3412321", "+3412*21", "ES"))
    }

    @Test
    fun patternWithoutDigitsNeverMatches() {
        assertFalse(NumberMatcher.matchesPattern("+34912345698", "*", "ES"))
    }

    @Test
    fun sameNumberInDifferentFormats() {
        assertTrue(NumberMatcher.numbersMatch("612345678", "+34612345678", "ES"))
        assertTrue(NumberMatcher.numbersMatch("+34 612 34 56 78", "612345678", "ES"))
        assertTrue(NumberMatcher.numbersMatch("0034612345678", "+34612345678", "ES"))
        assertFalse(NumberMatcher.numbersMatch("612345679", "+34612345678", "ES"))
    }

    @Test
    fun brazilianNumbersWithAreaCode() {
        // Issue #28: contacts stored as DDDxxxxxxxxx
        assertTrue(NumberMatcher.numbersMatch("11912345678", "+5511912345678", "BR"))
    }

    @Test
    fun storedPatternsInNumberLists() {
        assertTrue(NumberMatcher.numbersMatch("+33162*", "+33162123456", "FR"))
        assertFalse(NumberMatcher.numbersMatch("+33162*", "+33163123456", "FR"))
    }

    @Test
    fun worksWithoutKnownRegion() {
        assertTrue(NumberMatcher.numbersMatch("+34612345678", "+34612345678", null))
        assertTrue(NumberMatcher.matchesPattern("+34612345678", "+346*", null))
    }
}
