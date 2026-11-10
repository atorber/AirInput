package app.airinput.hid

/** 8-byte keyboard report (modifiers + reserved + 6 keys). */
data class KeyboardReport(
    val modifiers: Int = 0,
    val keyCodes: List<Int> = emptyList(),
) {
    fun toBytes(): ByteArray {
        val keys = keyCodes.take(6).map { it.coerceIn(0, 0x65) }
        val out = ByteArray(8)
        out[0] = modifiers.coerceIn(0, 0xFF).toByte()
        out[1] = 0
        for (i in keys.indices) {
            out[2 + i] = keys[i].toByte()
        }
        return out
    }

    companion object {
        const val REPORT_ID: Byte = 0x02

        // HID modifier bitmask (byte 0)
        const val MOD_LCTRL = 0x01
        const val MOD_LSHIFT = 0x02
        const val MOD_LALT = 0x04
        const val MOD_LMETA = 0x08 // Command on macOS / Win on Windows

        // HID usage IDs (Keyboard/Keypad page, within descriptor 0x00..0x65)
        const val KEY_A = 0x04
        const val KEY_C = 0x06
        const val KEY_F = 0x09
        const val KEY_H = 0x0B
        const val KEY_Q = 0x14
        const val KEY_V = 0x19
        const val KEY_W = 0x1A
        const val KEY_Z = 0x1D
        const val KEY_ENTER = 0x28 // Return / Enter
        const val KEY_ESC = 0x29
        const val KEY_BACKSPACE = 0x2A // ⌫
        const val KEY_TAB = 0x2B
        const val KEY_SPACE = 0x2C
        const val KEY_MINUS = 0x2D
        const val KEY_EQUAL = 0x2E
        const val KEY_F5 = 0x3E
        const val KEY_HOME = 0x4A
        const val KEY_PAGE_UP = 0x4B
        const val KEY_DELETE = 0x4C // Forward Delete ⌦
        const val KEY_END = 0x4D
        const val KEY_PAGE_DOWN = 0x4E
        const val KEY_RIGHT = 0x4F
        const val KEY_LEFT = 0x50
        const val KEY_DOWN = 0x51
        const val KEY_UP = 0x52
        const val KEY_APPLICATION = 0x65 // Context / Menu

        fun release(): KeyboardReport = KeyboardReport()
    }
}
