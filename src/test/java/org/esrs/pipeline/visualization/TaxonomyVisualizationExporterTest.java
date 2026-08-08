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
        Path treeHtml = outputDir.resolve("taxonomy-visualization-tree.html");
        Path graphHtml = outputDir.resolve("taxonomy-visualization-graph.html");
        Path layerHtml = outputDir.resolve("taxonomy-visualization-layer.html");
        Path matrixHtml = outputDir.resolve("taxonomy-visualization-matrix.html");
        Path flowHtml = outputDir.resolve("taxonomy-visualization-flow.html");

        TaxonomyVisualizationExporter exporter = new TaxonomyVisualizationExporter();
        assertDoesNotThrow(() -> exporter.export(
            MappingRegistry.fromPath(root.resolve("mapping/map-esrs-2023-12-22.json")),
            root,
            root.resolve("mapping/report-layout-map.json"),
            outputHtml
        ));

        assertTrue(Files.exists(outputHtml));
        assertTrue(Files.exists(treeHtml));
        assertTrue(Files.exists(graphHtml));
        assertTrue(Files.exists(layerHtml));
        assertTrue(Files.exists(matrixHtml));
        assertTrue(Files.exists(flowHtml));

        String html = Files.readString(outputHtml, StandardCharsets.UTF_8);
        assertTrue(html.contains("ESRS Taxonomie-Visualisierungen"));
        assertTrue(html.contains("taxonomy-visualization-tree.html"));
        assertTrue(html.contains("taxonomy-visualization-graph.html"));
        assertTrue(html.contains("taxonomy-visualization-layer.html"));
        assertTrue(html.contains("taxonomy-visualization-matrix.html"));
        assertTrue(html.contains("taxonomy-visualization-flow.html"));

        String tree = Files.readString(treeHtml, StandardCharsets.UTF_8);
        assertTrue(tree.contains("Präsentationshierarchie"));
        assertTrue(tree.contains("Taxonomie: "));

        String graph = Files.readString(graphHtml, StandardCharsets.UTF_8);
        assertTrue(graph.contains("layer-toggle"));

        String layer = Files.readString(layerHtml, StandardCharsets.UTF_8);
        assertTrue(layer.contains("Unterelemente einblenden"));
    }
}