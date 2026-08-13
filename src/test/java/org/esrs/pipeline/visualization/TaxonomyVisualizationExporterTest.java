package org.esrs.pipeline.visualization;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.support.TestTaxonomyFixture;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TaxonomyVisualizationExporterTest {
    @Test
    void shouldExportHierarchicalTaxonomyExplorer() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path taxonomyFixtureRoot = Files.createTempDirectory("esrs-taxonomy-fixture-viz");
        TestTaxonomyFixture.createMinimalFixture(taxonomyFixtureRoot, root.resolve("mapping/map-esrs-2023-12-22.json"));
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
        Path impactHeatmapHtml = outputDir.resolve("taxonomy-visualization-impact-heatmap.html");
        Path hypercubeDimensionInventoryHtml = outputDir.resolve("taxonomy-visualization-hypercube-dimension-inventory.html");
        Path mappingFlowHtml = outputDir.resolve("taxonomy-visualization-mapping-flow.html");
        Path conceptBacklogHtml = outputDir.resolve("taxonomy-visualization-concept-backlog.html");
        Path scopePeriodHtml = outputDir.resolve("taxonomy-visualization-scope-period-analysis.html");
        Path ruleCoverageMatrixHtml = outputDir.resolve("taxonomy-visualization-rule-coverage-matrix.html");
        Path intersectionRiskHtml = outputDir.resolve("taxonomy-visualization-intersection-risk.html");
        Path traceabilityMatrixHtml = outputDir.resolve("taxonomy-visualization-traceability-matrix.html");
        Path dimensionCooccurrenceHtml = outputDir.resolve("taxonomy-visualization-dimension-cooccurrence.html");
        Path defaultMemberQualityHtml = outputDir.resolve("taxonomy-visualization-default-member-quality.html");
        Path enumDomainValidityHtml = outputDir.resolve("taxonomy-visualization-enum-domain-validity.html");
        Path externalSchemasHtml = outputDir.resolve("taxonomy-visualization-external-schemas.html");
        Path dashboardHtml = outputDir.resolve("taxonomy-visualization-dashboard.html");

        TaxonomyVisualizationExporter exporter = new TaxonomyVisualizationExporter();
        assertDoesNotThrow(() -> exporter.export(
            MappingRegistry.fromPath(root.resolve("mapping/map-esrs-2023-12-22.json")),
            taxonomyFixtureRoot,
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
        assertTrue(Files.exists(impactHeatmapHtml));
        assertTrue(Files.exists(hypercubeDimensionInventoryHtml));
        assertTrue(Files.exists(mappingFlowHtml));
        assertTrue(Files.exists(conceptBacklogHtml));
        assertTrue(Files.exists(scopePeriodHtml));
        assertTrue(Files.exists(ruleCoverageMatrixHtml));
        assertTrue(Files.exists(intersectionRiskHtml));
        assertTrue(Files.exists(traceabilityMatrixHtml));
        assertTrue(Files.exists(dimensionCooccurrenceHtml));
        assertTrue(Files.exists(defaultMemberQualityHtml));
        assertTrue(Files.exists(enumDomainValidityHtml));
        assertTrue(Files.exists(externalSchemasHtml));
        assertTrue(Files.exists(dashboardHtml));

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
        assertTrue(html.contains("taxonomy-visualization-impact-heatmap.html"));
        assertTrue(html.contains("taxonomy-visualization-hypercube-dimension-inventory.html"));
        assertTrue(html.contains("taxonomy-visualization-mapping-flow.html"));
        assertTrue(html.contains("taxonomy-visualization-concept-backlog.html"));
        assertTrue(html.contains("taxonomy-visualization-scope-period-analysis.html"));
        assertTrue(html.contains("taxonomy-visualization-rule-coverage-matrix.html"));
        assertTrue(html.contains("taxonomy-visualization-intersection-risk.html"));
        assertTrue(html.contains("taxonomy-visualization-traceability-matrix.html"));
        assertTrue(html.contains("taxonomy-visualization-dimension-cooccurrence.html"));
        assertTrue(html.contains("taxonomy-visualization-default-member-quality.html"));
        assertTrue(html.contains("taxonomy-visualization-enum-domain-validity.html"));
        assertTrue(html.contains("taxonomy-visualization-external-schemas.html"));
        assertTrue(html.contains("taxonomy-visualization-dashboard.html"));

        String tree = Files.readString(treeHtml, StandardCharsets.UTF_8);
        assertTrue(tree.contains("Präsentationshierarchie"));
        assertTrue(tree.contains("taxonomy-node"));

        String graph = Files.readString(graphHtml, StandardCharsets.UTF_8);
        assertTrue(graph.contains("layer-toggle"));

        String layer = Files.readString(layerHtml, StandardCharsets.UTF_8);
        assertTrue(layer.contains("Unterelemente einblenden"));

        String hypercube = Files.readString(hypercubeHtml, StandardCharsets.UTF_8);
        assertTrue(hypercube.contains("Hypercube View"));

        String hypercube3d = Files.readString(hypercube3dHtml, StandardCharsets.UTF_8);
        assertTrue(hypercube3d.contains("Hypercube 3D View"));
        assertTrue(hypercube3d.contains("three.min.js"));

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

        String impactHeatmap = Files.readString(impactHeatmapHtml, StandardCharsets.UTF_8);
        assertTrue(impactHeatmap.contains("Impact Heatmap View"));

        String hypercubeDimensionInventory = Files.readString(hypercubeDimensionInventoryHtml, StandardCharsets.UTF_8);
        assertTrue(hypercubeDimensionInventory.contains("Hypercube Dimension Inventar"));

        String mappingFlow = Files.readString(mappingFlowHtml, StandardCharsets.UTF_8);
        assertTrue(mappingFlow.contains("Mapping Flow View"));

        String conceptBacklog = Files.readString(conceptBacklogHtml, StandardCharsets.UTF_8);
        assertTrue(conceptBacklog.contains("Concept Backlog View"));

        String scopePeriod = Files.readString(scopePeriodHtml, StandardCharsets.UTF_8);
        assertTrue(scopePeriod.contains("Scope & Period Analysis"));

        String ruleCoverageMatrix = Files.readString(ruleCoverageMatrixHtml, StandardCharsets.UTF_8);
        assertTrue(ruleCoverageMatrix.contains("Rule Coverage Matrix"));

        String intersectionRisk = Files.readString(intersectionRiskHtml, StandardCharsets.UTF_8);
        assertTrue(intersectionRisk.contains("Intersection Risk View"));

        String traceabilityMatrix = Files.readString(traceabilityMatrixHtml, StandardCharsets.UTF_8);
        assertTrue(traceabilityMatrix.contains("Traceability Matrix View"));

        String dimensionCooccurrence = Files.readString(dimensionCooccurrenceHtml, StandardCharsets.UTF_8);
        assertTrue(dimensionCooccurrence.contains("Dimension Co-Occurrence View"));

        String defaultMemberQuality = Files.readString(defaultMemberQualityHtml, StandardCharsets.UTF_8);
        assertTrue(defaultMemberQuality.contains("Default Member Quality View"));

        String enumDomainValidity = Files.readString(enumDomainValidityHtml, StandardCharsets.UTF_8);
        assertTrue(enumDomainValidity.contains("Enum Domain Validity View"));

        String externalSchemas = Files.readString(externalSchemasHtml, StandardCharsets.UTF_8);
        assertTrue(externalSchemas.contains("External Schema References"));
        assertTrue(externalSchemas.contains("http://www.xbrl.org/dtr/type/2022-03-31"));
        assertTrue(externalSchemas.contains("http://www.xbrl.org/2003/linkbase"));
        assertTrue(externalSchemas.contains("http://www.w3.org/1999/xlink"));
        assertTrue(externalSchemas.contains("http://xbrl.org/2005/xbrldt"));
        assertTrue(externalSchemas.contains("externalSchemaNodes"));
        assertTrue(externalSchemas.contains("externalSchemaEdges"));
        assertTrue(externalSchemas.contains("externalSchemaSearch"));
        assertTrue(externalSchemas.contains("External Schema Dependency Graph"));
        assertTrue(externalSchemas.contains("Analysierte XSD-Typen"));
        assertTrue(externalSchemas.contains("Typ-Inventar"));
        assertTrue(externalSchemas.contains("domainItemType"));
        assertTrue(externalSchemas.contains("enumeration"));
        assertTrue(externalSchemas.contains("externalTypeSearch"));
        assertTrue(externalSchemas.contains("externalNamespaceSearch"));
        assertTrue(externalSchemas.contains("applyExternalSchemaTableFilters"));
        assertTrue(externalSchemas.contains("external-type-row"));
        assertTrue(externalSchemas.contains("external-namespace-row"));

        String dashboard = Files.readString(dashboardHtml, StandardCharsets.UTF_8);
        assertTrue(dashboard.contains("Master Dashboard"));
    }
}