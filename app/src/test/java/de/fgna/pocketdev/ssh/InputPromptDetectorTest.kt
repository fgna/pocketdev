package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputPromptDetectorTest {
    @Test fun recognizesShortUnterminatedPrompts() {
        assertEquals("Name:", InputPromptDetector.detect("Previous output\nName: "))
        assertEquals("Reply 2:", InputPromptDetector.detect("Reply 2: "))
        assertEquals("Enter passphrase:", InputPromptDetector.detect("Enter passphrase: "))
        assertEquals("Password:", InputPromptDetector.detect("Password: "))
    }

    @Test fun avoidsCompletedLinesAndUnrelatedOutput() {
        assertNull(InputPromptDetector.detect("Name: \n"))
        assertNull(InputPromptDetector.detect("build successful"))
        assertNull(InputPromptDetector.detect("error: "))
        assertNull(InputPromptDetector.detect("x".repeat(121) + ": "))
    }

    @Test fun splitPromptsOnlyTriggerWhenComplete() {
        var state = CommandUiState(running = true)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "Nam"))
        assertEquals(0L, state.inputPromptGeneration)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "e: "))
        assertEquals("Name:", state.inputPrompt)
        assertEquals(1L, state.inputPromptGeneration)
        repeat(5) {
            state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDOUT, "progress\n"))
        }
        assertEquals(1L, state.inputPromptGeneration)
        assertTrue(state.running)
        state = CommandStateReducer.reduce(state, CommandEvent.Completed(0))
        assertNull(state.inputPrompt)
    }

    @Test fun repeatedIdenticalPromptCreatesNewGeneration() {
        var state = CommandUiState(running = true)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "Reply: "))
        assertEquals(1L, state.inputPromptGeneration)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "\n"))
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "Reply: "))
        assertEquals(2L, state.inputPromptGeneration)
    }

    @Test fun startingNextCommandResetsPromptGeneration() {
        var state = CommandUiState(running = true)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "Password: "))
        assertEquals(1L, state.inputPromptGeneration)
        state = CommandStateReducer.reduce(state, CommandEvent.Connecting)
        assertEquals(0L, state.inputPromptGeneration)
        assertNull(state.inputPrompt)
    }
}
