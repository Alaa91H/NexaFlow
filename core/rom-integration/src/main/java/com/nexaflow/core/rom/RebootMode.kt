package com.nexaflow.core.rom

/**
 * Canonical `SYSTEM_REBOOT.mode` contract.
 *
 * The action schema has always offered NORMAL, RECOVERY and BOOTLOADER, but
 * the runtime used to call a plain `reboot` for every value, so a workflow
 * configured for recovery silently rebooted normally. The mapping lives here
 * so the builder chips, the handler and the tests share one closed set:
 * anything outside it is refused before an elevated command is built.
 */
object RebootMode {
    const val NORMAL = "NORMAL"
    const val RECOVERY = "RECOVERY"
    const val BOOTLOADER = "BOOTLOADER"

    /** Values the schema offers, in display order. */
    val selectable: List<String> = listOf(NORMAL, RECOVERY, BOOTLOADER)

    /**
     * Arguments appended to the elevated `reboot` command, or null when [mode]
     * is not a supported target. Blank and missing values mean NORMAL, which
     * matches the schema default and every automation saved before the mode
     * field existed.
     */
    fun argumentsFor(mode: String?): List<String>? = when (mode?.trim()?.uppercase()) {
        null, "", NORMAL -> emptyList()
        RECOVERY -> listOf("recovery")
        BOOTLOADER -> listOf("bootloader")
        else -> null
    }

    /** Result text describing the target that was actually requested. */
    fun successMessage(mode: String?): String = when (mode?.trim()?.uppercase()) {
        RECOVERY -> "Recovery reboot requested"
        BOOTLOADER -> "Bootloader reboot requested"
        else -> "Reboot requested"
    }
}
