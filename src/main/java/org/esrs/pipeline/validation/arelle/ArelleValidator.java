package org.esrs.pipeline.validation.arelle;

import java.io.IOException;
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
        cmd.add("--disclosureSystem");
        cmd.add("esef");
        cmd.add("--packages");
        cmd.add(taxonomyPackageRoot.toString());
        cmd.add("--logFile");
        cmd.add(logFile.toString());
        cmd.add("--logFormat");
        cmd.add("text");

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int code = process.waitFor();
        ValidationReportParser parser = new ValidationReportParser();
        List<ValidationIssue> parsed = parser.parse(logFile);
        if (code != 0 && parsed.isEmpty()) {
            parsed = List.of(new ValidationIssue("ERROR", "ARELLE_EXEC", "Arelle exited with code " + code));
        }
        return parsed;
    }
}
