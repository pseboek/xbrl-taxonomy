package org.esrs.pipeline.validation.arelle;

import org.esrs.pipeline.model.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationReportParserTest {
    @Test
    void shouldReturnWarningForEmptyLog() throws Exception {
        Path log = Files.createTempFile("arelle-empty", ".log");
        Files.writeString(log, "   \n", StandardCharsets.UTF_8);

        ValidationReportParser parser = new ValidationReportParser();
        List<ValidationIssue> issues = parser.parse(log);

        assertTrue(issues.stream().anyMatch(i -> "ARELLE_LOG_EMPTY".equals(i.code())));
    }
}
