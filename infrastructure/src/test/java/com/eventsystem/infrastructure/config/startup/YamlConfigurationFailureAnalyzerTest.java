package com.eventsystem.infrastructure.config.startup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.parser.ParserException;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigurationFailureAnalyzerTest {

    private final YamlConfigurationFailureAnalyzer analyzer = new YamlConfigurationFailureAnalyzer();

    @Test
    void malformedYamlFailureIncludesStartupConfigErrorAndSyntaxGuidance() {
        YAMLException yamlException = new YAMLException("mapping values are not allowed here");

        FailureAnalysis analysis = analyzer.analyze(new IllegalStateException("Failed to load property source", yamlException));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
                .contains("STARTUP_CONFIG_ERROR")
                .contains("configuration YAML is malformed")
                .contains("mapping values are not allowed here");
        assertThat(analysis.getAction())
                .contains("missing ':'")
                .contains("{}")
                .contains("[]");
    }

    @Test
    void markedYamlFailureIncludesLineAndColumn() {
        Mark problemMark = new Mark("application-main.yml", 0, 4, 7, new int[0], 0);
        ParserException parserException = new ParserException(
                "while parsing a block mapping",
                null,
                "expected <block end>, but found '}'",
                problemMark
        );

        FailureAnalysis analysis = analyzer.analyze(parserException);

        assertThat(analysis.getDescription())
                .contains("application-main.yml")
                .contains("line 5")
                .contains("column 8")
                .contains("expected <block end>, but found '}'");
    }
}
