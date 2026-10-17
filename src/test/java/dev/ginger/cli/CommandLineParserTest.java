package dev.ginger.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineParserTest {

    private final CommandLineParser parser = new CommandLineParser();

    @Test
    void noArgumentsShowsHelp() throws UsageException {
        assertEquals(ParsedCommand.Action.HELP, parser.parse(new String[0]).action());
    }

    @Test
    void helpFlags() throws UsageException {
        assertEquals(ParsedCommand.Action.HELP, parser.parse(new String[]{"--help"}).action());
        assertEquals(ParsedCommand.Action.HELP, parser.parse(new String[]{"-h"}).action());
        assertEquals(ParsedCommand.Action.HELP, parser.parse(new String[]{"help"}).action());
    }

    @Test
    void versionFlags() throws UsageException {
        assertEquals(ParsedCommand.Action.VERSION, parser.parse(new String[]{"--version"}).action());
        assertEquals(ParsedCommand.Action.VERSION, parser.parse(new String[]{"-v"}).action());
    }

    @Test
    void quotedArgumentStaysSingleArgument() throws UsageException {
        ParsedCommand parsed = parser.parse(new String[]{"commit", "-m", "Initial commit"});

        assertEquals(ParsedCommand.Action.RUN, parsed.action());
        assertEquals("commit", parsed.name());
        assertEquals(List.of("-m", "Initial commit"), parsed.arguments());
    }

    @Test
    void debugFlagBeforeCommand() throws UsageException {
        ParsedCommand parsed = parser.parse(new String[]{"--debug", "init", "repo"});

        assertTrue(parsed.debug());
        assertEquals("init", parsed.name());
        assertEquals(List.of("repo"), parsed.arguments());
    }

    @Test
    void unknownGlobalOptionFails() {
        assertThrows(UsageException.class, () -> parser.parse(new String[]{"--nope"}));
    }
}
