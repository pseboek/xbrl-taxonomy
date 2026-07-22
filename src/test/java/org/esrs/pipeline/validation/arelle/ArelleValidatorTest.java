package org.esrs.pipeline.validation.arelle;

import org.esrs.pipeline.model.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArelleValidatorTest {
    @Test
    void shouldReturnExecutionErrorWhenCommandIsMissing() throws Exception {
        ArelleValidator validator = new ArelleValidator("missing-arelle-command-xyz");
        Path reportFile = Files.createTempFile("dummy-report", ".xml");
        Path logFile = Files.createTempDirectory("arelle-log").resolve("arelle.log");

        List<ValidationIssue> issues = validator.validate(reportFile, Path.of("."), logFile);
        assertTrue(issues.stream().anyMatch(i -> "ARELLE_EXEC".equals(i.code())));
    }
}
