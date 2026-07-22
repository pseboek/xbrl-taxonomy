package org.esrs.pipeline.ixbrl.viewer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IxbrlViewerExporter {
    private final String arelleCommand;

    public IxbrlViewerExporter(String arelleCommand) {
        this.arelleCommand = arelleCommand;
    }

    public ViewerExportResult export(Path ixbrlXhtml, Path htmlOutput) throws IOException, InterruptedException {
        Files.createDirectories(htmlOutput.getParent());
        String viewerPlugin = System.getenv().getOrDefault("IXBRL_VIEWER_PLUGIN", "iXBRLViewerPlugin");

        List<String> cmd = new ArrayList<>();
        cmd.add(arelleCommand);
        cmd.add("--plugins");
        cmd.add(viewerPlugin);
        cmd.add("--file");
        cmd.add(ixbrlXhtml.toString());
        cmd.add("--save-viewer");
        cmd.add(htmlOutput.toString());

        int code = -1;
        boolean fallback = false;
        String reason = "";

        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            code = process.waitFor();
        } catch (IOException e) {
            fallback = true;
            reason = "Failed to start viewer export command: " + e.getMessage();
        }

        if (code != 0 || !Files.exists(htmlOutput)) {
            fallback = true;
            if (reason.isBlank()) {
                reason = "Viewer plugin not available or export command returned code " + code;
            }
            // Fallback artifact keeps the pipeline deterministic when the plugin is unavailable.
            Files.writeString(
                htmlOutput,
                "<!doctype html><html><head><meta charset=\"utf-8\"><title>Viewer Export Fallback</title></head>"
                    + "<body><h1>Viewer export fallback</h1><p>Arelle iXBRL viewer plugin was not available.</p>"
                    + "<p>Source: " + ixbrlXhtml.getFileName() + "</p></body></html>",
                StandardCharsets.UTF_8
            );
        }

        return new ViewerExportResult(fallback, code, reason);
    }

    public record ViewerExportResult(boolean fallbackUsed, int processExitCode, String reason) {
    }
}
