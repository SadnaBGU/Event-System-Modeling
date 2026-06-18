package com.eventsystem.infrastructure.init;

import java.util.List;

/**
 * A single parsed instruction from the initial-state file, e.g.
 * {@code open-production-company(rina, acme, "Acme Live", "desc", 4.5)}.
 *
 * @param name       the (lower-cased) command name, e.g. {@code open-production-company}
 * @param args       the positional arguments, already unquoted and trimmed
 * @param lineNumber the 1-based source line number, used for error reporting
 */
public record InitCommand(String name, List<String> args, int lineNumber) {

    public InitCommand {
        name = name == null ? "" : name.trim().toLowerCase();
        args = args == null ? List.of() : List.copyOf(args);
    }

    /** Number of supplied arguments. */
    public int arity() {
        return args.size();
    }
}
