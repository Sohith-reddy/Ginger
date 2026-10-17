package dev.ginger.command;

/**
 * A single ginger subcommand, e.g. {@code ginger init}.
 */
public interface Command {

    String name();

    String description();

    /**
     * @param args arguments following the command name
     * @return process exit code
     */
    int execute(String[] args) throws Exception;
}
