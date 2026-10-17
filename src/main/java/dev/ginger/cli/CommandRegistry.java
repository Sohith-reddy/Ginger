package dev.ginger.cli;

import dev.ginger.commands.Command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public CommandRegistry register(Command command) {
        if (commands.putIfAbsent(command.name(), command) != null) {
            throw new IllegalArgumentException("command already registered: " + command.name());
        }
        return this;
    }

    public Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public Collection<Command> all() {
        return Collections.unmodifiableCollection(commands.values());
    }
}
