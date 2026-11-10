package app.airinput.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportsTest {
    @Test
    fun mouseReportClampsAxes() {
        val bytes = MouseReport(buttons = MouseReport.BUTTON_LEFT, dx = 500, dy = -500, wheel = 3).toBytes()
        assertEquals(4, bytes.size)
        assertEquals(1.toByte(), bytes[0])
        assertEquals(127.toByte(), bytes[1])
        assertEquals((-127).toByte(), bytes[2])
        assertEquals(3.toByte(), bytes[3])
    }

    @Test
    fun keyboardReleaseIsAllZero() {
        val bytes = KeyboardReport.release().toBytes()
        assertEquals(8, bytes.size)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun keyboardEncodesSingleKey() {
        val bytes = KeyboardReport(keyCodes = listOf(KeyboardReport.KEY_ENTER)).toBytes()
        assertEquals(KeyboardReport.KEY_ENTER.toByte(), bytes[2])
    }

    @Test
    fun keyboardEncodesNavigationAndEditKeys() {
        val codes = listOf(
            KeyboardReport.KEY_UP,
            KeyboardReport.KEY_DOWN,
            KeyboardReport.KEY_LEFT,
            KeyboardReport.KEY_RIGHT,
            KeyboardReport.KEY_ESC,
            KeyboardReport.KEY_DELETE,
            KeyboardReport.KEY_ENTER,
        )
        for (code in codes) {
            val bytes = KeyboardReport(keyCodes = listOf(code)).toBytes()
            assertEquals(code.toByte(), bytes[2])
            assertTrue("HID usage $code must fit descriptor max 0x65", code in 1..0x65)
        }
    }

    @Test
    fun descriptorIsNonEmpty() {
        assertTrue(HidReportDescriptor.BYTES.size > 50)
    }
}
