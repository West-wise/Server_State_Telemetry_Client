package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryFormatTest {
    @Test
    fun networkUsesBytesPerSecondAndExisting1024Scales() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("0 B/s", formatBytesPerSec(0L))
            assertEquals("1023 B/s", formatBytesPerSec(1_023L))
            assertEquals("1.00 KB/s", formatBytesPerSec(1_024L))
            assertEquals("1.50 KB/s", formatBytesPerSec(1_536L))
            assertEquals("1.00 MB/s", formatBytesPerSec(1_048_576L))
            assertEquals("1.00 GB/s", formatBytesPerSec(1_073_741_824L))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun uptimeRollsSecondsIntoMinutesHoursAndDays() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("0d 00:00:00", formatUptime(0L))
            assertEquals("0d 00:00:59", formatUptime(59L))
            assertEquals("0d 00:01:00", formatUptime(60L))
            assertEquals("0d 01:00:00", formatUptime(3_600L))
            assertEquals("1d 00:00:00", formatUptime(86_400L))
            assertEquals("1d 01:01:01", formatUptime(90_061L))
            assertEquals("49710d 06:28:15", formatUptime(0xFFFFFFFFL))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
