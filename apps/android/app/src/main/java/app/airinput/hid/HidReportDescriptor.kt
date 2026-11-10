package app.airinput.hid

/**
 * Combined Boot Mouse + Boot Keyboard HID report descriptor.
 * Report ID 1 = mouse, Report ID 2 = keyboard.
 */
object HidReportDescriptor {
    val BYTES: ByteArray = byteArrayOf(
        // Mouse
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x02, // Usage (Mouse)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x09, 0x01, // Usage (Pointer)
        0xA1.toByte(), 0x00, // Collection (Physical)
        0x85.toByte(), 0x01, // Report ID (1)
        0x05, 0x09, // Usage Page (Button)
        0x19, 0x01, // Usage Minimum (1)
        0x29, 0x03, // Usage Maximum (3)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95.toByte(), 0x03, // Report Count (3)
        0x75, 0x01, // Report Size (1)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x95.toByte(), 0x01, // Report Count (1)
        0x75, 0x05, // Report Size (5)
        0x81.toByte(), 0x03, // Input (Const,Var,Abs)
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x30, // Usage (X)
        0x09, 0x31, // Usage (Y)
        0x09, 0x38, // Usage (Wheel)
        0x15, 0x81.toByte(), // Logical Minimum (-127)
        0x25, 0x7F, // Logical Maximum (127)
        0x75, 0x08, // Report Size (8)
        0x95.toByte(), 0x03, // Report Count (3)
        0x81.toByte(), 0x06, // Input (Data,Var,Rel)
        0xC0.toByte(), // End Collection
        0xC0.toByte(), // End Collection

        // Keyboard
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x06, // Usage (Keyboard)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), 0x02, // Report ID (2)
        0x05, 0x07, // Usage Page (Key Codes)
        0x19, 0xE0.toByte(), // Usage Minimum (Left Control)
        0x29, 0xE7.toByte(), // Usage Maximum (Right GUI)
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95.toByte(), 0x08,
        0x81.toByte(), 0x02, // Modifier bits
        0x95.toByte(), 0x01,
        0x75, 0x08,
        0x81.toByte(), 0x01, // Reserved
        0x95.toByte(), 0x06,
        0x75, 0x08,
        0x15, 0x00,
        0x25, 0x65,
        0x05, 0x07,
        0x19, 0x00,
        0x29, 0x65,
        0x81.toByte(), 0x00, // Key array
        0xC0.toByte(),
    )
}
