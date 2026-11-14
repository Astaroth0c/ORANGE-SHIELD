package com.orangeshield.app.engine

import com.orangeshield.app.data.CallEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignAnalyzerTest {
    @Test
    fun groupsMultipleNumbersFromSameDddInsideWindow() {
        val now = 1_800_000_000_000L
        val numbers = listOf(
            "+5543996039726",
            "+5543997039726",
            "+5543998039726"
        )
        val events = numbers.flatMap { number ->
            listOf(
                event(number, now - 10 * 60_000L),
                event(number, now - 5 * 60_000L)
            )
        }

        val findings = detectCampaigns(events, now)
        assertEquals(1, findings.size)
        assertEquals("43", findings.first().ddd)
        assertEquals(3, findings.first().distinctNumbers)
        assertEquals(6, findings.first().totalCalls)
        assertEquals("BAIXA", findings.first().confidence)
    }

    @Test
    fun doesNotCreateCampaignFromOnlyTwoNumbers() {
        val now = 1_800_000_000_000L
        val events = listOf(
            event("+5543996039726", now - 1_000L),
            event("+5543997039726", now - 2_000L),
            event("+5543996039726", now - 3_000L),
            event("+5543997039726", now - 4_000L),
            event("+5543996039726", now - 5_000L)
        )

        assertTrue(detectCampaigns(events, now).isEmpty())
    }

    private fun event(number: String, at: Long) = CallEventEntity(
        e164 = number,
        decision = "WARN",
        riskScore = 30,
        reason = "test event",
        ruleVersion = "2.1.0",
        occurredAt = at
    )
}
