package org.esrs.pipeline.config;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PipelineConfigTest {
    @Test
    void shouldLoadDefaultsFromProperties() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        PipelineConfig config = PipelineConfig.load(root, Map.of());

        assertEquals("arelleCmdLine", config.arelleCommand());
        assertTrue(config.skipArelle());
        assertTrue(config.failOnValidationIssues());
        assertFalse(config.requireViewerPlugin());
        assertEquals("iXBRLViewerPlugin", config.ixbrlViewerPlugin());

        assertEquals(root.resolve("mapping/map-esrs-2023-12-22.json"), config.mappingFile());
        assertEquals(root.resolve("templates/report-base.xhtml"), config.templateFile());
    }

    @Test
    void shouldApplyEnvironmentOverrides() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        PipelineConfig config = PipelineConfig.load(root, Map.of(
            "ARELLE_CMD", "myArelle",
            "SKIP_ARELLE", "false",
            "FAIL_ON_VALIDATION_ISSUES", "false",
            "REQUIRE_VIEWER_PLUGIN", "true",
            "IXBRL_VIEWER_PLUGIN", "CustomViewerPlugin"
        ));

        assertEquals("myArelle", config.arelleCommand());
        assertFalse(config.skipArelle());
        assertFalse(config.failOnValidationIssues());
        assertTrue(config.requireViewerPlugin());
        assertEquals("CustomViewerPlugin", config.ixbrlViewerPlugin());
    }
}
