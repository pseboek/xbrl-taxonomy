package org.esrs.pipeline;

import java.nio.file.Path;

import org.esrs.pipeline.config.PipelineConfig;
import org.esrs.pipeline.orchestration.ReportingPipelineOrchestrator;

public final class EsrsPipelineApplication {
    private EsrsPipelineApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        PipelineConfig config = PipelineConfig.load(root);

        ReportingPipelineOrchestrator orchestrator = new ReportingPipelineOrchestrator(config);
        ReportingPipelineOrchestrator.PipelineResult result = orchestrator.run(
            config.inputJson(),
            config.mappingFile(),
            config.templateFile(),
            config.layoutMap(),
            config.outputDir(),
            config.taxonomyRoot(),
            config.skipArelle(),
            config.failOnValidationIssues(),
            config.requireViewerPlugin()
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
