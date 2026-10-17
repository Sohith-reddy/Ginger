package dev.ginger;

import dev.ginger.cli.CommandLineParser;
import dev.ginger.cli.CommandRegistry;
import dev.ginger.cli.UsageException;
import dev.ginger.commands.Command;
import dev.ginger.commands.InitCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private Main main;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        PrintStream outStream = new PrintStream(out, true);
        CommandRegistry registry = new CommandRegistry()
                .register(new InitCommand(outStream))
                .register(new EchoCommand(outStream))
                .register(new FailingCommand());
        main = new Main(new CommandLineParser(), registry, outStream, new PrintStream(err, true));
    }

    @Test
    void helpListsCommands() {
        assertEquals(0, main.run(new String[]{"--help"}));
        assertTrue(out.toString().contains("init"));
        assertEquals("", err.toString());
    }

    @Test
    void versionPrintsVersion() {
        assertEquals(0, main.run(new String[]{"--version"}));
        assertEquals("ginger version " + Main.VERSION, out.toString().strip());
    }

    @Test
    void unknownCommandFails() {
        assertEquals(1, main.run(new String[]{"not-a-command"}));
        assertTrue(err.toString().contains("'not-a-command' is not a ginger command"));
        assertEquals("", out.toString());
    }

    @Test
    void missingArgumentShowsUsage() {
        assertEquals(2, main.run(new String[]{"echo"}));
        assertTrue(err.toString().contains("usage: ginger echo -m <message>"));
    }

    @Test
    void tooManyArgumentsShowsUsage() {
        assertEquals(2, main.run(new String[]{"init", "a", "b"}));
        assertTrue(err.toString().contains("usage: ginger init"));
    }

    @Test
    void quotedMessageIsPassedThrough() {
        assertEquals(0, main.run(new String[]{"echo", "-m", "Initial commit"}));
        assertEquals("Initial commit", out.toString().strip());
    }

    @Test
    void unexpectedErrorHasNoStackTrace() {
        assertEquals(128, main.run(new String[]{"fail"}));
        assertEquals("fatal: boom", err.toString().strip());
    }

    @Test
    void debugShowsStackTrace() {
        assertEquals(128, main.run(new String[]{"--debug", "fail"}));
        assertTrue(err.toString().contains("IllegalStateException"));
    }

    @Test
    void initCreatesRepository() {
        assertEquals(0, main.run(new String[]{"init", tmp.toString()}));
        assertTrue(Files.isDirectory(tmp.resolve(".ginger")));
        assertFalse(out.toString().isEmpty());
    }

    private record EchoCommand(PrintStream out) implements Command {

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Print a message";
        }

        @Override
        public String usage() {
            return "ginger echo -m <message>";
        }

        @Override
        public void validate(List<String> args) throws UsageException {
            if (args.size() != 2 || !args.get(0).equals("-m")) {
                throw new UsageException("missing message");
            }
        }

        @Override
        public int execute(List<String> args) {
            out.println(args.get(1));
            return 0;
        }
    }

    private static final class FailingCommand implements Command {

        @Override
        public String name() {
            return "fail";
        }

        @Override
        public String description() {
            return "Always fails";
        }

        @Override
        public String usage() {
            return "ginger fail";
        }

        @Override
        public int execute(List<String> args) {
            throw new IllegalStateException("boom");
        }
    }
}
