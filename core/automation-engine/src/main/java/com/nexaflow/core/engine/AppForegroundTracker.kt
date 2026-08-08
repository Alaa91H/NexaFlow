package com.nexaflow.core.engine

/**
 * Pure state machine for the "active while the app stays in the foreground"
 * app trigger: a task fires (RUN) when one of its apps opens and stays held
 * until that app leaves the foreground, at which point the task's end options
 * apply (EXIT).
 *
 * The accessibility service feeds every *real* foreground change (after
 * filtering system chrome via [AppForegroundRules]) into [onForegroundChange],
 * which decides which tasks to start or end based on their configured packages
 * and cooldowns. The class has no Android dependencies, so the whole lifecycle
 * is unit-tested deterministically.
 */
class AppForegroundTracker(
    private val now: () -> Long = System::currentTimeMillis
) {

    /** A task that can be activated by the foreground-app trigger. */
    class Task(val id: String, val cooldownMillis: Long)

    sealed interface Command {
        /** Run the task's actions: one of its apps just came to the foreground. */
        data class Run(val taskId: String) : Command

        /** End the task: its app left the foreground while it was active. */
        data class Exit(val taskId: String) : Command
    }

    private val lastRunAt = mutableMapOf<String, Long>()

    /** Task ids currently held active by a foreground app. */
    private val active = mutableSetOf<String>()

    /** True when [taskId] is currently held active by a foreground app. */
    fun isActive(taskId: String): Boolean = taskId in active

    /**
     * Feeds one real foreground-package change. [matches] tells whether the new
     * package activates the task (per its trigger config). Returns the commands
     * to execute, in task order.
     *
     * A task already active (for *any* of its apps — e.g. it triggers on two
     * packages and the user switches between them) is not re-run; a task that
     * stops matching while active emits [Command.Exit] — including one that was
     * disabled mid-session, which lets its end options restore the device.
     * Cooldowns gate re-entry so a close→reopen cycle cannot hammer the engine.
     */
    fun onForegroundChange(
        packageName: String,
        tasks: List<Task>,
        matches: (taskId: String, packageName: String) -> Boolean
    ): List<Command> {
        val commands = mutableListOf<Command>()
        val timestamp = now()
        tasks.forEach { task ->
            if (matches(task.id, packageName)) {
                val alreadyActive = task.id in active
                val last = lastRunAt[task.id] ?: 0L
                if (!alreadyActive && timestamp - last > task.cooldownMillis) {
                    lastRunAt[task.id] = timestamp
                    active += task.id
                    commands += Command.Run(task.id)
                }
            } else if (active.remove(task.id)) {
                commands += Command.Exit(task.id)
            }
        }
        return commands
    }
}
