package org.esrs.pipeline;

import java.nio.file.Path;

import org.esrs.pipeline.config.PipelineConfig;
import org.esrs.pipeline.orchestration.ReportingPipelineOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EsrsPipelineApplication {
    private static final Logger LOG = LoggerFactory.getLogger(EsrsPipelineApplication.class);

    private EsrsPipelineApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        PipelineConfig config = PipelineConfig.load(root);

        LOG.info("Starting ESRS pipeline with input={}, mapping={}, output={}",
            config.inputJson(),
            config.mappingFile(),
            config.outputDir());

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

        LOG.info("XBRL: {}", result.xbrlPath());
        LOG.info("iXBRL: {}", result.ixbrlPath());
        LOG.info("Viewer: {}", result.interactiveHtmlPath());
        LOG.info("Viewer fallback used: {}", result.viewerFallbackUsed());
        LOG.info("Taxonomy visualization: {}", config.outputDir().resolve("taxonomy-visualization.html"));
        result.validationIssues().forEach(issue ->
            LOG.info("{} {} - {}", issue.severity(), issue.code(), issue.message())
        );
    }
}
