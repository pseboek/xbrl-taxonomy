package org.esrs.pipeline;

import java.nio.file.Path;
import org.esrs.pipeline.orchestration.ReportingPipelineOrchestrator;

public final class EsrsPipelineApplication {
    private EsrsPipelineApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path inputJson = root.resolve("src/main/resources/testdata/fictive-esrs-input.json");
        Path mappingFile = root.resolve("mapping/map-esrs-2023-12-22.json");
        Path templateFile = root.resolve("templates/report-base.xhtml");
        Path layoutMap = root.resolve("mapping/report-layout-map.json");
        Path outputDir = root.resolve("output");

        String arelleCommand = System.getenv().getOrDefault("ARELLE_CMD", "arelleCmdLine");
        boolean skipArelle = Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_ARELLE", "true"));
        boolean failOnValidationIssues = Boolean.parseBoolean(System.getenv().getOrDefault("FAIL_ON_VALIDATION_ISSUES", "true"));
        boolean requireViewerPlugin = Boolean.parseBoolean(System.getenv().getOrDefault("REQUIRE_VIEWER_PLUGIN", "false"));

        ReportingPipelineOrchestrator orchestrator = new ReportingPipelineOrchestrator(arelleCommand);
        ReportingPipelineOrchestrator.PipelineResult result = orchestrator.run(
            inputJson,
            mappingFile,
            templateFile,
            layoutMap,
            outputDir,
            root,
            skipArelle,
            failOnValidationIssues,
            requireViewerPlugin
        );

        System.out.println("XBRL: " + result.xbrlPath());
        System.out.println("iXBRL: " + result.ixbrlPath());
        System.out.println("Viewer: " + result.interactiveHtmlPath());
        System.out.println("Viewer fallback used: " + result.viewerFallbackUsed());
        result.validationIssues().forEach(issue ->
            System.out.println(issue.severity() + " " + issue.code() + " - " + issue.message())
        );
    }
}
