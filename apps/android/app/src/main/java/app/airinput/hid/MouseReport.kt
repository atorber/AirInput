package app.airinput.hid

/** 4-byte mouse report after Report ID is prepended by the sender. */
data class MouseReport(
    val buttons: Int = 0,
    val dx: Int = 0,
    val dy: Int = 0,
    val wheel: Int = 0,
) {
    fun toBytes(): ByteArray {
        require(buttons in 0..7) { "buttons out of range" }
        return byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte(),
        )
    }

    companion object {
        const val REPORT_ID: Byte = 0x01
        const val BUTTON_LEFT = 0x01
        const val BUTTON_RIGHT = 0x02
        const val BUTTON_MIDDLE = 0x04
    }
}
