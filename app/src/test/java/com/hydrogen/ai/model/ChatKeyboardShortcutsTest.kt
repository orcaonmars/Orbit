package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatKeyboardShortcutsTest {
    private fun command(
        key: Char? = null,
        ctrl: Boolean = false,
        meta: Boolean = false,
        shift: Boolean = false,
        escape: Boolean = false,
        hasMessages: Boolean = true,
        findOpen: Boolean = false,
        canOpenDrawer: Boolean = true
    ) = ChatKeyboardShortcuts.resolve(key, ctrl, meta, shift, escape, hasMessages, findOpen, canOpenDrawer)

    @Test fun ctrlOrCommandFOnlyFindsInsideANonemptyConversation() {
        assertEquals(ChatKeyboardAction.FIND_IN_CHAT, command('f', ctrl = true))
        assertEquals(ChatKeyboardAction.FIND_IN_CHAT, command('F', meta = true))
        assertNull(command('f', ctrl = true, hasMessages = false))
        assertNull(command('f'))
        assertNull(command('f', ctrl = true, shift = true))
    }

    @Test fun newChatUsesControlOrCommandShiftO() {
        assertEquals(ChatKeyboardAction.NEW_CHAT, command('o', ctrl = true, shift = true))
        assertEquals(ChatKeyboardAction.NEW_CHAT, command('O', meta = true, shift = true))
        assertNull(command('o', ctrl = true))
    }

    @Test fun commandKOnlyOpensAnAvailableSidebar() {
        assertEquals(ChatKeyboardAction.OPEN_CONVERSATIONS, command('k', ctrl = true))
        assertNull(command('k', ctrl = true, canOpenDrawer = false))
        assertNull(command('k', ctrl = true, shift = true))
    }

    @Test fun escapeClosesOnlyWhenFindIsOpenWithoutModifiers() {
        assertEquals(ChatKeyboardAction.CLOSE_FIND, command(escape = true, findOpen = true))
        assertNull(command(escape = true))
        assertNull(command(escape = true, ctrl = true, findOpen = true))
    }

    @Test fun ordinaryKeyPressesAndOtherShortcutsAreLeftAlone() {
        assertNull(command('a', ctrl = true))
        assertNull(command('k'))
        assertNull(command('n', ctrl = true))
    }
}
