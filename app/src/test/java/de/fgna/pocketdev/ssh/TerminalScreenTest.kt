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
        val screen = TerminalScreen(20, 5)
        screen.accept("one\ntwo\nthree\nfour\nfive\nsix")
        assertEquals("two\nthree\nfour\nfive\nsix", screen.render())
        screen.accept("\u001b[999;999H!")
        assertTrue(screen.render().contains("!"))
    }

    @Test fun resizeUpdatesWrappingAndBoundsWithoutLosingVisibleContent() {
        val screen = TerminalScreen(20, 5)
        screen.accept("kept")
        screen.resize(30, 6)
        screen.accept("\u001b[5;30H!")
        assertTrue(screen.render().lines().last().endsWith("!"))
        assertTrue(screen.render().startsWith("kept"))

        screen.resize(20, 5)
        assertTrue(screen.render().lines().size <= 5)
        assertTrue(screen.render().lines().all { it.length <= 20 })
        screen.accept("\u001b[5;20H?")
        assertTrue(screen.render().lines().last().endsWith("?"))
    }

    @Test fun titleEscapeIsNotDisplayed() {
        val screen = TerminalScreen(20, 5)
        screen.accept("\u001b]0;secret title\u0007hello")
        assertEquals("hello", screen.render())
    }
}
