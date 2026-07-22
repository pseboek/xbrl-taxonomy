package org.esrs.pipeline.validation.arelle;

import java.io.IOException;
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

        List<ValidationIssue> issues = new ArrayList<>();
        for (String line : Files.readAllLines(logFile)) {
            String normalized = line.toLowerCase();
            if (normalized.contains("error")) {
                issues.add(new ValidationIssue("ERROR", "ARELLE", line));
            } else if (normalized.contains("warning")) {
                issues.add(new ValidationIssue("WARN", "ARELLE", line));
            }
        }
        if (issues.isEmpty()) {
            issues.add(new ValidationIssue("INFO", "ARELLE_OK", "No errors or warnings parsed from Arelle log."));
        }
        return issues;
    }
}
