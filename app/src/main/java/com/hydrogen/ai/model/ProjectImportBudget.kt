package com.hydrogen.ai.model

/**
 * A failed read must not use a project file slot. A separate read-attempt limit
 * prevents a huge picker selection from keeping the editor busy indefinitely.
 */
internal class ProjectImportBudget(existingCount: Int, maximumFiles: Int, private val maxAttempts: Int = 100) {
    val capacity = (maximumFiles - existingCount.coerceAtLeast(0)).coerceAtLeast(0)
    var added: Int = 0
        private set
    var attempted: Int = 0
        private set
    val full: Boolean get() = added >= capacity
    val attemptLimitReached: Boolean get() = attempted >= maxAttempts
    val canRead: Boolean get() = !full && !attemptLimitReached

    fun beginRead(): Boolean {
        if (!canRead) return false
        attempted++
        return true
    }

    fun recordAdded() {
        check(added < attempted && added < capacity)
        added++
    }
}
