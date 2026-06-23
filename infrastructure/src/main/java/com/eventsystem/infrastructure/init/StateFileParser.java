package com.eventsystem.infrastructure.init;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the textual initial-state file into a list of {@link InitCommand}s.
 *
 * <h2>Grammar (one command per non-blank line)</h2>
 * <pre>
 *   command-name(arg1, arg2, ...)        ; optional trailing semicolon
 * </pre>
 *
 * <ul>
 *   <li>Blank lines are ignored.</li>
 *   <li>Lines whose first non-space characters are {@code #} or {@code //} are comments.</li>
 *   <li>Arguments are comma-separated. An argument may be wrapped in double quotes
 *       to embed commas, spaces or parentheses; inside quotes, {@code \"} is an
 *       escaped quote and {@code \\} an escaped backslash.</li>
 *   <li>An empty argument list {@code cmd()} yields zero arguments.</li>
 * </ul>
 *
 * The parser is intentionally strict: malformed lines raise {@link InitFileException}
 * carrying the offending line number so the whole initialization fails fast.
 */
public final class StateFileParser {

    public List<InitCommand> parse(String content) {
        List<InitCommand> commands = new ArrayList<>();
        if (content == null) {
            return commands;
        }

        String[] lines = content.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String raw = lines[i].trim();

            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) {
                continue;
            }

            // Drop a single optional trailing semicolon.
            if (raw.endsWith(";")) {
                raw = raw.substring(0, raw.length() - 1).trim();
            }
            if (raw.isEmpty()) {
                continue;
            }

            commands.add(parseLine(raw, lineNumber));
        }
        return commands;
    }

    private InitCommand parseLine(String line, int lineNumber) {
        int open = line.indexOf('(');
        if (open < 0 || !line.endsWith(")")) {
            throw new InitFileException(lineNumber,
                    "expected 'command(arg, ...)' but got: " + line);
        }

        String name = line.substring(0, open).trim();
        if (name.isEmpty()) {
            throw new InitFileException(lineNumber, "missing command name");
        }

        String inside = line.substring(open + 1, line.length() - 1);
        List<String> args = splitArgs(inside, lineNumber);
        return new InitCommand(name, args, lineNumber);
    }

    private List<String> splitArgs(String inside, int lineNumber) {
        List<String> args = new ArrayList<>();
        if (inside.trim().isEmpty()) {
            return args;
        }

        int i = 0;
        int n = inside.length();
        while (true) {
            // Skip leading whitespace before an argument.
            while (i < n && Character.isWhitespace(inside.charAt(i))) {
                i++;
            }

            StringBuilder current = new StringBuilder();
            if (i < n && inside.charAt(i) == '"') {
                // Quoted argument: read until the closing quote, honouring escapes.
                i++; // consume opening quote
                boolean closed = false;
                while (i < n) {
                    char c = inside.charAt(i);
                    if (c == '\\' && i + 1 < n) {
                        char next = inside.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i += 2;
                            continue;
                        }
                    }
                    if (c == '"') {
                        closed = true;
                        i++; // consume closing quote
                        break;
                    }
                    current.append(c);
                    i++;
                }
                if (!closed) {
                    throw new InitFileException(lineNumber, "unterminated quoted argument");
                }
                // Skip trailing whitespace until comma or end.
                while (i < n && Character.isWhitespace(inside.charAt(i))) {
                    i++;
                }
                if (i < n && inside.charAt(i) != ',') {
                    throw new InitFileException(lineNumber,
                            "unexpected characters after quoted argument");
                }
                args.add(current.toString());
            } else {
                // Unquoted argument: read until the next comma, then trim.
                while (i < n && inside.charAt(i) != ',') {
                    current.append(inside.charAt(i));
                    i++;
                }
                args.add(current.toString().trim());
            }

            if (i >= n) {
                break;
            }
            // Current char is a comma; consume it and continue with the next arg.
            i++;
        }
        return args;
    }
}
