package com.nexaflow.core.security

/**
 * Builds and validates shell commands so user-supplied values can never break
 * out of an argument position or inject arbitrary commands.
 *
 * Rules:
 *  - arguments are wrapped in single quotes with embedded `'` escaped as `'\''`
 *  - control characters and NUL bytes are rejected outright
 *  - a maximum command length prevents pathological payloads
 */
object SafeCommandBuilder {

    const val MAX_COMMAND_LENGTH = 8192

    /** Quotes a single argument for a POSIX shell (`sh -c`). */
    fun quote(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    /** Builds a command string from a program and safely-quoted arguments. */
    fun build(program: String, vararg args: String): String {
        return (listOf(program) + args).joinToString(" ") { quote(it) }
    }

    /** True when the command contains no NUL bytes or control characters. */
    fun isSafeCommand(command: String): Boolean {
        if (command.length > MAX_COMMAND_LENGTH) return false
        return command.none { it.code < 0x20 && it != '\t' && it != '\n' && it != '\r' }
    }

    /**
     * Validates a user-provided command string before handing it to an elevated
     * runtime. Returns the command unchanged when safe, otherwise null.
     */
    fun validateUserCommand(command: String): String? {
        if (!isSafeCommand(command)) return null
        if (command.isBlank()) return null
        return command
    }
}
