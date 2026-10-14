package de.fgna.pocketdev.ssh

import org.junit.Assert.*
import org.junit.Test

class SudoPromptParserTest {
    @Test fun shortNormalPromptAppearsWithoutNewlineOrEof() {
        val events = mutableListOf<CommandEvent>()
        val parser = SudoPromptParser(events::add)
        parser.accept("Owner password: ")
        assertEquals(listOf(CommandEvent.Output(OutputStreamKind.STDERR, "Owner password: ")), events)
    }

    @Test fun sudoMarkerWorksAtEverySplitAndDoesNotLeak() {
        val marker = SudoPromptParser.MARKER
        for (split in 0..marker.length) {
            val events = mutableListOf<CommandEvent>()
            val parser = SudoPromptParser(events::add)
            parser.accept("before " + marker.take(split))
            parser.accept(marker.drop(split) + "after ")
            parser.finish()
            assertEquals(1, events.count { it == CommandEvent.SudoPasswordRequired })
            assertEquals("before after ", events.filterIsInstance<CommandEvent.Output>().joinToString("") { it.text })
        }
    }

    @Test fun incompleteMarkerAndOrdinaryPrefixArePreserved() {
        val events = mutableListOf<CommandEvent>()
        val parser = SudoPromptParser(events::add)
        parser.accept("P")
        parser.accept("lease enter a value: ")
        parser.accept("POCKETDEV_SU")
        parser.finish()
        assertEquals("Please enter a value: POCKETDEV_SU", events.filterIsInstance<CommandEvent.Output>().joinToString("") { it.text })
        assertFalse(events.contains(CommandEvent.SudoPasswordRequired))
    }

    @Test fun repeatedPromptsAreRecognizedInOneChunk() {
        val events = mutableListOf<CommandEvent>()
        val parser = SudoPromptParser(events::add)
        parser.accept(SudoPromptParser.MARKER + "try again\n" + SudoPromptParser.MARKER)
        assertEquals(2, events.count { it == CommandEvent.SudoPasswordRequired })
        assertEquals("try again\n", events.filterIsInstance<CommandEvent.Output>().joinToString("") { it.text })
    }
}
