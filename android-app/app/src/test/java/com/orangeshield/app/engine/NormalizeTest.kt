package com.orangeshield.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeTest {

    @Test
    fun removesBrazilianTrunkZeroOnlyAfterValidation() {
        val result = normalizePhoneDetailed("55043996039726")
        assertEquals("+5543996039726", result.e164)
        assertTrue(result.isValid)
        assertEquals("br_trunk_prefix", result.source)
    }

    @Test
    fun acceptsCanonicalBrazilianE164WithoutDuplicatingCountryCode() {
        assertEquals("+5543996039726", normalizePhone("5543996039726"))
        assertEquals("+5543996039726", normalizePhone("+55 43 99603-9726"))
    }

    @Test
    fun acceptsBrazilianNationalTrunkFormat() {
        val result = normalizePhoneDetailed("043996039726")
        assertEquals("+5543996039726", result.e164)
        assertTrue(result.isValid)
    }

    @Test
    fun doesNotConfuseBrazilianDdd55WithCountryCode() {
        val result = normalizePhoneDetailed("55999991234")
        assertEquals("+5555999991234", result.e164)
        assertTrue(result.isValid)
    }

    @Test
    fun doesNotInventBrazilianValidityForMalformedCountryNumber() {
        val result = normalizePhoneDetailed("5500996039726")
        assertFalse(result.isValid)
        assertFalse(result.e164.startsWith("+5555"))
    }

    @Test
    fun preservesExplicitForeignCountryCode() {
        val result = normalizePhoneDetailed("+1 650-253-0000")
        assertEquals("+16502530000", result.e164)
        assertTrue(result.isValid)
    }
}
