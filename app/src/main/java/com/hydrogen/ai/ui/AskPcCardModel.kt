package com.hydrogen.ai.ui

import com.hydrogen.ai.companion.CompanionPathHelper

/** The one button an "Ask your PC" card can show. None of them approves anything: that only happens on the PC. */
enum class AskPcCardAction { OPEN_COMPANION, SEND, SEND_AGAIN, CHECK_APPROVAL, RUN, CHECK_TASK, ADD_RESULT }

/** What the "Ask your PC" card under one assistant reply says and offers. */
data class AskPcCardModel(
    val status: String,
    val isError: Boolean = false,
    val showProgress: Boolean = false,
    val action: AskPcCardAction? = null,
    val actionLabel: String? = null,
    val actionEnabled: Boolean = true
)

/** Pure state for the card, derived from the PC Companion state so it can be unit-tested. */
object AskPcCard {

    const val TITLE = "Orbit suggests asking your PC"

    fun model(companion: CompanionUiState, messageId: String): AskPcCardModel {
        val send = companion.askPcSends[messageId]
        val sentId = send?.approvalId
        if (!companion.isPaired) {
            return AskPcCardModel(
                status = if (sentId != null) {
                    "This phone is no longer paired with your PC."
                } else {
                    "Pair this phone with your PC to send this."
                },
                action = AskPcCardAction.OPEN_COMPANION,
                actionLabel = "Set up PC Companion"
            )
        }
        if (send?.sending == true) return AskPcCardModel("Sending to your PC…", showProgress = true)
        val busy = companion.isWorkBusy || companion.isRevoking
        if (sentId != null) return sentModel(companion, sentId, busy)
        send?.errorMessage?.let { error ->
            return AskPcCardModel(
                status = error,
                isError = true,
                action = AskPcCardAction.SEND,
                actionLabel = "Try again",
                actionEnabled = !busy
            )
        }
        if (companion.health?.workEnabled == false) {
            return AskPcCardModel(
                status = "No folders are shared yet. Share one in Orbit PC Companion on your PC.",
                action = AskPcCardAction.OPEN_COMPANION,
                actionLabel = "Open PC Companion"
            )
        }
        return AskPcCardModel(
            status = if (busy) {
                "Your PC is busy with another request. Try again in a moment."
            } else {
                "Nothing is sent until you tap Send. Then you approve it on your PC."
            },
            action = AskPcCardAction.SEND,
            actionLabel = "Send to PC",
            actionEnabled = !busy
        )
    }

    private fun sentModel(companion: CompanionUiState, approvalId: String, busy: Boolean): AskPcCardModel {
        // Another request (from the PC Companion sheet or another card) replaced this one as the active request.
        val approval = companion.activeApproval?.takeIf { it.approvalId == approvalId }
            ?: return AskPcCardModel(
                status = "Sent to your PC. You can see its status in PC Companion.",
                action = AskPcCardAction.OPEN_COMPANION,
                actionLabel = "Open PC Companion"
            )
        return when (approval.status) {
            "PENDING" -> if (companion.isPollingApproval) {
                AskPcCardModel("Waiting for you to approve it on your PC…", showProgress = true)
            } else {
                AskPcCardModel(
                    status = "Still waiting for approval on your PC.",
                    action = AskPcCardAction.CHECK_APPROVAL,
                    actionLabel = "Check again",
                    actionEnabled = !busy
                )
            }
            "APPROVED" -> if (companion.isRunningWorkTask) {
                AskPcCardModel("Starting on your PC…", showProgress = true)
            } else {
                AskPcCardModel(
                    status = "Approved on your PC.",
                    action = AskPcCardAction.RUN,
                    actionLabel = "Run on PC",
                    actionEnabled = !busy
                )
            }
            // An approval is used up once its task starts; the task carries the rest of the story.
            "CONSUMED" -> taskModel(companion, approvalId, busy)
            "DENIED" -> AskPcCardModel("Denied on your PC.")
            "EXPIRED" -> AskPcCardModel(
                status = "It wasn't approved in time.",
                action = AskPcCardAction.SEND_AGAIN,
                actionLabel = "Send again",
                actionEnabled = !busy
            )
            "REVOKED" -> AskPcCardModel("This request was withdrawn on your PC.")
            else -> AskPcCardModel("Status on your PC: ${approval.status.lowercase()}.")
        }
    }

    private fun taskModel(companion: CompanionUiState, approvalId: String, busy: Boolean): AskPcCardModel {
        val task = companion.activeTask?.takeIf { it.approvalId == approvalId }
            ?: return if (companion.isRunningWorkTask) {
                AskPcCardModel("Starting on your PC…", showProgress = true)
            } else {
                AskPcCardModel(
                    status = "Approved and used. You can see the result in PC Companion.",
                    action = AskPcCardAction.OPEN_COMPANION,
                    actionLabel = "Open PC Companion"
                )
            }
        return when (task.status) {
            "QUEUED", "RUNNING" -> if (companion.isRunningWorkTask) {
                AskPcCardModel("Running on your PC…", showProgress = true)
            } else {
                AskPcCardModel(
                    status = "Still running on your PC.",
                    action = AskPcCardAction.CHECK_TASK,
                    actionLabel = "Check again",
                    actionEnabled = !busy
                )
            }
            "COMPLETED" -> if (CompanionPathHelper.formatWorkResultForDraft(task) != null) {
                AskPcCardModel(
                    status = "Done. Add the result to your message to talk about it.",
                    action = AskPcCardAction.ADD_RESULT,
                    actionLabel = "Add to message"
                )
            } else {
                AskPcCardModel("Done, but there's no plain text to add to the chat.")
            }
            "FAILED" -> AskPcCardModel(
                status = task.error?.trim()?.takeIf { it.isNotEmpty() }?.let { "It didn't finish on your PC: $it" }
                    ?: "It didn't finish on your PC.",
                isError = true
            )
            "CANCELLED" -> AskPcCardModel("Cancelled.")
            else -> AskPcCardModel("Status on your PC: ${task.status.lowercase()}.")
        }
    }
}
