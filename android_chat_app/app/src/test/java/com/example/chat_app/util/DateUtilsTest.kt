package com.example.chat_app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar // For creating specific test dates if needed

class DateUtilsTest {
    @Test
    fun formatTimestampToTime_isCorrect() {
        // Example: Test for 10:30 AM for a specific date
        // Note: This test is timezone-dependent. For more robust tests, mock Calendar or use a library.
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis
        assertEquals("10:30", DateUtils.formatTimestampToTime(timestamp))

        // Test for 00:00 (midnight)
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("00:00", DateUtils.formatTimestampToTime(calendar.timeInMillis))
        
        // Test for 23:59
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        assertEquals("23:59", DateUtils.formatTimestampToTime(calendar.timeInMillis))

        // Test for 05:05
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 5)
        }
        assertEquals("05:05", DateUtils.formatTimestampToTime(calendar.timeInMillis))

        // Test for 12:00 PM (noon)
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("12:00", DateUtils.formatTimestampToTime(calendar.timeInMillis))
    }
}
