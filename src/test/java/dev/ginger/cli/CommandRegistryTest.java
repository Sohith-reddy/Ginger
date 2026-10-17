package dev.ginger.cli;

import dev.ginger.commands.InitCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryTest {

    @Test
    void findsRegisteredCommand() {
        InitCommand init = new InitCommand(System.out);
        CommandRegistry registry = new CommandRegistry().register(init);

        assertSame(init, registry.find("init").orElseThrow());
    }

    @Test
    void unknownCommandIsEmpty() {
        assertTrue(new CommandRegistry().find("not-a-command").isEmpty());
    }

    @Test
    void duplicateRegistrationFails() {
        CommandRegistry registry = new CommandRegistry().register(new InitCommand(System.out));

        assertThrows(IllegalArgumentException.class, () -> registry.register(new InitCommand(System.out)));
    }
}
