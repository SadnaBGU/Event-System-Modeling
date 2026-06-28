package com.eventsystem.infrastructure.config.startup;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

public class YamlConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<YAMLException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, YAMLException cause) {
        return new FailureAnalysis(
                "STARTUP_CONFIG_ERROR: configuration YAML is malformed. " +
                location(cause) +
                "Parser message: " + safeMessage(cause),
                "Fix the YAML syntax in the configuration file. Check missing ':' after keys, " +
                "mis-indented blocks, and unbalanced inline map/list delimiters such as {}, [], or quotes.",
                cause
        );
    }

    private String location(YAMLException cause) {
        if (cause instanceof MarkedYAMLException marked && marked.getProblemMark() != null) {
            Mark mark = marked.getProblemMark();
            return "Problem near " + mark.getName() +
                    " line " + (mark.getLine() + 1) +
                    ", column " + (mark.getColumn() + 1) + ". ";
        }

        return "";
    }

    private String safeMessage(YAMLException cause) {
        return cause.getMessage() == null ? "<no parser details available>" : cause.getMessage();
    }
}
