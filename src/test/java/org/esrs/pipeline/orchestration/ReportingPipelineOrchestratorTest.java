package org.esrs.pipeline.orchestration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

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
        assertTrue(Files.exists(outputDir.resolve("taxonomy-visualization.html")));

        String ixbrl = Files.readString(outputDir.resolve("report-ixbrl.xhtml"), StandardCharsets.UTF_8);
        assertTrue(ixbrl.contains("<div style=\"display:none\">"));
        assertTrue(ixbrl.contains("<ix:header>"));
        assertTrue(ixbrl.contains("<ix:resources>"));
        assertTrue(ixbrl.contains("<link:schemaRef"));
        assertTrue(ixbrl.contains("<ix:nonFraction"));
        assertTrue(ixbrl.contains("<ix:nonNumeric"));
        assertTrue(ixbrl.contains("Vollstaendige Faktentabelle"));
        assertTrue(ixbrl.contains("facts-table"));
        assertTrue(ixbrl.contains("Absolute Scope 1 Reduktion"));
        assertTrue(ixbrl.contains("<ix:nonFraction name=\"esrs:AbsoluteValueOfScope1GreenhouseGasEmissionsReduction\""));
        assertTrue(ixbrl.contains("Adressierung prekaerer Arbeit"));
        assertTrue(!ixbrl.contains("{{fact:"), "All fact placeholders should be embedded in iXBRL output.");
        assertTrue(!ixbrl.contains("{{facts:all}}"), "Dynamic fact table marker should be replaced in iXBRL output.");

        String visualization = Files.readString(outputDir.resolve("taxonomy-visualization.html"), StandardCharsets.UTF_8);
        assertTrue(visualization.contains("ESRS Taxonomie-Explorer"));
        assertTrue(visualization.contains("Präsentationshierarchie"));
        assertTrue(visualization.contains("taxonomySearch"));
        assertTrue(visualization.contains("Dimensionsfilter ist ausgeblendet"));
    }

    @Test
    void shouldFailWhenValidationGateIsEnabledAndArelleExecutionFails() {
        Path root = Path.of(".").toAbsolutePath().normalize();

        ReportingPipelineOrchestrator orchestrator = new ReportingPipelineOrchestrator("missing-arelle-command-xyz");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            orchestrator.run(
                root.resolve("src/main/resources/testdata/fictive-esrs-input.json"),
                root.resolve("mapping/map-esrs-2023-12-22.json"),
                root.resolve("templates/report-base.xhtml"),
                root.resolve("mapping/report-layout-map.json"),
                Files.createTempDirectory("esrs-output-strict"),
                root,
                false,
                true,
                false
            )
        );
        assertTrue(thrown.getMessage().contains("Validation gate failed"));
    }
}
