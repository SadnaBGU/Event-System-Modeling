package com.eventsystem.infrastructure.init;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateFileParserTest {

    private final StateFileParser parser = new StateFileParser();

    @Test
    void parsesSimpleCommandWithArgs() {
        List<InitCommand> commands = parser.parse("login(rina, secret)");

        assertThat(commands).hasSize(1);
        InitCommand cmd = commands.get(0);
        assertThat(cmd.name()).isEqualTo("login");
        assertThat(cmd.args()).containsExactly("rina", "secret");
        assertThat(cmd.lineNumber()).isEqualTo(1);
    }

    @Test
    void ignoresBlankLinesAndComments() {
        String content = """
                # a comment
                // another comment

                login(a, b)
                """;

        List<InitCommand> commands = parser.parse(content);

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).lineNumber()).isEqualTo(4);
    }

    @Test
    void stripsOptionalTrailingSemicolon() {
        List<InitCommand> commands = parser.parse("login(a, b);");

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).args()).containsExactly("a", "b");
    }

    @Test
    void preservesQuotedWhitespaceAndCommas() {
        List<InitCommand> commands =
                parser.parse("open-production-company(rina, acme, \"Acme, Live\", \"top  shows\", 4.5)");

        InitCommand cmd = commands.get(0);
        assertThat(cmd.args()).containsExactly("rina", "acme", "Acme, Live", "top  shows", "4.5");
    }

    @Test
    void parsesEmptyArgumentList() {
        List<InitCommand> commands = parser.parse("ping()");

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).arity()).isZero();
    }

    @Test
    void normalisesCommandNameToLowerCase() {
        List<InitCommand> commands = parser.parse("LOGIN(a, b)");

        assertThat(commands.get(0).name()).isEqualTo("login");
    }

    @Test
    void rejectsLineWithoutParentheses() {
        assertThatThrownBy(() -> parser.parse("login a b"))
                .isInstanceOf(InitFileException.class)
                .satisfies(e -> assertThat(((InitFileException) e).lineNumber()).isEqualTo(1));
    }

    @Test
    void rejectsUnterminatedQuote() {
        assertThatThrownBy(() -> parser.parse("login(\"unterminated)"))
                .isInstanceOf(InitFileException.class);
    }

    @Test
    void reportsCorrectLineNumberOnError() {
        String content = """
                login(a, b)
                login(c, d)
                broken line
                """;

        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(InitFileException.class)
                .satisfies(e -> assertThat(((InitFileException) e).lineNumber()).isEqualTo(3));
    }
}
