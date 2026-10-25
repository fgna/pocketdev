package de.fgna.pocketdev.ssh

import org.junit.Assert.*
import org.junit.Test

class TerminalScreenTest {
    @Test fun cursorAddressingAndErase() {
        val screen = TerminalScreen(20, 5)
        screen.accept("hello\u001b[2;3Hworld")
        assertTrue(screen.render().contains("  world"))
        screen.accept("\u001b[2;3H\u001b[K")
        assertFalse(screen.render().contains("world"))
        assertTrue(screen.render().contains("hello"))
    }

    @Test fun splitControlSequenceAndAlternateScreen() {
        val screen = TerminalScreen(20, 5)
        screen.accept("original")
        screen.accept("\u001b[?10")
        screen.accept("49h")
        assertEquals("", screen.render())
        screen.accept("alternate")
        assertEquals("alternate", screen.render())
        screen.accept("\u001b[?1049l")
        assertEquals("original", screen.render())
    }

    @Test fun scrollAndBoundedCursorMovement() {
        val screen = TerminalScreen(20, 3)
        screen.accept("one\r\ntwo\r\nthree\r\nfour")
        assertEquals("two\nthree\nfour", screen.render())
        screen.accept("\u001b[999;999H!")
        assertTrue(screen.render().contains("!"))
    }

    @Test fun titleEscapeIsNotDisplayed() {
        val screen = TerminalScreen(20, 5)
        screen.accept("\u001b]0;secret title\u0007hello")
        assertEquals("hello", screen.render())
    }
}
