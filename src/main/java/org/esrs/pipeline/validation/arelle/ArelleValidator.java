package org.esrs.pipeline.validation.arelle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.esrs.pipeline.model.ValidationIssue;

public class ArelleValidator {
    private final String command;

    public ArelleValidator(String command) {
        this.command = command;
    }

    public List<ValidationIssue> validate(Path reportFile, Path taxonomyPackageRoot, Path logFile) throws IOException, InterruptedException {
        Files.createDirectories(logFile.getParent());
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("--file");
        cmd.add(reportFile.toString());
        cmd.add("--validate");

        String disclosureSystem = System.getenv("ARELLE_DISCLOSURE_SYSTEM");
        if (disclosureSystem != null && !disclosureSystem.isBlank()) {
            cmd.add("--disclosureSystem");
            cmd.add(disclosureSystem);
        }

        cmd.add("--packages");
        cmd.add(taxonomyPackageRoot.toString());
        cmd.add("--logFile");
        cmd.add(logFile.toString());
        cmd.add("--logFileMode");
        cmd.add("w");

        String logFormat = System.getenv("ARELLE_LOG_FORMAT");
        if (logFormat != null && !logFormat.isBlank()) {
            cmd.add("--logFormat");
            cmd.add(logFormat);
        }

        Process process;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            return List.of(new ValidationIssue("ERROR", "ARELLE_EXEC", "Arelle command failed to start: " + e.getMessage()));
        }

        String processOutput;
        try (InputStream inputStream = process.getInputStream()) {
            processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        int code = process.waitFor();
        ValidationReportParser parser = new ValidationReportParser();

        if ((!Files.exists(logFile) || Files.readString(logFile, StandardCharsets.UTF_8).isBlank()) && !processOutput.isBlank()) {
            Files.writeString(logFile, processOutput, StandardCharsets.UTF_8);
        }

        List<ValidationIssue> parsed = parser.parse(logFile);

        if (code != 0) {
            parsed = new ArrayList<>(parsed);
            parsed.add(0, new ValidationIssue(
                "ERROR",
                "ARELLE_EXIT",
                "Arelle exited with code " + code + ". Output: " + compact(processOutput)
            ));
            return parsed;
        }

        if (hasCode(parsed, "ARELLE_LOG_MISSING") || hasCode(parsed, "ARELLE_LOG_EMPTY")) {
            parsed = new ArrayList<>(parsed);
            parsed.add(0, new ValidationIssue(
                "ERROR",
                "ARELLE_EVIDENCE_MISSING",
                "Arelle returned success but did not provide parseable validation evidence in log output."
            ));
        }

        if (!processOutput.isBlank()) {
            parsed = new ArrayList<>(parsed);
            parsed.add(new ValidationIssue("INFO", "ARELLE_STDOUT", compact(processOutput)));
        }
        return parsed;
    }

    private boolean hasCode(List<ValidationIssue> issues, String code) {
        for (ValidationIssue issue : issues) {
            if (code.equalsIgnoreCase(issue.code())) {
                return true;
            }
        }
        return false;
    }

    private String compact(String input) {
        String normalized = input.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
    }
}
