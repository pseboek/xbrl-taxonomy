package org.esrs.pipeline.validation.arelle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.esrs.pipeline.model.ValidationIssue;

public class ValidationReportParser {
    public List<ValidationIssue> parse(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return List.of(new ValidationIssue("WARN", "ARELLE_LOG_MISSING", "Validation log file was not generated."));
        }

        String content = Files.readString(logFile, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return List.of(new ValidationIssue("WARN", "ARELLE_LOG_EMPTY", "Validation log file is empty."));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String normalized = trimmed.toLowerCase();
            if (normalized.contains("error")) {
                issues.add(new ValidationIssue("ERROR", "ARELLE", trimmed));
            } else if (normalized.contains("warning")) {
                issues.add(new ValidationIssue("WARN", "ARELLE", trimmed));
            } else if (normalized.contains("arelle skipped")) {
                issues.add(new ValidationIssue("INFO", "ARELLE_SKIPPED", trimmed));
            }
        }
        if (issues.isEmpty()) {
            issues.add(new ValidationIssue("INFO", "ARELLE_OK", "No errors or warnings parsed from Arelle log."));
        }
        return issues;
    }
}
