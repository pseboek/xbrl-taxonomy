package org.esrs.pipeline.visualization;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.esrs.pipeline.mapping.MappingRegistry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TaxonomyVisualizationExporterTest {
    @Test
    void shouldExportHierarchicalTaxonomyExplorer() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path outputDir = Files.createTempDirectory("taxonomy-visualization");
        Path outputHtml = outputDir.resolve("taxonomy-visualization.html");

        TaxonomyVisualizationExporter exporter = new TaxonomyVisualizationExporter();
        assertDoesNotThrow(() -> exporter.export(
            MappingRegistry.fromPath(root.resolve("mapping/map-esrs-2023-12-22.json")),
            root,
            root.resolve("mapping/report-layout-map.json"),
            outputHtml
        ));

        assertTrue(Files.exists(outputHtml));

        String html = Files.readString(outputHtml, StandardCharsets.UTF_8);
        assertTrue(html.contains("ESRS Taxonomie-Explorer"));
        assertTrue(html.contains("Präsentationshierarchie"));
        assertTrue(html.contains("Layout-Zuordnung"));
        assertTrue(html.contains("taxonomySearch"));
        assertTrue(html.contains("Dimensionsfilter ist ausgeblendet"));
        assertTrue(html.contains("toggleAll(true)") || html.contains("expandAll(true)"));
        assertTrue(html.contains("Konzept") || html.contains("Typ") || html.contains("Periode") || html.contains("Einheit") || html.contains("Enumeration") || html.contains("Placeholder"));
    }
}