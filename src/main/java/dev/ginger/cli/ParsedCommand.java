package dev.ginger.cli;

import java.util.List;

public record ParsedCommand(Action action, String name, List<String> arguments, boolean debug) {

    public enum Action { HELP, VERSION, RUN }
}
