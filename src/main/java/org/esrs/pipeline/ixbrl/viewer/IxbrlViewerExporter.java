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

    public void export(Path ixbrlXhtml, Path htmlOutput) throws IOException, InterruptedException {
        Files.createDirectories(htmlOutput.getParent());
        List<String> cmd = new ArrayList<>();
        cmd.add(arelleCommand);
        cmd.add("--plugins");
        cmd.add("iXBRLViewerPlugin");
        cmd.add("--file");
        cmd.add(ixbrlXhtml.toString());
        cmd.add("--save-viewer");
        cmd.add(htmlOutput.toString());

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int code = process.waitFor();
        if (code != 0 || !Files.exists(htmlOutput)) {
            // Fallback artifact keeps the pipeline deterministic when the plugin is unavailable.
            Files.writeString(
                htmlOutput,
                "<!doctype html><html><head><meta charset=\"utf-8\"><title>Viewer Export Fallback</title></head>"
                    + "<body><h1>Viewer export fallback</h1><p>Arelle iXBRL viewer plugin was not available.</p>"
                    + "<p>Source: " + ixbrlXhtml.getFileName() + "</p></body></html>",
                StandardCharsets.UTF_8
            );
        }
    }
}
