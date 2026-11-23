package com.hydrogen.ai

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hydrogen.ai.model.QuickAction

/**
 * "Ask Orbit" Quick Settings tile: one swipe and tap from anywhere opens a fresh chat with the
 * keyboard up. On a locked phone it asks for unlock first, so chats are never shown over the keyguard.
 */
class QuickChatTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openNewChat() } else openNewChat()
    }

    private fun openNewChat() {
        // SINGLE_TOP + CLEAR_TOP reuse a running Hydrogen (onNewIntent), so a reply that is still
        // streaming in another chat keeps going.
        val intent = Intent(this, MainActivity::class.java)
            .setAction(QuickAction.NEW_CHAT.intentAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
