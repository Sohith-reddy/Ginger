package dev.ginger.commands;

import dev.ginger.cli.UsageException;

import java.util.List;

public interface Command {

    String name();

    String description();

    String usage();

    default void validate(List<String> args) throws UsageException {
    }

    int execute(List<String> args) throws Exception;
}
