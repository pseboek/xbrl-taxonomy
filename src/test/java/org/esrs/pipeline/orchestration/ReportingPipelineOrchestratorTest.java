package org.esrs.pipeline.orchestration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingPipelineOrchestratorTest {
    @Test
    void shouldRunEndToEndWithArelleSkipped() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path outputDir = Files.createTempDirectory("esrs-output");

        ReportingPipelineOrchestrator orchestrator = new ReportingPipelineOrchestrator("arelleCmdLine");
        orchestrator.run(
            root.resolve("src/main/resources/testdata/fictive-esrs-input.json"),
            root.resolve("mapping/map-esrs-2023-12-22.json"),
            root.resolve("templates/report-base.xhtml"),
            root.resolve("mapping/report-layout-map.json"),
            outputDir,
            root,
            true
        );

        assertTrue(Files.exists(outputDir.resolve("report-instance.xml")));
        assertTrue(Files.exists(outputDir.resolve("report-ixbrl.xhtml")));
        assertTrue(Files.exists(outputDir.resolve("report-interaktiv.html")));
    }
}
