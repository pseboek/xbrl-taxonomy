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
        Path hypercubeHtml = outputDir.resolve("taxonomy-visualization-hypercube.html");
        Path hypercube3dHtml = outputDir.resolve("taxonomy-visualization-hypercube-3d.html");
        Path coverageHtml = outputDir.resolve("taxonomy-visualization-coverage.html");
        Path enumerationHtml = outputDir.resolve("taxonomy-visualization-enumeration.html");
        Path referenceHtml = outputDir.resolve("taxonomy-visualization-reference.html");
        Path calculationHtml = outputDir.resolve("taxonomy-visualization-calculation.html");
        Path intersectionHtml = outputDir.resolve("taxonomy-visualization-intersection.html");
        Path validationHtml = outputDir.resolve("taxonomy-visualization-validation.html");
        Path allocationHtml = outputDir.resolve("taxonomy-visualization-allocation.html");
        Path statsHtml = outputDir.resolve("taxonomy-visualization-stats.html");
        Path complexityHtml = outputDir.resolve("taxonomy-visualization-complexity.html");

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
        assertTrue(Files.exists(hypercubeHtml));
        assertTrue(Files.exists(hypercube3dHtml));
        assertTrue(Files.exists(coverageHtml));
        assertTrue(Files.exists(enumerationHtml));
        assertTrue(Files.exists(referenceHtml));
        assertTrue(Files.exists(calculationHtml));
        assertTrue(Files.exists(intersectionHtml));
        assertTrue(Files.exists(validationHtml));
        assertTrue(Files.exists(allocationHtml));
        assertTrue(Files.exists(statsHtml));
        assertTrue(Files.exists(complexityHtml));

        String html = Files.readString(outputHtml, StandardCharsets.UTF_8);
        assertTrue(html.contains("ESRS Taxonomie-Visualisierungen"));
        assertTrue(html.contains("taxonomy-visualization-tree.html"));
        assertTrue(html.contains("taxonomy-visualization-graph.html"));
        assertTrue(html.contains("taxonomy-visualization-layer.html"));
        assertTrue(html.contains("taxonomy-visualization-matrix.html"));
        assertTrue(html.contains("taxonomy-visualization-flow.html"));
        assertTrue(html.contains("taxonomy-visualization-hypercube.html"));
        assertTrue(html.contains("taxonomy-visualization-hypercube-3d.html"));
        assertTrue(html.contains("taxonomy-visualization-coverage.html"));
        assertTrue(html.contains("taxonomy-visualization-enumeration.html"));
        assertTrue(html.contains("taxonomy-visualization-reference.html"));
        assertTrue(html.contains("taxonomy-visualization-calculation.html"));
        assertTrue(html.contains("taxonomy-visualization-intersection.html"));
        assertTrue(html.contains("taxonomy-visualization-validation.html"));
        assertTrue(html.contains("taxonomy-visualization-allocation.html"));
        assertTrue(html.contains("taxonomy-visualization-stats.html"));
        assertTrue(html.contains("taxonomy-visualization-complexity.html"));

        String tree = Files.readString(treeHtml, StandardCharsets.UTF_8);
        assertTrue(tree.contains("Präsentationshierarchie"));
        assertTrue(tree.contains("Taxonomie: "));

        String graph = Files.readString(graphHtml, StandardCharsets.UTF_8);
        assertTrue(graph.contains("layer-toggle"));

        String layer = Files.readString(layerHtml, StandardCharsets.UTF_8);
        assertTrue(layer.contains("Unterelemente einblenden"));

        String hypercube = Files.readString(hypercubeHtml, StandardCharsets.UTF_8);
        assertTrue(hypercube.contains("Hypercube View"));

        String hypercube3d = Files.readString(hypercube3dHtml, StandardCharsets.UTF_8);
        assertTrue(hypercube3d.contains("Hypercube 3D View"));
        assertTrue(hypercube3d.contains("unpkg.com/three"));

        String coverage = Files.readString(coverageHtml, StandardCharsets.UTF_8);
        assertTrue(coverage.contains("Coverage View"));

        String enumeration = Files.readString(enumerationHtml, StandardCharsets.UTF_8);
        assertTrue(enumeration.contains("Enumeration View"));

        String reference = Files.readString(referenceHtml, StandardCharsets.UTF_8);
        assertTrue(reference.contains("Reference View"));

        String calculation = Files.readString(calculationHtml, StandardCharsets.UTF_8);
        assertTrue(calculation.contains("Calculation View"));

        String intersection = Files.readString(intersectionHtml, StandardCharsets.UTF_8);
        assertTrue(intersection.contains("Intersection View"));

        String validation = Files.readString(validationHtml, StandardCharsets.UTF_8);
        assertTrue(validation.contains("Validation View"));

        String allocation = Files.readString(allocationHtml, StandardCharsets.UTF_8);
        assertTrue(allocation.contains("Allocation View"));

        String stats = Files.readString(statsHtml, StandardCharsets.UTF_8);
        assertTrue(stats.contains("Stats View"));

        String complexity = Files.readString(complexityHtml, StandardCharsets.UTF_8);
        assertTrue(complexity.contains("Complexity View"));
    }
}