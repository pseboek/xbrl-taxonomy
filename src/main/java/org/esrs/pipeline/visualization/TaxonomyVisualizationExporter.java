package org.esrs.pipeline.visualization;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.esrs.pipeline.mapping.MappingEntry;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DimensionSelection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaxonomyVisualizationExporter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient EXTERNAL_SCHEMA_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final String TAXONOMY_PATH = "xbrl.efrag.org/taxonomy/esrs/2023-12-22";
    private static final String LINK_NS = "http://www.xbrl.org/2003/linkbase";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String PARENT_CHILD_ARCROLE = "http://www.xbrl.org/2003/arcrole/parent-child";
    private static final String DIM_ARCROLE_ALL = "http://xbrl.org/int/dim/arcrole/all";
    private static final String DIM_ARCROLE_NOT_ALL = "http://xbrl.org/int/dim/arcrole/notAll";
    private static final String DIM_ARCROLE_HYPERCUBE_DIMENSION = "http://xbrl.org/int/dim/arcrole/hypercube-dimension";
    private static final String DIM_ARCROLE_DIMENSION_DOMAIN = "http://xbrl.org/int/dim/arcrole/dimension-domain";
    private static final String DIM_ARCROLE_DOMAIN_MEMBER = "http://xbrl.org/int/dim/arcrole/domain-member";
    private static final String DIM_ARCROLE_DIMENSION_DEFAULT = "http://xbrl.org/int/dim/arcrole/dimension-default";
    private static final Pattern XSD_ELEMENT_TAG_PATTERN = Pattern.compile("<(?:xsd|xs):element\\b[^>]*>");
    private static final Pattern XSD_NAME_ATTR_PATTERN = Pattern.compile("\\bname=\"([^\"]+)\"");
    private static final Pattern XSD_TYPE_ATTR_PATTERN = Pattern.compile("\\btype=\"([^\"]+)\"");
    private static final Pattern XSD_ENUM_DOMAIN_ATTR_PATTERN = Pattern.compile("\\benum2:domain=\"([^\"]+)\"");
    private static final Pattern XSD_ENUM_LINKROLE_ATTR_PATTERN = Pattern.compile("\\benum2:linkrole=\"([^\"]+)\"");
    private static final Pattern FORMULA_ESRS_QNAME_PATTERN = Pattern.compile("\\besrs:[A-Za-z0-9_]+");
    public VisualizationResult export(MappingRegistry mappingRegistry,
                                      Path taxonomyRoot,
                                      Path layoutMap,
                                      Path outputHtml) throws IOException {
        if (outputHtml.getParent() != null) {
            Files.createDirectories(outputHtml.getParent());
        }

        List<MappingEntry> entries = new ArrayList<>(mappingRegistry.all().values());
        entries.sort(Comparator.comparing(MappingEntry::field));

        LayoutSnapshot layoutSnapshot = loadLayoutSnapshot(layoutMap);
        PresentationForest forest = loadPresentationForest(taxonomyRoot);
        TaxonomyMetadata metadata = loadTaxonomyMetadata(taxonomyRoot);
        Map<String, List<MappingEntry>> mappingsByConcept = groupByConcept(entries);
        Map<String, List<String>> placeholdersByField = reverseLayout(layoutSnapshot.placeholderMappings());
        Map<String, List<String>> conceptReferences = loadConceptReferences(taxonomyRoot);

        String stem = outputHtml.getFileName().toString().replaceFirst("\\.html$", "");
        Path treeHtml = outputHtml.resolveSibling(stem + "-tree.html");
        Path graphHtml = outputHtml.resolveSibling(stem + "-graph.html");
        Path layerHtml = outputHtml.resolveSibling(stem + "-layer.html");
        Path matrixHtml = outputHtml.resolveSibling(stem + "-matrix.html");
        Path flowHtml = outputHtml.resolveSibling(stem + "-flow.html");
        Path hypercubeHtml = outputHtml.resolveSibling(stem + "-hypercube.html");
        Path hypercube3dHtml = outputHtml.resolveSibling(stem + "-hypercube-3d.html");
        Path localThreeJs = outputHtml.resolveSibling("three.min.js");
        Path coverageHtml = outputHtml.resolveSibling(stem + "-coverage.html");
        Path enumerationHtml = outputHtml.resolveSibling(stem + "-enumeration.html");
        Path referenceHtml = outputHtml.resolveSibling(stem + "-reference.html");
        Path calculationHtml = outputHtml.resolveSibling(stem + "-calculation.html");
        Path intersectionHtml = outputHtml.resolveSibling(stem + "-intersection.html");
        Path validationHtml = outputHtml.resolveSibling(stem + "-validation.html");
        Path allocationHtml = outputHtml.resolveSibling(stem + "-allocation.html");
        Path statsHtml = outputHtml.resolveSibling(stem + "-stats.html");
        Path complexityHtml = outputHtml.resolveSibling(stem + "-complexity.html");
        Path impactHeatmapHtml = outputHtml.resolveSibling(stem + "-impact-heatmap.html");
        Path hypercubeDimensionInventoryHtml = outputHtml.resolveSibling(stem + "-hypercube-dimension-inventory.html");
        Path mappingFlowHtml = outputHtml.resolveSibling(stem + "-mapping-flow.html");
        Path conceptBacklogHtml = outputHtml.resolveSibling(stem + "-concept-backlog.html");
        Path scopePeriodHtml = outputHtml.resolveSibling(stem + "-scope-period-analysis.html");
        Path ruleCoverageMatrixHtml = outputHtml.resolveSibling(stem + "-rule-coverage-matrix.html");
        Path intersectionRiskHtml = outputHtml.resolveSibling(stem + "-intersection-risk.html");
        Path traceabilityMatrixHtml = outputHtml.resolveSibling(stem + "-traceability-matrix.html");
        Path dimensionCooccurrenceHtml = outputHtml.resolveSibling(stem + "-dimension-cooccurrence.html");
        Path defaultMemberQualityHtml = outputHtml.resolveSibling(stem + "-default-member-quality.html");
        Path enumDomainValidityHtml = outputHtml.resolveSibling(stem + "-enum-domain-validity.html");
        Path externalSchemasHtml = outputHtml.resolveSibling(stem + "-external-schemas.html");
        Path dashboardHtml = outputHtml.resolveSibling(stem + "-dashboard.html");

        Files.writeString(treeHtml, renderTreeHtml(forest, metadata, mappingsByConcept, placeholdersByField, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(graphHtml, renderGraphHtml(metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(layerHtml, renderLayerHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(matrixHtml, renderMatrixHtml(mappingsByConcept, placeholdersByField, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(flowHtml, renderFlowHtml(forest, metadata, mappingsByConcept, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(hypercubeHtml, renderHypercubeHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(hypercube3dHtml, renderHypercube3dHtml(metadata), StandardCharsets.UTF_8);
        copyLocalThreeBundle(taxonomyRoot, localThreeJs);
        Files.writeString(coverageHtml, renderCoverageHtml(forest, mappingsByConcept, placeholdersByField, metadata), StandardCharsets.UTF_8);
        Files.writeString(enumerationHtml, renderEnumerationHtml(mappingsByConcept, placeholdersByField, metadata), StandardCharsets.UTF_8);
        Files.writeString(referenceHtml, renderReferenceHtml(mappingsByConcept, placeholdersByField, conceptReferences), StandardCharsets.UTF_8);
        Files.writeString(calculationHtml, renderCalculationHtml(metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(intersectionHtml, renderIntersectionHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(validationHtml, renderValidationHtml(metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(allocationHtml, renderAllocationHtml(layoutSnapshot, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(statsHtml, renderStatsHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(complexityHtml, renderComplexityHtml(forest, metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(impactHeatmapHtml, renderImpactHeatmapHtml(forest, metadata, mappingsByConcept, placeholdersByField), StandardCharsets.UTF_8);
        Files.writeString(hypercubeDimensionInventoryHtml, renderHypercubeDimensionInventoryHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(mappingFlowHtml, renderMappingFlowHtml(forest, mappingsByConcept, metadata), StandardCharsets.UTF_8);
        Files.writeString(conceptBacklogHtml, renderConceptBacklogHtml(forest, mappingsByConcept, placeholdersByField, metadata), StandardCharsets.UTF_8);
        Files.writeString(scopePeriodHtml, renderScopePeriodAnalysisHtml(forest, metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(ruleCoverageMatrixHtml, renderRuleCoverageMatrixHtml(metadata, mappingsByConcept), StandardCharsets.UTF_8);
        Files.writeString(intersectionRiskHtml, renderIntersectionRiskHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(traceabilityMatrixHtml, renderTraceabilityMatrixHtml(mappingsByConcept, placeholdersByField, conceptReferences, metadata), StandardCharsets.UTF_8);
        Files.writeString(dimensionCooccurrenceHtml, renderDimensionCooccurrenceHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(defaultMemberQualityHtml, renderDefaultMemberQualityHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(enumDomainValidityHtml, renderEnumDomainValidityHtml(mappingsByConcept, metadata), StandardCharsets.UTF_8);
        Files.writeString(externalSchemasHtml, renderExternalSchemasHtml(metadata.externalSchemaReferences(), metadata.externalSchemaTypes(), metadata.externalSchemaEdges(), metadata.externalSchemaSubstitutions()), StandardCharsets.UTF_8);
        Files.writeString(dashboardHtml, renderDashboardHtml(treeHtml, graphHtml, layerHtml, matrixHtml, flowHtml, hypercubeHtml, hypercube3dHtml, coverageHtml, enumerationHtml, referenceHtml, calculationHtml, intersectionHtml, validationHtml, allocationHtml, statsHtml, complexityHtml, impactHeatmapHtml, hypercubeDimensionInventoryHtml, mappingFlowHtml, conceptBacklogHtml, scopePeriodHtml, ruleCoverageMatrixHtml, intersectionRiskHtml, traceabilityMatrixHtml, dimensionCooccurrenceHtml, defaultMemberQualityHtml, enumDomainValidityHtml, externalSchemasHtml), StandardCharsets.UTF_8);
        Files.writeString(outputHtml, renderOverviewHtml(forest, metadata, mappingsByConcept, layoutSnapshot, treeHtml, graphHtml, layerHtml, matrixHtml, flowHtml, hypercubeHtml, hypercube3dHtml, coverageHtml, enumerationHtml, referenceHtml, calculationHtml, intersectionHtml, validationHtml, allocationHtml, statsHtml, complexityHtml, impactHeatmapHtml, hypercubeDimensionInventoryHtml, mappingFlowHtml, conceptBacklogHtml, scopePeriodHtml, ruleCoverageMatrixHtml, intersectionRiskHtml, traceabilityMatrixHtml, dimensionCooccurrenceHtml, defaultMemberQualityHtml, enumDomainValidityHtml, externalSchemasHtml, dashboardHtml), StandardCharsets.UTF_8);

        return new VisualizationResult(
            outputHtml,
            forest.roleCount(),
            forest.nodeCount(),
            entries.size(),
            mappingsByConcept.size(),
            layoutSnapshot.placeholderMappings().size()
        );
    }

    private void copyLocalThreeBundle(Path taxonomyRoot, Path targetFile) throws IOException {
        Path localBundle = taxonomyRoot.resolve("templates").resolve("assets").resolve("vendor").resolve("three.min.js");
        if (!Files.exists(localBundle)) {
            return;
        }
        Files.copy(localBundle, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private LayoutSnapshot loadLayoutSnapshot(Path layoutMap) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(layoutMap, StandardCharsets.UTF_8));
        Map<String, String> placeholders = new TreeMap<>();
        JsonNode placeholderNode = root.path("placeholders");
        if (placeholderNode.isObject()) {
            placeholderNode.fields().forEachRemaining(entry -> placeholders.put(entry.getKey(), entry.getValue().asText()));
        }
        return new LayoutSnapshot(placeholders);
    }

    private Map<String, List<String>> loadConceptReferences(Path taxonomyRoot) throws IOException {
        Path referenceCsv = taxonomyRoot.resolve("output").resolve("arelle-concept-reference.csv");
        if (!Files.exists(referenceCsv)) {
            return Map.of();
        }

        Map<String, Set<String>> referencesByConcept = new TreeMap<>();
        String currentConcept = null;

        List<String> lines = Files.readAllLines(referenceCsv, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            List<String> columns = parseCsvLine(line);
            if (columns.size() < 3) {
                continue;
            }

            String conceptCandidate = column(columns, 1);
            if (conceptCandidate.startsWith("esrs:")) {
                currentConcept = normalizeConceptKey(conceptCandidate);
                referencesByConcept.computeIfAbsent(currentConcept, key -> new TreeSet<>());
                continue;
            }

            String referenceText = column(columns, 2).trim();
            String arcrole = column(columns, 3);
            if (currentConcept != null
                && !referenceText.isBlank()
                && arcrole.contains("concept-reference")) {
                referencesByConcept.computeIfAbsent(currentConcept, key -> new TreeSet<>()).add(referenceText);
            }
        }

        Map<String, List<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : referencesByConcept.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private String column(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index) == null ? "" : values.get(index);
    }

    private Map<String, List<String>> reverseLayout(Map<String, String> placeholderMappings) {
        Map<String, List<String>> fields = new TreeMap<>();
        for (Map.Entry<String, String> entry : placeholderMappings.entrySet()) {
            fields.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        for (List<String> values : fields.values()) {
            values.sort(String::compareTo);
        }
        return fields;
    }

    private PresentationForest loadPresentationForest(Path taxonomyRoot) throws IOException {
        Path linkbaseDir = taxonomyRoot.resolve(TAXONOMY_PATH).resolve("all").resolve("linkbases");
        if (!Files.exists(linkbaseDir)) {
            return new PresentationForest(List.of(), 0);
        }

        List<Path> files;
        try (var stream = Files.list(linkbaseDir)) {
            files = stream
                .filter(path -> path.getFileName().toString().startsWith("pre_esrs_") && path.getFileName().toString().endsWith(".xml"))
                .sorted()
                .toList();
        }

        List<PresentationRoleGraph> roles = new ArrayList<>();
        int nodeCount = 0;
        for (Path file : files) {
            List<PresentationRoleGraph> parsedRoles = parsePresentationFile(file);
            roles.addAll(parsedRoles);
            for (PresentationRoleGraph role : parsedRoles) {
                nodeCount += role.nodeCount();
            }
        }
        return new PresentationForest(roles, nodeCount);
    }

    private List<PresentationRoleGraph> parsePresentationFile(Path file) throws IOException {
        Document document = parseXml(file);
        NodeList linkNodes = document.getElementsByTagNameNS(LINK_NS, "presentationLink");
        List<PresentationRoleGraph> graphs = new ArrayList<>();

        for (int i = 0; i < linkNodes.getLength(); i++) {
            Element linkElement = (Element) linkNodes.item(i);
            String roleUri = linkElement.getAttributeNS(XLINK_NS, "role");
            String roleLabel = roleLabel(file, roleUri);

            Map<String, String> labelToQname = new LinkedHashMap<>();
            Map<String, List<PresentationArc>> outgoingArcs = new HashMap<>();
            Set<String> incomingLabels = new HashSet<>();

            NodeList children = linkElement.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element childElement = (Element) child;
                String localName = childElement.getLocalName();
                if ("loc".equals(localName)) {
                    String label = childElement.getAttributeNS(XLINK_NS, "label");
                    String href = childElement.getAttributeNS(XLINK_NS, "href");
                    labelToQname.put(label, extractQName(href));
                } else if ("presentationArc".equals(localName)) {
                    String arcrole = childElement.getAttributeNS(XLINK_NS, "arcrole");
                    if (!PARENT_CHILD_ARCROLE.equals(arcrole)) {
                        continue;
                    }

                    String from = childElement.getAttributeNS(XLINK_NS, "from");
                    String to = childElement.getAttributeNS(XLINK_NS, "to");
                    double order = parseOrder(childElement.getAttribute("order"));
                    outgoingArcs.computeIfAbsent(from, key -> new ArrayList<>()).add(new PresentationArc(from, to, order));
                    incomingLabels.add(to);
                }
            }

            for (List<PresentationArc> arcs : outgoingArcs.values()) {
                arcs.sort(Comparator.comparingDouble(PresentationArc::order).thenComparing(PresentationArc::to));
            }

            List<String> roots = labelToQname.keySet().stream()
                .filter(label -> !incomingLabels.contains(label))
                .sorted(Comparator.comparing(label -> humanize(labelToQname.get(label))))
                .toList();

            graphs.add(new PresentationRoleGraph(roleUri, roleLabel, labelToQname, outgoingArcs, roots));
        }

        return graphs;
    }

    private Document parseXml(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse presentation linkbase: " + file, e);
        }
    }

    private String extractQName(String href) {
        int hash = href.lastIndexOf('#');
        if (hash >= 0 && hash < href.length() - 1) {
            return href.substring(hash + 1);
        }
        return href;
    }

    private double parseOrder(String order) {
        try {
            return Double.parseDouble(order);
        } catch (NumberFormatException ex) {
            return Double.MAX_VALUE;
        }
    }

    private Map<String, List<MappingEntry>> groupByConcept(List<MappingEntry> entries) {
        Map<String, List<MappingEntry>> concepts = new TreeMap<>();
        for (MappingEntry entry : entries) {
            concepts.computeIfAbsent(normalizeConceptKey(entry.concept()), key -> new ArrayList<>()).add(entry);
        }
        for (List<MappingEntry> conceptEntries : concepts.values()) {
            conceptEntries.sort(Comparator.comparing(MappingEntry::field));
        }
        return concepts;
    }

    private TaxonomyMetadata loadTaxonomyMetadata(Path taxonomyRoot) throws IOException {
        Path taxonomyBase = taxonomyRoot.resolve(TAXONOMY_PATH);
        if (!Files.exists(taxonomyBase)) {
            return new TaxonomyMetadata(0, 0, Map.of(), Map.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), new HypercubeMetadata(List.of(), 0), Map.of(), List.of(), List.of(), List.of(), List.of());
        }

        long xsdElementCount = 0;
        long xsdImportCount = 0;
        Map<String, Long> fileCountByLayer = new TreeMap<>();
        Map<String, Long> edgeCountByLayer = new TreeMap<>();
        Map<String, List<LinkEdge>> sampleEdgesByLayer = new TreeMap<>();
        Set<String> sampledEdgeKeys = new HashSet<>();
        final int maxSamplePerLayer = 180;
        Set<String> hrefTargets = new TreeSet<>();
        Map<String, TaxonomyEnumeration> taxonomyEnumerationsByConcept = new TreeMap<>();
        Map<String, Integer> formulaMentionsByConcept = new TreeMap<>();
        Map<String, Set<String>> formulaConceptsByFileRaw = new TreeMap<>();
        List<DimensionalArc> dimensionalArcs = new ArrayList<>();
        Map<String, ExternalSchemaReference> externalSchemaReferencesByNamespace = new TreeMap<>();

        try (Stream<Path> stream = Files.walk(taxonomyBase)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.endsWith(".xsd")) {
                    String xsdText = Files.readString(file, StandardCharsets.UTF_8);
                    xsdElementCount += countOccurrences(xsdText, "<xsd:element") + countOccurrences(xsdText, "<xs:element");
                    xsdImportCount += countOccurrences(xsdText, "<xsd:import") + countOccurrences(xsdText, "<xs:import");
                    xsdImportCount += countOccurrences(xsdText, "<xsd:include") + countOccurrences(xsdText, "<xs:include");
                    collectHrefTargetsFromText(xsdText, hrefTargets);
                    collectEnumerationConceptsFromText(xsdText, taxonomyEnumerationsByConcept);
                    collectExternalSchemaReferences(file, xsdText, externalSchemaReferencesByNamespace);
                    continue;
                }

                if (fileName.endsWith(".xml")) {
                    String xmlText = Files.readString(file, StandardCharsets.UTF_8);
                    collectExternalSchemaReferences(file, xmlText, externalSchemaReferencesByNamespace);
                }
                if (!fileName.endsWith(".xml")) {
                    continue;
                }

                String layer = detectLayer(fileName);
                fileCountByLayer.merge(layer, 1L, Long::sum);

                if (isFormulaFile(file)) {
                    String formulaXml = Files.readString(file, StandardCharsets.UTF_8);
                    collectFormulaConceptMentionsFromText(formulaXml, formulaMentionsByConcept);
                    collectFormulaConceptsByFile(formulaXml, file, taxonomyRoot, formulaConceptsByFileRaw);
                }

                Document document = parseXml(file);
                collectHrefTargets(document, hrefTargets);

                Map<String, String> locators = new HashMap<>();
                NodeList locNodes = document.getElementsByTagNameNS(LINK_NS, "loc");
                for (int i = 0; i < locNodes.getLength(); i++) {
                    Element loc = (Element) locNodes.item(i);
                    String label = loc.getAttributeNS(XLINK_NS, "label");
                    String href = loc.getAttributeNS(XLINK_NS, "href");
                    if (label != null && !label.isBlank()) {
                        locators.put(label, extractQName(href));
                    }
                }

                NodeList allNodes = document.getElementsByTagName("*");
                for (int i = 0; i < allNodes.getLength(); i++) {
                    Element element = (Element) allNodes.item(i);
                    String localName = element.getLocalName();
                    if (localName == null || !localName.endsWith("Arc")) {
                        continue;
                    }

                    edgeCountByLayer.merge(layer, 1L, Long::sum);
                    String from = element.getAttributeNS(XLINK_NS, "from");
                    String to = element.getAttributeNS(XLINK_NS, "to");
                    String source = locators.getOrDefault(from, from);
                    String target = locators.getOrDefault(to, to);
                    String arcrole = element.getAttributeNS(XLINK_NS, "arcrole");
                    if (source != null && !source.isBlank() && target != null && !target.isBlank()) {
                        String edgeKey = layer + "|" + source + "|" + target;
                        List<LinkEdge> layerSample = sampleEdgesByLayer.computeIfAbsent(layer, key -> new ArrayList<>());
                        if (layerSample.size() < maxSamplePerLayer && sampledEdgeKeys.add(edgeKey)) {
                            layerSample.add(new LinkEdge(source, target, layer));
                        }

                        if (isDimensionalArcrole(arcrole)) {
                            dimensionalArcs.add(new DimensionalArc(source, target, arcrole));
                        }
                    }
                }
            }
        }

        List<LinkEdge> sampleEdges = new ArrayList<>();
        List<String> preferredOrder = List.of("presentation", "calculation", "definition", "dimension", "label", "reference", "other");
        final int samplePerLayerBudget = 70;
        final int sampleTotalBudget = 420;

        for (String layer : preferredOrder) {
            List<LinkEdge> layerSample = sampleEdgesByLayer.get(layer);
            if (layerSample == null || layerSample.isEmpty()) {
                continue;
            }
            int limit = Math.min(samplePerLayerBudget, layerSample.size());
            for (int i = 0; i < limit && sampleEdges.size() < sampleTotalBudget; i++) {
                sampleEdges.add(layerSample.get(i));
            }
            if (sampleEdges.size() >= sampleTotalBudget) {
                break;
            }
        }

        if (sampleEdges.size() < sampleTotalBudget) {
            for (Map.Entry<String, List<LinkEdge>> entry : sampleEdgesByLayer.entrySet()) {
                if (preferredOrder.contains(entry.getKey())) {
                    continue;
                }
                for (LinkEdge edge : entry.getValue()) {
                    if (sampleEdges.size() >= sampleTotalBudget) {
                        break;
                    }
                    sampleEdges.add(edge);
                }
                if (sampleEdges.size() >= sampleTotalBudget) {
                    break;
                }
            }
        }

        List<String> hrefSample = hrefTargets.stream().limit(80).toList();
        HypercubeMetadata hypercubeMetadata = buildHypercubeMetadata(dimensionalArcs);
        Map<String, List<String>> domainMembersByDomain = buildDomainMembersByDomain(dimensionalArcs);
        Map<String, List<String>> formulaConceptsByFile = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : formulaConceptsByFileRaw.entrySet()) {
            formulaConceptsByFile.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        List<ExternalSchemaReference> externalSchemaReferences = externalSchemaReferencesByNamespace.values().stream()
            .sorted(Comparator.comparing(ExternalSchemaReference::namespace).thenComparing(ExternalSchemaReference::schemaLocation))
            .toList();
        ExternalSchemaAnalysis externalSchemaAnalysis = analyzeExternalSchemas(externalSchemaReferences);
        List<ExternalSchemaType> mergedTypes = mergeExternalSchemaTypes(externalSchemaAnalysis.types(), taxonomyBase);
        List<ExternalSchemaSubstitution> substitutions = mergeExternalSchemaSubstitutions(externalSchemaAnalysis.substitutions(), taxonomyBase);

        return new TaxonomyMetadata(
            xsdElementCount,
            xsdImportCount,
            fileCountByLayer,
            edgeCountByLayer,
            sampleEdges,
            hrefSample,
            taxonomyEnumerationsByConcept,
            formulaMentionsByConcept,
            formulaConceptsByFile,
            hypercubeMetadata,
            domainMembersByDomain,
            externalSchemaReferences,
            mergedTypes,
            externalSchemaAnalysis.edges(),
            substitutions
        );
    }

    private boolean isFormulaFile(Path file) {
        String normalized = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/all/formula/") && normalized.endsWith(".xml");
    }

    private void collectFormulaConceptMentionsFromText(String formulaXml,
                                                       Map<String, Integer> formulaMentionsByConcept) {
        if (formulaXml == null || formulaXml.isBlank()) {
            return;
        }
        Matcher matcher = FORMULA_ESRS_QNAME_PATTERN.matcher(formulaXml);
        while (matcher.find()) {
            String qname = matcher.group();
            formulaMentionsByConcept.merge(normalizeConceptKey(qname), 1, Integer::sum);
        }
    }

    private void collectFormulaConceptsByFile(String formulaXml,
                                              Path formulaFile,
                                              Path taxonomyRoot,
                                              Map<String, Set<String>> formulaConceptsByFile) {
        if (formulaXml == null || formulaXml.isBlank()) {
            return;
        }
        String fileLabel = taxonomyRoot.relativize(formulaFile).toString().replace('\\', '/');
        Set<String> concepts = formulaConceptsByFile.computeIfAbsent(fileLabel, key -> new TreeSet<>());
        Matcher matcher = FORMULA_ESRS_QNAME_PATTERN.matcher(formulaXml);
        while (matcher.find()) {
            concepts.add(matcher.group());
        }
    }

    private boolean isDimensionalArcrole(String arcrole) {
        if (arcrole == null || arcrole.isBlank()) {
            return false;
        }
        return DIM_ARCROLE_ALL.equals(arcrole)
            || DIM_ARCROLE_NOT_ALL.equals(arcrole)
            || DIM_ARCROLE_HYPERCUBE_DIMENSION.equals(arcrole)
            || DIM_ARCROLE_DIMENSION_DOMAIN.equals(arcrole)
            || DIM_ARCROLE_DOMAIN_MEMBER.equals(arcrole)
            || DIM_ARCROLE_DIMENSION_DEFAULT.equals(arcrole);
    }

    private HypercubeMetadata buildHypercubeMetadata(List<DimensionalArc> dimensionalArcs) {
        if (dimensionalArcs == null || dimensionalArcs.isEmpty()) {
            return new HypercubeMetadata(List.of(), 0);
        }

        Map<String, Set<String>> primariesAllByCube = new TreeMap<>();
        Map<String, Set<String>> primariesNotAllByCube = new TreeMap<>();
        Map<String, Set<String>> dimensionsByCube = new TreeMap<>();
        Map<String, Set<String>> domainsByDimension = new TreeMap<>();
        Map<String, Set<String>> membersByDomain = new TreeMap<>();
        Map<String, Set<String>> defaultsByDimension = new TreeMap<>();

        for (DimensionalArc arc : dimensionalArcs) {
            switch (arc.arcrole()) {
                case DIM_ARCROLE_ALL -> primariesAllByCube.computeIfAbsent(arc.target(), key -> new TreeSet<>()).add(arc.source());
                case DIM_ARCROLE_NOT_ALL -> primariesNotAllByCube.computeIfAbsent(arc.target(), key -> new TreeSet<>()).add(arc.source());
                case DIM_ARCROLE_HYPERCUBE_DIMENSION -> dimensionsByCube.computeIfAbsent(arc.source(), key -> new TreeSet<>()).add(arc.target());
                case DIM_ARCROLE_DIMENSION_DOMAIN -> domainsByDimension.computeIfAbsent(arc.source(), key -> new TreeSet<>()).add(arc.target());
                case DIM_ARCROLE_DOMAIN_MEMBER -> membersByDomain.computeIfAbsent(arc.source(), key -> new TreeSet<>()).add(arc.target());
                case DIM_ARCROLE_DIMENSION_DEFAULT -> defaultsByDimension.computeIfAbsent(arc.source(), key -> new TreeSet<>()).add(arc.target());
                default -> {
                    // no-op
                }
            }
        }

        Set<String> cubeNames = new TreeSet<>();
        cubeNames.addAll(primariesAllByCube.keySet());
        cubeNames.addAll(primariesNotAllByCube.keySet());
        cubeNames.addAll(dimensionsByCube.keySet());

        List<HypercubeCube> cubes = new ArrayList<>();
        int relationCount = dimensionalArcs.size();

        for (String cube : cubeNames) {
            List<String> allPrimaries = new ArrayList<>(primariesAllByCube.getOrDefault(cube, Set.of()));
            List<String> notAllPrimaries = new ArrayList<>(primariesNotAllByCube.getOrDefault(cube, Set.of()));
            List<String> dimensions = new ArrayList<>(dimensionsByCube.getOrDefault(cube, Set.of()));

            Map<String, List<String>> domainsPerDimension = new LinkedHashMap<>();
            Map<String, List<String>> defaultsPerDimension = new LinkedHashMap<>();
            Map<String, List<String>> membersPerDomain = new LinkedHashMap<>();

            for (String dimension : dimensions) {
                List<String> domains = new ArrayList<>(domainsByDimension.getOrDefault(dimension, Set.of()));
                domainsPerDimension.put(dimension, domains);
                defaultsPerDimension.put(dimension, new ArrayList<>(defaultsByDimension.getOrDefault(dimension, Set.of())));
                for (String domain : domains) {
                    membersPerDomain.put(domain, new ArrayList<>(membersByDomain.getOrDefault(domain, Set.of())));
                }
            }

            cubes.add(new HypercubeCube(cube, allPrimaries, notAllPrimaries, dimensions, domainsPerDimension, defaultsPerDimension, membersPerDomain));
        }

        return new HypercubeMetadata(cubes, relationCount);
    }

    private Map<String, List<String>> buildDomainMembersByDomain(List<DimensionalArc> dimensionalArcs) {
        if (dimensionalArcs == null || dimensionalArcs.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> directMembers = new TreeMap<>();
        for (DimensionalArc arc : dimensionalArcs) {
            if (!DIM_ARCROLE_DOMAIN_MEMBER.equals(arc.arcrole())) {
                continue;
            }
            if (arc.source() == null || arc.source().isBlank() || arc.target() == null || arc.target().isBlank()) {
                continue;
            }
            directMembers.computeIfAbsent(arc.source(), key -> new TreeSet<>()).add(arc.target());
        }

        Map<String, List<String>> domainMembersByDomain = new TreeMap<>();
        for (String domain : directMembers.keySet()) {
            Set<String> visited = new TreeSet<>();
            List<String> stack = new ArrayList<>(directMembers.getOrDefault(domain, Set.of()));
            while (!stack.isEmpty()) {
                String member = stack.remove(stack.size() - 1);
                if (!visited.add(member)) {
                    continue;
                }
                Set<String> nestedMembers = directMembers.get(member);
                if (nestedMembers != null && !nestedMembers.isEmpty()) {
                    stack.addAll(nestedMembers);
                }
            }
            domainMembersByDomain.put(domain, new ArrayList<>(visited));
        }
        return domainMembersByDomain;
    }

    private void collectExternalSchemaReferences(Path file,
                                                String xsdText,
                                                Map<String, ExternalSchemaReference> externalSchemaReferencesByNamespace) {
        if (xsdText == null || xsdText.isBlank()) {
            return;
        }

        Matcher xmlnsMatcher = Pattern.compile("xmlns(?::([A-Za-z0-9_-]+))?=\"([^\"]+)\"").matcher(xsdText);
        while (xmlnsMatcher.find()) {
            String namespace = xmlnsMatcher.group(2).trim();
            if (namespace == null || namespace.isBlank()) {
                continue;
            }
            if (namespace.startsWith("http://www.w3.org/2001/XMLSchema")
                || namespace.startsWith("https://xbrl.efrag.org/taxonomy/esrs/")
                || namespace.startsWith("http://www.efrag.org/esrs")) {
                continue;
            }
            String defaultHint = inferSchemaLocationHint(namespace);
            externalSchemaReferencesByNamespace.computeIfAbsent(namespace, key -> new ExternalSchemaReference(
                namespace,
                defaultHint,
                classifyExternalSchema(namespace),
                file.getFileName().toString()
            ));
        }

        Matcher schemaLocationMatcher = Pattern.compile("(?:<xsd:import|<xs:import|<xsd:include|<xs:include)[^>]*schemaLocation=\"([^\"]+)\"[^>]*>").matcher(xsdText);
        while (schemaLocationMatcher.find()) {
            String schemaLocation = schemaLocationMatcher.group(1).trim();
            if (schemaLocation == null || schemaLocation.isBlank()) {
                continue;
            }
            String namespace = namespaceForSchemaLocation(schemaLocation);
            if (namespace == null || namespace.isBlank()) {
                continue;
            }
            externalSchemaReferencesByNamespace.computeIfAbsent(namespace, key -> new ExternalSchemaReference(
                namespace,
                schemaLocation,
                classifyExternalSchema(namespace),
                file.getFileName().toString()
            ));
        }
    }

    private List<ExternalSchemaType> mergeExternalSchemaTypes(List<ExternalSchemaType> externalTypes, Path taxonomyBase) {
        Map<String, ExternalSchemaType> uniqueTypes = new TreeMap<>();
        if (externalTypes != null) {
            for (ExternalSchemaType type : externalTypes) {
                uniqueTypes.putIfAbsent(type.namespace() + "|" + type.name(), type);
            }
        }

        if (taxonomyBase != null && Files.exists(taxonomyBase)) {
            try (Stream<Path> stream = Files.walk(taxonomyBase)) {
                for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xsd")).toList()) {
                    try {
                        String xsdText = Files.readString(file, StandardCharsets.UTF_8);
                        Document document = parseXmlText(xsdText, file.toString());
                        String targetNamespace = document.getDocumentElement().getAttribute("targetNamespace");
                        if (targetNamespace == null || targetNamespace.isBlank()) {
                            targetNamespace = file.toUri().toString();
                        }
                        List<ExternalSchemaType> localTypes = new ArrayList<>();
                        collectExternalSchemaTypes(document, targetNamespace, file.toString(), localTypes, new ArrayList<>());
                        for (ExternalSchemaType type : localTypes) {
                            uniqueTypes.putIfAbsent(type.namespace() + "|" + type.name(), type);
                        }
                    } catch (IOException ignored) {
                        // ignore malformed local schema files for the overview
                    }
                }
            } catch (IOException ignored) {
                // ignore walk failures for the overview
            }
        }

        List<ExternalSchemaType> merged = new ArrayList<>(uniqueTypes.values());
        merged.sort(Comparator.comparing(ExternalSchemaType::namespace).thenComparing(ExternalSchemaType::name));
        return merged;
    }

    private List<ExternalSchemaSubstitution> mergeExternalSchemaSubstitutions(List<ExternalSchemaSubstitution> externalSubstitutions,
                                                                                Path taxonomyBase) {
        Map<String, ExternalSchemaSubstitution> unique = new TreeMap<>();
        if (externalSubstitutions != null) {
            for (ExternalSchemaSubstitution substitution : externalSubstitutions) {
                unique.putIfAbsent(substitution.namespace() + "|" + substitution.element() + "|" + substitution.substitutionGroup(), substitution);
            }
        }
        if (taxonomyBase != null && Files.exists(taxonomyBase)) {
            try (Stream<Path> stream = Files.walk(taxonomyBase)) {
                for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xsd")).toList()) {
                    try {
                        Document document = parseXmlText(Files.readString(file, StandardCharsets.UTF_8), file.toString());
                        String namespace = document.getDocumentElement().getAttribute("targetNamespace");
                        List<ExternalSchemaSubstitution> local = new ArrayList<>();
                        collectExternalSchemaSubstitutions(document.getElementsByTagNameNS(XS_NS, "element"), namespace, file.toString(), local);
                        for (ExternalSchemaSubstitution substitution : local) {
                            unique.putIfAbsent(substitution.namespace() + "|" + substitution.element() + "|" + substitution.substitutionGroup(), substitution);
                        }
                    } catch (IOException ignored) {
                        // ignore malformed local schema files for the overview
                    }
                }
            } catch (IOException ignored) {
                // ignore walk failures for the overview
            }
        }
        return new ArrayList<>(unique.values());
    }

    private ExternalSchemaAnalysis analyzeExternalSchemas(List<ExternalSchemaReference> references) {
        List<ExternalSchemaType> types = new ArrayList<>();
        List<ExternalSchemaEdge> edges = new ArrayList<>();
        List<ExternalSchemaSubstitution> substitutions = new ArrayList<>();
        Map<String, String> responseCache = new HashMap<>();
        for (ExternalSchemaReference reference : references) {
            String location = reference.schemaLocation();
            if (location == null || !location.startsWith("http")) {
                continue;
            }
            String xsdText = responseCache.get(location);
            String status = "loaded";
            if (xsdText == null) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(location))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/xml, text/xml, */*")
                        .GET()
                        .build();
                    HttpResponse<String> response = EXTERNAL_SCHEMA_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        xsdText = response.body();
                        responseCache.put(location, xsdText);
                    } else {
                        status = "HTTP " + response.statusCode();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    status = "interrupted";
                } catch (IOException | IllegalArgumentException e) {
                    status = "unavailable";
                }
            }
            if (xsdText == null || xsdText.isBlank()) {
                types.add(new ExternalSchemaType(reference.namespace(), "(Schema nicht geladen)", "unbekannt", "-", location, "-", status));
                continue;
            }
            try {
                Document document = parseXmlText(xsdText, location);
                Element root = document.getDocumentElement();
                String targetNamespace = root.getAttribute("targetNamespace");
                if (targetNamespace == null || targetNamespace.isBlank()) targetNamespace = reference.namespace();
                collectExternalSchemaEdges(document, targetNamespace, location, edges);
                collectExternalSchemaTypes(document, targetNamespace, location, types, substitutions);
            } catch (IOException e) {
                types.add(new ExternalSchemaType(reference.namespace(), "(XSD nicht parsebar)", "unbekannt", "-", location, "-", "parse error"));
            }
        }
        Map<String, ExternalSchemaType> uniqueTypes = new TreeMap<>();
        for (ExternalSchemaType type : types) {
            uniqueTypes.putIfAbsent(type.namespace() + "|" + type.name(), type);
        }
        Map<String, ExternalSchemaEdge> uniqueEdges = new TreeMap<>();
        for (ExternalSchemaEdge edge : edges) {
            uniqueEdges.putIfAbsent(edge.source() + "|" + edge.target() + "|" + edge.relation(), edge);
        }
        List<ExternalSchemaType> deduplicatedTypes = new ArrayList<>(uniqueTypes.values());
        List<ExternalSchemaEdge> deduplicatedEdges = new ArrayList<>(uniqueEdges.values());
        Map<String, ExternalSchemaSubstitution> uniqueSubstitutions = new TreeMap<>();
        for (ExternalSchemaSubstitution substitution : substitutions) {
            uniqueSubstitutions.putIfAbsent(substitution.namespace() + "|" + substitution.element() + "|" + substitution.substitutionGroup(), substitution);
        }
        List<ExternalSchemaSubstitution> deduplicatedSubstitutions = new ArrayList<>(uniqueSubstitutions.values());
        deduplicatedTypes.sort(Comparator.comparing(ExternalSchemaType::namespace).thenComparing(ExternalSchemaType::name));
        deduplicatedEdges.sort(Comparator.comparing(ExternalSchemaEdge::source).thenComparing(ExternalSchemaEdge::target));
        deduplicatedSubstitutions.sort(Comparator.comparing(ExternalSchemaSubstitution::substitutionGroup)
            .thenComparing(ExternalSchemaSubstitution::namespace).thenComparing(ExternalSchemaSubstitution::element));
        return new ExternalSchemaAnalysis(deduplicatedTypes, deduplicatedEdges, deduplicatedSubstitutions);
    }

    private void collectExternalSchemaTypes(Document document,
                                             String namespace,
                                             String source,
                                             List<ExternalSchemaType> types,
                                             List<ExternalSchemaSubstitution> substitutions) {
        collectExternalSchemaTypeNodes(document.getElementsByTagNameNS(XS_NS, "simpleType"), namespace, source, types, false);
        collectExternalSchemaTypeNodes(document.getElementsByTagNameNS(XS_NS, "complexType"), namespace, source, types, true);
        collectExternalSchemaElementNodes(document.getElementsByTagNameNS(XS_NS, "element"), namespace, source, types);
        collectExternalSchemaSubstitutions(document.getElementsByTagNameNS(XS_NS, "element"), namespace, source, substitutions);
    }

    private void collectExternalSchemaTypes(Document document,
                                             String namespace,
                                             String source,
                                             List<ExternalSchemaType> types) {
        collectExternalSchemaTypes(document, namespace, source, types, new ArrayList<>());
    }

    private void collectExternalSchemaTypeNodes(NodeList nodes,
                                                String namespace,
                                                String source,
                                                List<ExternalSchemaType> types,
                                                boolean complex) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element typeElement = (Element) nodes.item(i);
            String name = typeElement.getAttribute("name");
            if (name == null || name.isBlank()) continue;
            NodeList restrictions = typeElement.getElementsByTagNameNS(XS_NS, "restriction");
            NodeList extensions = typeElement.getElementsByTagNameNS(XS_NS, "extension");
            String base = restrictions.getLength() > 0 ? ((Element) restrictions.item(0)).getAttribute("base")
                : extensions.getLength() > 0 ? ((Element) extensions.item(0)).getAttribute("base") : "-";
            NodeList enumerations = typeElement.getElementsByTagNameNS(XS_NS, "enumeration");
            String facets = enumerations.getLength() > 0 ? "enumeration(" + enumerations.getLength() + ")" : collectFacetSummary(typeElement);
            String category = classifyExternalType(name, base, complex, enumerations.getLength() > 0);
            types.add(new ExternalSchemaType(namespace, name, category, base, source, facets, "loaded"));
        }
    }

    private void collectExternalSchemaElementNodes(NodeList nodes,
                                                  String namespace,
                                                  String source,
                                                  List<ExternalSchemaType> types) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = element.getAttribute("name");
            if (name == null || name.isBlank()) continue;
            String type = element.getAttribute("type");
            String substitutionGroup = element.getAttribute("substitutionGroup");
            String base = (!type.isBlank()) ? type : (!substitutionGroup.isBlank()) ? substitutionGroup : "-";
            String category = classifyExternalType(name, base, false, false);
            if (!substitutionGroup.isBlank()) {
                category = "element/substitution";
            } else if (!type.isBlank()) {
                category = "element";
            }
            String facets = element.hasAttribute("abstract") && Boolean.parseBoolean(element.getAttribute("abstract")) ? "abstract" : "-";
            types.add(new ExternalSchemaType(namespace, name, category, base, source, facets, "loaded"));
        }
    }

    private void collectExternalSchemaSubstitutions(NodeList nodes,
                                                     String namespace,
                                                     String source,
                                                     List<ExternalSchemaSubstitution> substitutions) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String substitutionGroup = element.getAttribute("substitutionGroup");
            if (substitutionGroup == null || substitutionGroup.isBlank()) continue;
            substitutions.add(new ExternalSchemaSubstitution(
                namespace,
                element.getAttribute("name"),
                element.getAttribute("type"),
                substitutionGroup,
                Boolean.parseBoolean(element.getAttribute("abstract")),
                source
            ));
        }
    }

    private String collectFacetSummary(Element typeElement) {
        List<String> facets = new ArrayList<>();
        for (String facet : List.of("length", "minLength", "maxLength", "pattern", "minInclusive", "maxInclusive", "fractionDigits")) {
            if (typeElement.getElementsByTagNameNS(XS_NS, facet).getLength() > 0) facets.add(facet);
        }
        return facets.isEmpty() ? "-" : String.join(", ", facets);
    }

    private String classifyExternalType(String name, String base, boolean complex, boolean enumeration) {
        String value = (name + " " + base).toLowerCase(Locale.ROOT);
        if (enumeration) return "enumeration";
        if (value.matches(".*(decimal|integer|int|long|short|byte|double|float|number|amount|percent).*")) return "numerisch";
        if (value.matches(".*(date|time|year|duration).*")) return "datum/zeit";
        if (value.matches(".*(boolean|bool).*")) return "boolean";
        if (value.matches(".*(qname|uri|href|reference).*")) return "referenz/qname";
        if (complex) return "komplex";
        if (value.matches(".*(string|text|token|normalized).*")) return "string/text";
        return "unbekannt";
    }

    private void collectExternalSchemaEdges(Document document,
                                             String sourceNamespace,
                                             String source,
                                             List<ExternalSchemaEdge> edges) {
        for (String localName : List.of("import", "include")) {
            NodeList nodes = document.getElementsByTagNameNS(XS_NS, localName);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String target = element.getAttribute("namespace");
                if (target == null || target.isBlank()) target = element.getAttribute("schemaLocation");
                if (target != null && !target.isBlank()) edges.add(new ExternalSchemaEdge(sourceNamespace, target, localName, source));
            }
        }
    }

    private Document parseXmlText(String xml, String source) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse external schema: " + source, e);
        }
    }

    private String inferSchemaLocationHint(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "-";
        }
        if (namespace.contains("/dtr/type/")) {
            return "https://www.xbrl.org/dtr/type/2022-03-31/types.xsd";
        }
        if (namespace.contains("/2003/linkbase")) {
            return "https://www.xbrl.org/2003/xbrl-linkbase-2003-12-31.xsd";
        }
        if (namespace.contains("/1999/xlink")) {
            return "https://www.w3.org/1999/xlink.xsd";
        }
        if (namespace.contains("/2005/xbrldt")) {
            return "http://www.xbrl.org/2005/xbrldt-2005.xsd";
        }
        return "-";
    }

    private String classifyExternalSchema(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "external";
        }
        if (namespace.contains("/dtr/type/")) {
            return "DTR type namespace";
        }
        if (namespace.contains("/2003/linkbase")) {
            return "XBRL linkbase namespace";
        }
        if (namespace.contains("/1999/xlink")) {
            return "XLink namespace";
        }
        if (namespace.contains("/2005/xbrldt")) {
            return "XBRL dimensions namespace";
        }
        return "external schema reference";
    }

    private String namespaceForSchemaLocation(String schemaLocation) {
        if (schemaLocation == null || schemaLocation.isBlank()) {
            return null;
        }
        if (schemaLocation.contains("dtr/type/2022-03-31")) {
            return "http://www.xbrl.org/dtr/type/2022-03-31";
        }
        if (schemaLocation.contains("xbrl-linkbase-2003") || schemaLocation.contains("/2003/linkbase")) {
            return "http://www.xbrl.org/2003/linkbase";
        }
        if (schemaLocation.contains("1999/xlink")) {
            return "http://www.w3.org/1999/xlink";
        }
        if (schemaLocation.contains("xbrldt")) {
            return "http://xbrl.org/2005/xbrldt";
        }
        return null;
    }

    private void collectEnumerationConceptsFromText(String xsdText,
                                                    Map<String, TaxonomyEnumeration> taxonomyEnumerationsByConcept) {
        if (xsdText == null || xsdText.isBlank()) {
            return;
        }

        Matcher elementMatcher = XSD_ELEMENT_TAG_PATTERN.matcher(xsdText);
        while (elementMatcher.find()) {
            String elementTag = elementMatcher.group();
            String localName = extractAttribute(elementTag, XSD_NAME_ATTR_PATTERN);
            String type = extractAttribute(elementTag, XSD_TYPE_ATTR_PATTERN);
            if (localName == null || type == null) {
                continue;
            }

            boolean isSingle = "enum2:enumerationItemType".equals(type);
            boolean isSet = "enum2:enumerationSetItemType".equals(type);
            if (!isSingle && !isSet) {
                continue;
            }

            String conceptKey = normalizeConceptKey("esrs:" + localName);
            String domain = extractAttribute(elementTag, XSD_ENUM_DOMAIN_ATTR_PATTERN);
            String linkrole = extractAttribute(elementTag, XSD_ENUM_LINKROLE_ATTR_PATTERN);
            taxonomyEnumerationsByConcept.put(conceptKey, new TaxonomyEnumeration(isSet, domain, linkrole));
        }
    }

    private String extractAttribute(String elementTag, Pattern pattern) {
        Matcher matcher = pattern.matcher(elementTag);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int countOccurrences(String text, String token) {
        if (text == null || text.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private void collectHrefTargetsFromText(String text, Set<String> hrefTargets) {
        if (text == null || text.isBlank()) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?:xlink:href|schemaLocation)\\s*=\\s*\"([^\"]+)\"")
            .matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isBlank()) {
                hrefTargets.add(value.trim());
            }
        }
    }

    private void collectHrefTargets(Document document, Set<String> hrefTargets) {
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element element = (Element) allNodes.item(i);
            String href = element.getAttributeNS(XLINK_NS, "href");
            if (href != null && !href.isBlank()) {
                hrefTargets.add(href);
            }
            String schemaLocation = element.getAttribute("schemaLocation");
            if (schemaLocation != null && !schemaLocation.isBlank()) {
                hrefTargets.add(schemaLocation);
            }
        }
    }

    private String detectLayer(String fileName) {
        if (fileName.startsWith("pre_")) {
            return "presentation";
        }
        if (fileName.startsWith("cal_")) {
            return "calculation";
        }
        if (fileName.startsWith("def_")) {
            return "definition";
        }
        if (fileName.startsWith("lab_")) {
            return "label";
        }
        if (fileName.startsWith("ref_")) {
            return "reference";
        }
        if (fileName.startsWith("dim_")) {
            return "dimension";
        }
        return "other";
    }

    private String renderOverviewHtml(PresentationForest forest,
                                      TaxonomyMetadata metadata,
                                      Map<String, List<MappingEntry>> mappingsByConcept,
                                      LayoutSnapshot layoutSnapshot,
                                      Path treeHtml,
                                      Path graphHtml,
                                      Path layerHtml,
                                      Path matrixHtml,
                                      Path flowHtml,
                                      Path hypercubeHtml,
                                      Path hypercube3dHtml,
                                      Path coverageHtml,
                                      Path enumerationHtml,
                                      Path referenceHtml,
                                      Path calculationHtml,
                                      Path intersectionHtml,
                                      Path validationHtml,
                                      Path allocationHtml,
                                      Path statsHtml,
                                      Path complexityHtml,
                                      Path impactHeatmapHtml,
                                      Path hypercubeDimensionInventoryHtml,
                                      Path mappingFlowHtml,
                                      Path conceptBacklogHtml,
                                      Path scopePeriodHtml,
                                      Path ruleCoverageMatrixHtml,
                                      Path intersectionRiskHtml,
                                      Path traceabilityMatrixHtml,
                                      Path dimensionCooccurrenceHtml,
                                      Path defaultMemberQualityHtml,
                                      Path enumDomainValidityHtml,
                                      Path externalSchemasHtml,
                                      Path dashboardHtml) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>ESRS Taxonomie-Visualisierungen</h1>")
            .append("<p class=\"lead\">Die Visualisierung wurde in getrennte Ansichten aufgeteilt, damit jede Seite kleiner, schneller und gezielter nutzbar ist.</p>")
            .append("<div class=\"summary\">")
            .append("<a href=\"")
            .append(escapeHtml(fileNameOnly(dashboardHtml)))
            .append("\" class=\"theme-chip\"><span class=\"dot\" style=\"background:#1e5f99\"></span>Master Dashboard oeffnen</a>")
            .append(summaryCard("Präsentationsrollen", forest.roleCount()))
            .append(summaryCard("Knoten", forest.nodeCount()))
            .append(summaryCard("Konzepte", mappingsByConcept.size()))
            .append(summaryCard("Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append(summaryCard("XSD Elemente", metadata.xsdElementCount()))
            .append(summaryCard("Externe Schemas", metadata.externalSchemaReferences().size()))
            .append(summaryCard("Verlinkungen (href)", metadata.hrefTargets().size()))
            .append("</div>")
            .append("<section><h2>Ansichten</h2><div class=\"flow-grid\">")
            .append(viewCard("1. Tree", fileNameOnly(treeHtml), "Hierarchie + Drilldown + Mapping-Meta"))
            .append(viewCard("2. Graph", fileNameOnly(graphHtml), "Interaktiver Dependency-Graph mit Layer-Toggles"))
            .append(viewCard("3. Layer", fileNameOnly(layerHtml), "Layer-Übersicht mit aufklappbaren Unterelementen"))
            .append(viewCard("4. Matrix", fileNameOnly(matrixHtml), "Konzeptindex und Layout-Zuordnung"))
            .append(viewCard("5. Flow", fileNameOnly(flowHtml), "Reporting-Flow von Input bis Disclosure"))
            .append(viewCard("6. Hypercube", fileNameOnly(hypercubeHtml), "Dimensionale Struktur mit Hypercubes, Achsen, Domains und Members"))
                        .append(viewCard("7. Hypercube 3D", fileNameOnly(hypercube3dHtml), "Raeumliche Navigation durch Cube-, Dimension- und Member-Strukturen"))
                        .append(viewCard("8. Coverage", fileNameOnly(coverageHtml), "Abdeckung: Mapping, Layout, Enumeration und Dimensionen"))
                        .append(viewCard("9. Enumeration", fileNameOnly(enumerationHtml), "Enumeration-Domaenen, Allowed Values und Taxonomie-Hinweise"))
                        .append(viewCard("10. Reference", fileNameOnly(referenceHtml), "Konzept-zu-ESRS-Referenznachweise (Traceability)"))
                        .append(viewCard("11. Calculation", fileNameOnly(calculationHtml), "Calculation- und Formula-Abhaengigkeiten (Sample + Impact)"))
                        .append(viewCard("12. Intersection", fileNameOnly(intersectionHtml), "Kombinationen von Dimensionen je Hypercube"))
                        .append(viewCard("13. Validation", fileNameOnly(validationHtml), "Rule-Abhaengigkeiten: Formula-Dateien und referenzierte Konzepte"))
                        .append(viewCard("14. Allocation", fileNameOnly(allocationHtml), "Section-zu-Placeholder-zu-Konzept Zuordnung"))
                        .append(viewCard("15. Stats", fileNameOnly(statsHtml), "Linkbase Edge Statistics und Struktur-Hinweise"))
                        .append(viewCard("16. Complexity", fileNameOnly(complexityHtml), "Komplexitaetsindikatoren je Konzept"))
                        .append(viewCard("17. Impact Heatmap", fileNameOnly(impactHeatmapHtml), "Konzept x Section Impact-Analyse mit filterbarer Heatmap-Tabelle"))
                        .append(viewCard("18. Hypercube Dimension Inventar", fileNameOnly(hypercubeDimensionInventoryHtml), "Filterbare Inventarliste fuer Cube-Achsen mit Domain/Member/Default-Kennzahlen"))
                        .append(viewCard("19. Mapping Flow", fileNameOnly(mappingFlowHtml), "Sankey-orientierte Feld->Konzept->Hypercube Flows als filterbare Tabelle"))
                        .append(viewCard("20. Concept Backlog", fileNameOnly(conceptBacklogHtml), "Priorisierte Backlog-Tabelle fuer Mapping-/Layout-/Dimensionsluecken"))
                        .append(viewCard("21. Scope & Period", fileNameOnly(scopePeriodHtml), "Analyse nach Reporting-Section, Periodentyp und Einheit"))
                        .append(viewCard("22. Rule Coverage Matrix", fileNameOnly(ruleCoverageMatrixHtml), "Matrix Formula-Datei x Konzept mit Mapping-Hinweisen"))
                        .append(viewCard("23. Intersection Risk", fileNameOnly(intersectionRiskHtml), "Risikoanalyse fuer Dimensionspaar-Kombinationen je Hypercube"))
                        .append(viewCard("24. Traceability Matrix", fileNameOnly(traceabilityMatrixHtml), "Konzept-zu-Referenz-zu-Feld Matrix mit Filterung"))
                        .append(viewCard("25. Dimension Co-Occurrence", fileNameOnly(dimensionCooccurrenceHtml), "Haeufigkeiten von Dimensionspaaren ueber alle Hypercubes"))
                        .append(viewCard("26. Default Member Quality", fileNameOnly(defaultMemberQualityHtml), "Qualitaetscheck fuer Default-Member je Dimension"))
                        .append(viewCard("27. Enum Domain Validity", fileNameOnly(enumDomainValidityHtml), "Uebersicht je Enumeration-Domain mit Nutzungs- und Value-Signalen"))
            .append(viewCard("28. External Schema References", fileNameOnly(externalSchemasHtml), "Externe XBRL-Namespaces wie Linkbase, XLink, XBRL Dimensions und DTR Types"))
            .append("</div></section>");
        return renderPage("ESRS Taxonomie-Visualisierungen", body.toString(), "");
    }

    private String renderDashboardHtml(Path treeHtml,
                                       Path graphHtml,
                                       Path layerHtml,
                                       Path matrixHtml,
                                       Path flowHtml,
                                       Path hypercubeHtml,
                                       Path hypercube3dHtml,
                                       Path coverageHtml,
                                       Path enumerationHtml,
                                       Path referenceHtml,
                                       Path calculationHtml,
                                       Path intersectionHtml,
                                       Path validationHtml,
                                       Path allocationHtml,
                                       Path statsHtml,
                                       Path complexityHtml,
                                       Path impactHeatmapHtml,
                                       Path hypercubeDimensionInventoryHtml,
                                       Path mappingFlowHtml,
                                       Path conceptBacklogHtml,
                                       Path scopePeriodHtml,
                                       Path ruleCoverageMatrixHtml,
                                       Path intersectionRiskHtml,
                                       Path traceabilityMatrixHtml,
                                       Path dimensionCooccurrenceHtml,
                                       Path defaultMemberQualityHtml,
                                       Path enumDomainValidityHtml,
                                       Path externalSchemasHtml) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>Master Dashboard: Visual Analytics Hub</h1>")
            .append("<p class=\"lead\">Ein zentraler Einstieg mit globaler Suche und Themenfiltern fuer alle Visualisierungsansichten.</p>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"dashSearch\" type=\"search\" placeholder=\"Ansicht, Thema oder Stichwort suchen...\" oninput=\"applyDashFilter()\">")
            .append("<label class=\"filter\"><input type=\"checkbox\" id=\"dashFocusStructure\" onchange=\"applyDashFilter()\"> Struktur</label>")
            .append("<label class=\"filter\"><input type=\"checkbox\" id=\"dashFocusQuality\" onchange=\"applyDashFilter()\"> Qualitaet</label>")
            .append("<label class=\"filter\"><input type=\"checkbox\" id=\"dashFocusCoverage\" onchange=\"applyDashFilter()\"> Coverage</label>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"saveDashFilters()\">Filter speichern</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"loadDashFilters()\">Filter laden</button>")
            .append("</div>")
            .append("<section><h2>Ansichten im Kontext</h2><div class=\"flow-grid\" id=\"dashCards\">")
            .append(dashboardCard("Tree", fileNameOnly(treeHtml), "struktur navigation hierarchy drilldown", "Struktur"))
            .append(dashboardCard("Graph", fileNameOnly(graphHtml), "dependency relation network", "Struktur"))
            .append(dashboardCard("Layer", fileNameOnly(layerHtml), "layer technical source", "Struktur"))
            .append(dashboardCard("Matrix", fileNameOnly(matrixHtml), "mapping placeholder index", "Coverage"))
            .append(dashboardCard("Flow", fileNameOnly(flowHtml), "process journey disclosure", "Struktur"))
            .append(dashboardCard("Hypercube", fileNameOnly(hypercubeHtml), "dimension domain member", "Struktur"))
            .append(dashboardCard("Hypercube 3D", fileNameOnly(hypercube3dHtml), "3d cube dimension", "Struktur"))
            .append(dashboardCard("Coverage", fileNameOnly(coverageHtml), "layout mapping completeness", "Coverage"))
            .append(dashboardCard("Enumeration", fileNameOnly(enumerationHtml), "enum domain allowed values", "Quality"))
            .append(dashboardCard("Reference", fileNameOnly(referenceHtml), "traceability esrs references", "Coverage"))
            .append(dashboardCard("Calculation", fileNameOnly(calculationHtml), "formula dependency impact", "Quality"))
            .append(dashboardCard("Intersection", fileNameOnly(intersectionHtml), "dimension pairs combinations", "Struktur"))
            .append(dashboardCard("Validation", fileNameOnly(validationHtml), "rules formulas checks", "Quality"))
            .append(dashboardCard("Allocation", fileNameOnly(allocationHtml), "section placeholder concept", "Coverage"))
            .append(dashboardCard("Stats", fileNameOnly(statsHtml), "edges nodes degrees", "Struktur"))
            .append(dashboardCard("Complexity", fileNameOnly(complexityHtml), "risk score concept", "Quality"))
            .append(dashboardCard("Impact Heatmap", fileNameOnly(impactHeatmapHtml), "section impact score", "Coverage"))
            .append(dashboardCard("Hypercube Dimension Inventar", fileNameOnly(hypercubeDimensionInventoryHtml), "dimension inventory defaults members", "Quality"))
            .append(dashboardCard("Mapping Flow", fileNameOnly(mappingFlowHtml), "field concept hypercube flow", "Coverage"))
            .append(dashboardCard("Concept Backlog", fileNameOnly(conceptBacklogHtml), "priority backlog risk", "Quality"))
            .append(dashboardCard("Scope & Period", fileNameOnly(scopePeriodHtml), "period unit section", "Coverage"))
            .append(dashboardCard("Rule Coverage Matrix", fileNameOnly(ruleCoverageMatrixHtml), "formula concept matrix", "Coverage"))
            .append(dashboardCard("Intersection Risk", fileNameOnly(intersectionRiskHtml), "dimension risk combinations", "Quality"))
            .append(dashboardCard("Traceability Matrix", fileNameOnly(traceabilityMatrixHtml), "reference field placeholder", "Coverage"))
            .append(dashboardCard("Dimension Co-Occurrence", fileNameOnly(dimensionCooccurrenceHtml), "dimension frequency pairs", "Struktur"))
            .append(dashboardCard("Default Member Quality", fileNameOnly(defaultMemberQualityHtml), "defaults consistency", "Quality"))
            .append(dashboardCard("Enum Domain Validity", fileNameOnly(enumDomainValidityHtml), "enum domain validity", "Quality"))
            .append(dashboardCard("External Schema References", fileNameOnly(externalSchemasHtml), "external schemas dtr xlink linkbase xbrldt", "Quality"))
            .append("</div></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyDashFilter(){const q=normalize(document.getElementById('dashSearch').value.trim());const fStruct=document.getElementById('dashFocusStructure').checked;const fQuality=document.getElementById('dashFocusQuality').checked;const fCoverage=document.getElementById('dashFocusCoverage').checked;document.querySelectorAll('.dash-card').forEach(c=>{const s=(c.dataset.search||'');const g=(c.dataset.group||'');const textOk=!q||s.includes(q);const groupWanted=(!fStruct&&!fQuality&&!fCoverage)||(fStruct&&g==='struktur')||(fQuality&&g==='quality')||(fCoverage&&g==='coverage');c.hidden=!(textOk&&groupWanted);});}"
            + "function saveDashFilters(){localStorage.setItem('dashFilters',JSON.stringify({q:document.getElementById('dashSearch').value,structure:document.getElementById('dashFocusStructure').checked,quality:document.getElementById('dashFocusQuality').checked,coverage:document.getElementById('dashFocusCoverage').checked}));}"
            + "function loadDashFilters(){try{const v=JSON.parse(localStorage.getItem('dashFilters')||'{}');document.getElementById('dashSearch').value=v.q||'';document.getElementById('dashFocusStructure').checked=!!v.structure;document.getElementById('dashFocusQuality').checked=!!v.quality;document.getElementById('dashFocusCoverage').checked=!!v.coverage;}catch(e){}applyDashFilter();}"
            + "</script>";
        return renderPage("Master Dashboard", body.toString(), script);
    }

        private String renderHypercube3dHtml(TaxonomyMetadata metadata) {
                HypercubeMetadata hypercubeMetadata = metadata.hypercubeMetadata();
                List<HypercubeCube> cubes = hypercubeMetadata.cubes();

                StringBuilder cubesJson = new StringBuilder("[");
                for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
                        HypercubeCube cube = cubes.get(cubeIndex);
                        if (cubeIndex > 0) {
                                cubesJson.append(',');
                        }

                        int primaryBindings = cube.primaryItemsAll().size() + cube.primaryItemsNotAll().size();
                        int totalDefaults = 0;
                        int totalDomains = 0;
                        int totalMembers = 0;

                        cubesJson.append("{id:\"")
                                .append(escapeJs(cube.cube()))
                                .append("\",primaryBindings:")
                                .append(primaryBindings)
                                .append(",dimensions:[");

                        List<String> dimensions = cube.dimensions();
                        for (int dimensionIndex = 0; dimensionIndex < dimensions.size(); dimensionIndex++) {
                                String dimension = dimensions.get(dimensionIndex);
                                if (dimensionIndex > 0) {
                                        cubesJson.append(',');
                                }

                                List<String> domains = cube.domainsPerDimension().getOrDefault(dimension, List.of());
                                List<String> defaults = cube.defaultsPerDimension().getOrDefault(dimension, List.of());
                                int memberCount = 0;
                                for (String domain : domains) {
                                        memberCount += cube.membersPerDomain().getOrDefault(domain, List.of()).size();
                                }

                                totalDefaults += defaults.size();
                                totalDomains += domains.size();
                                totalMembers += memberCount;

                                cubesJson.append("{id:\"")
                                        .append(escapeJs(dimension))
                                        .append("\",domains:")
                                        .append(domains.size())
                                        .append(",members:")
                                        .append(memberCount)
                                        .append(",defaults:")
                                        .append(defaults.size())
                                        .append("}");
                        }

                        cubesJson.append("],totalDomains:")
                                .append(totalDomains)
                                .append(",totalMembers:")
                                .append(totalMembers)
                                .append(",totalDefaults:")
                                .append(totalDefaults)
                                .append("}");
                }
                cubesJson.append(']');

                StringBuilder body = new StringBuilder();
                body.append("<h1>Hypercube 3D View: Raumstruktur der Dimensionen</h1>")
                        .append("<p class=\"lead\">Interaktive 3D-Ansicht mit Zoom, Pan und Orbit. Die Stage nutzt die volle verfuegbare Breite und eine 1440px-optimierte Hoehe fuer 2560x1440-Monitore.</p>")
                        .append("<div class=\"summary\">")
                        .append(summaryCard("Hypercubes", hypercubeMetadata.cubes().size()))
                        .append(summaryCard("Dimensionen", hypercubeMetadata.cubes().stream().mapToInt(c -> c.dimensions().size()).sum()))
                        .append(summaryCard("Dimensionale Relationen", hypercubeMetadata.relationCount()))
                        .append("</div>")
                        .append("<div class=\"toolbar\">")
                        .append("<button type=\"button\" onclick=\"resetCamera()\">Kamera reset</button>")
                        .append("<button type=\"button\" class=\"secondary\" onclick=\"fitToSelection()\">Auswahl fokussieren</button>")
                        .append("<button type=\"button\" class=\"secondary\" onclick=\"toggleFullscreen()\">Fullscreen</button>")
                        .append("<input id=\"cubeSearch\" type=\"search\" placeholder=\"Hypercube oder Dimension suchen...\" oninput=\"applyCubeFilter()\">")
                        .append("</div>")
                        .append("<section><h2>3D Stage</h2><div id=\"hypercube3dStage\" class=\"hypercube-3d-stage\"><canvas id=\"hypercube3dCanvas\"></canvas><div id=\"hypercube3dTooltip\" class=\"hypercube-3d-tooltip\" hidden></div><aside id=\"hypercube3dOverlay\" class=\"hypercube-3d-overlay\"><div class=\"overlay-title\">Auswahl</div><div class=\"overlay-body\">Objekt anklicken, um Details zu sehen.</div></aside></div>")
                        .append("<div id=\"hypercube3dInfo\" class=\"node-info\">Objekt anklicken fuer Details. Maus: Linke Taste orbit, Shift+Linksklick oder rechte Taste pan, Scroll zoom. Tastatur: W/A/S/D fuer Vor/Zurueck/Seitwaerts, Q/E fuer Hoch/Runter.</div></section>")
                        .append("<section><h2>Top Hypercubes nach Member-Anzahl</h2><table class=\"layout-table\"><thead><tr><th>Hypercube</th><th>Dimensionen</th><th>Domains</th><th>Members</th><th>Defaults</th><th>Primary-Bindings</th></tr></thead><tbody id=\"hypercube3dTable\"></tbody></table></section>");

                String scriptTemplate = """
                        <style>
                        main{max-width:none;padding:16px 16px 28px;}
                        .hypercube-3d-stage{position:relative;width:100%;height:min(1440px,calc(100vh - 140px));min-height:740px;background:radial-gradient(circle at 20% 10%,#13263f 0%,#0c1a2c 52%,#050a12 100%);border:1px solid #1b3b5f;border-radius:16px;overflow:hidden;}
                        #hypercube3dCanvas{width:100%;height:100%;display:block;}
                        .hypercube-3d-tooltip{position:absolute;pointer-events:none;background:rgba(8,18,30,.94);color:#e8f2ff;border:1px solid #2f5f8f;border-radius:10px;padding:8px 10px;max-width:360px;font-size:.9rem;line-height:1.35;box-shadow:0 8px 20px rgba(0,0,0,.35);z-index:20;}
                        .hypercube-3d-tooltip code{display:inline-block;max-width:100%;white-space:normal;overflow-wrap:anywhere;word-break:break-word;background:rgba(40,86,130,.45);color:#d9ecff;padding:2px 6px;border-radius:6px;}
                        .hypercube-3d-overlay{display:none;position:absolute;top:16px;right:16px;width:min(460px,42vw);max-height:calc(100% - 32px);overflow:auto;background:rgba(7,15,24,.82);backdrop-filter:blur(6px);border:1px solid #3f5c7c;border-radius:12px;color:#e9f2ff;padding:10px 12px;z-index:22;box-shadow:0 8px 24px rgba(0,0,0,.35);}
                        .hypercube-3d-overlay .overlay-title{font-size:.74rem;letter-spacing:.08em;text-transform:uppercase;color:#8fc3ff;margin-bottom:6px;font-weight:700;}
                        .hypercube-3d-overlay .overlay-body{font-size:.9rem;line-height:1.45;}
                        .hypercube-3d-overlay code{display:inline-block;max-width:100%;white-space:normal;overflow-wrap:anywhere;word-break:break-word;background:rgba(23,55,88,.72);color:#d8ecff;}
                        .hypercube-3d-stage:fullscreen .hypercube-3d-overlay,.hypercube-3d-stage.is-fullscreen .hypercube-3d-overlay{display:block;}
                        @media (max-width: 1400px){
                            .hypercube-3d-stage{height:min(1080px,calc(100vh - 130px));min-height:620px;}
                        }
                        </style>
                        <script src="three.min.js"></script>
                        <script>
                        const hypercubeData=__HYPERCUBE_DATA__;
                        const stage=document.getElementById('hypercube3dStage');
                        const canvas=document.getElementById('hypercube3dCanvas');
                        const tooltip=document.getElementById('hypercube3dTooltip');
                        const info=document.getElementById('hypercube3dInfo');
                        const overlay=document.getElementById('hypercube3dOverlay');
                        const tableBody=document.getElementById('hypercube3dTable');

                        let scene,camera,renderer,controls,raycaster;
                        let pointer;
                        const pickables=[];
                        const cubeGroups=[];
                        let hovered=null;
                        let selected=null;

                        function qualityRatio(){
                            const ratio=window.devicePixelRatio||1;
                            return Math.min(3,Math.max(1.25,ratio));
                        }

                        function initScene(){
                            if(typeof THREE==='undefined'){
                                info.textContent='Three.js konnte nicht geladen werden. Bitte lokales Bundle pruefen (output/three.min.js).';
                                return;
                            }

                            pointer=new THREE.Vector2();

                            scene=new THREE.Scene();
                            scene.background=new THREE.Color(0x070f18);

                            const width=stage.clientWidth;
                            const height=stage.clientHeight;
                            camera=new THREE.PerspectiveCamera(52,width/Math.max(1,height),0.1,4200);
                            camera.position.set(210,160,260);

                            renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false,powerPreference:'high-performance'});
                            renderer.setPixelRatio(qualityRatio());
                            renderer.setSize(width,height,false);
                            renderer.outputColorSpace=THREE.SRGBColorSpace;

                            controls=createSimpleOrbitController(camera,renderer.domElement);
                            controls.target.set(0,0,0);
                            controls.minDistance=40;
                            controls.maxDistance=1400;

                            raycaster=new THREE.Raycaster();

                            addLights();
                            addReferenceGrid();
                            buildCubeScene(hypercubeData);
                            fillCubeTable(hypercubeData);

                            renderer.domElement.addEventListener('mousemove',onPointerMove);
                            renderer.domElement.addEventListener('mouseleave',onPointerLeave);
                            renderer.domElement.addEventListener('click',onPointerClick);
                            window.addEventListener('resize',resizeScene);
                            document.addEventListener('fullscreenchange',syncFullscreenUi);
                            syncFullscreenUi();

                            animate();
                        }

                        function addLights(){
                            const hemi=new THREE.HemisphereLight(0xd7e8ff,0x243a54,1.05);
                            scene.add(hemi);

                            const ambient=new THREE.AmbientLight(0x8fb2d6,0.42);
                            scene.add(ambient);

                            const dirA=new THREE.DirectionalLight(0xffffff,0.9);
                            dirA.position.set(180,220,140);
                            scene.add(dirA);

                            const dirB=new THREE.DirectionalLight(0x89b7ff,0.72);
                            dirB.position.set(-160,120,-180);
                            scene.add(dirB);

                            const dirC=new THREE.DirectionalLight(0xa7d6ff,0.48);
                            dirC.position.set(0,80,260);
                            scene.add(dirC);
                        }

                        function addReferenceGrid(){
                            const grid=new THREE.GridHelper(1200,28,0x3d648d,0x284763);
                            grid.position.y=-38;
                            scene.add(grid);
                        }

                        function createSimpleOrbitController(camera,domElement){
                            const state={
                                target:new THREE.Vector3(0,0,0),
                                distance:370,
                                azimuth:0.78,
                                polar:1.1,
                                minDistance:40,
                                maxDistance:1400,
                                rotating:false,
                                panning:false,
                                moving:false,
                                lastX:0,
                                lastY:0,
                                keyState:Object.create(null)
                            };

                            const tmpDir=new THREE.Vector3();
                            const tmpRight=new THREE.Vector3();
                            const tmpUp=new THREE.Vector3();

                            function clampPolar(){
                                state.polar=Math.max(0.1,Math.min(Math.PI-0.1,state.polar));
                            }

                            function clampDistance(){
                                state.distance=Math.max(state.minDistance,Math.min(state.maxDistance,state.distance));
                            }

                            function apply(){
                                clampPolar();
                                clampDistance();
                                const sin=Math.sin(state.polar);
                                const x=state.target.x+state.distance*sin*Math.cos(state.azimuth);
                                const y=state.target.y+state.distance*Math.cos(state.polar);
                                const z=state.target.z+state.distance*sin*Math.sin(state.azimuth);
                                camera.position.set(x,y,z);
                                camera.lookAt(state.target);
                            }

                            function onPointerDown(event){
                                state.lastX=event.clientX;
                                state.lastY=event.clientY;
                                state.rotating=event.button===0 && !event.shiftKey;
                                state.panning=event.button===2 || event.button===1 || (event.button===0 && event.shiftKey);
                                state.moving=state.rotating||state.panning;
                                if(state.rotating||state.panning){
                                    domElement.setPointerCapture?.(event.pointerId);
                                }
                            }

                            function onPointerMove(event){
                                if(!state.rotating && !state.panning){
                                    return;
                                }
                                const dx=event.clientX-state.lastX;
                                const dy=event.clientY-state.lastY;
                                state.lastX=event.clientX;
                                state.lastY=event.clientY;

                                if(state.rotating){
                                    state.azimuth-=dx*0.007;
                                    state.polar-=dy*0.006;
                                } else if(state.panning){
                                    const panScale=Math.max(0.08,state.distance*0.0017);
                                    camera.getWorldDirection(tmpDir);
                                    tmpRight.crossVectors(tmpDir,camera.up).normalize();
                                    tmpUp.copy(camera.up).normalize();
                                    state.target.addScaledVector(tmpRight,-dx*panScale);
                                    state.target.addScaledVector(tmpUp,dy*panScale);
                                }
                                apply();
                            }

                            function onPointerUp(event){
                                state.rotating=false;
                                state.panning=false;
                                state.moving=false;
                                domElement.releasePointerCapture?.(event.pointerId);
                            }

                            function onWheel(event){
                                event.preventDefault();
                                const factor=event.deltaY<0?0.9:1.1;
                                state.distance*=factor;
                                apply();
                            }

                            function onKeyDown(event){
                                state.keyState[event.code]=true;
                            }

                            function onKeyUp(event){
                                delete state.keyState[event.code];
                            }

                            function moveByKeys(){
                                if(!camera){
                                    return;
                                }
                                const keys=state.keyState;
                                if(!keys || Object.keys(keys).length===0){
                                    return;
                                }

                                const speedBase=Math.max(0.8,state.distance*0.008);
                                const fast=(keys.ShiftLeft||keys.ShiftRight)?2.2:1.0;
                                const speed=speedBase*fast;

                                camera.getWorldDirection(tmpDir);
                                tmpDir.normalize();
                                tmpRight.crossVectors(tmpDir,camera.up).normalize();
                                tmpUp.copy(camera.up).normalize();

                                const delta=new THREE.Vector3();
                                if(keys.KeyW||keys.ArrowUp){
                                    delta.addScaledVector(tmpDir,speed);
                                }
                                if(keys.KeyS||keys.ArrowDown){
                                    delta.addScaledVector(tmpDir,-speed);
                                }
                                if(keys.KeyA||keys.ArrowLeft){
                                    delta.addScaledVector(tmpRight,-speed);
                                }
                                if(keys.KeyD||keys.ArrowRight){
                                    delta.addScaledVector(tmpRight,speed);
                                }
                                if(keys.KeyQ){
                                    delta.addScaledVector(tmpUp,speed);
                                }
                                if(keys.KeyE){
                                    delta.addScaledVector(tmpUp,-speed);
                                }

                                if(delta.lengthSq()>0){
                                    state.target.add(delta);
                                    apply();
                                }
                            }

                            domElement.addEventListener('contextmenu',event=>event.preventDefault());
                            domElement.addEventListener('pointerdown',onPointerDown);
                            domElement.addEventListener('pointermove',onPointerMove);
                            domElement.addEventListener('pointerup',onPointerUp);
                            domElement.addEventListener('pointercancel',onPointerUp);
                            domElement.addEventListener('wheel',onWheel,{passive:false});
                            window.addEventListener('keydown',onKeyDown);
                            window.addEventListener('keyup',onKeyUp);

                            return {
                                target:state.target,
                                minDistance:state.minDistance,
                                maxDistance:state.maxDistance,
                                update(){
                                    state.minDistance=this.minDistance;
                                    state.maxDistance=this.maxDistance;
                                    moveByKeys();
                                    apply();
                                }
                            };
                        }

                        function buildCubeScene(cubes){
                            if(!Array.isArray(cubes) || cubes.length===0){
                                info.textContent='Keine Hypercube-Daten verfuegbar.';
                                return;
                            }

                            const columns=Math.max(1,Math.ceil(Math.sqrt(cubes.length)));
                            const spacing=128;
                            const startX=-((columns-1)*spacing)/2;
                            const startZ=-((Math.ceil(cubes.length/columns)-1)*spacing)/2;

                            cubes.forEach((cube,cubeIndex)=>{
                                const row=Math.floor(cubeIndex/columns);
                                const col=cubeIndex%columns;
                                const center=new THREE.Vector3(startX+col*spacing,0,startZ+row*spacing);

                                const group=new THREE.Group();
                                group.position.copy(center);
                                scene.add(group);

                                const dimCount=Math.max(1,(cube.dimensions||[]).length);
                                const size=Math.min(52,20+dimCount*2.8);
                                const cubeGeometry=new THREE.BoxGeometry(size,size,size);
                                const cubeMaterial=new THREE.MeshPhysicalMaterial({
                                    color:0x1d5ea8,
                                    emissive:0x10233a,
                                    roughness:0.38,
                                    metalness:0.18,
                                    transmission:0.12,
                                    transparent:true,
                                    opacity:0.88
                                });
                                const cubeMesh=new THREE.Mesh(cubeGeometry,cubeMaterial);
                                cubeMesh.userData={type:'cube',cube};
                                group.add(cubeMesh);
                                pickables.push(cubeMesh);

                                const edgeLine=new THREE.LineSegments(
                                    new THREE.EdgesGeometry(cubeGeometry),
                                    new THREE.LineBasicMaterial({color:0x8dc3ff,transparent:true,opacity:0.75})
                                );
                                group.add(edgeLine);

                                const axisRadius=Math.max(30,size*1.2);
                                (cube.dimensions||[]).forEach((dimension,dimIndex)=>{
                                    const angle=(Math.PI*2*dimIndex)/Math.max(1,dimCount);
                                      const level=((dimIndex%3)-1)*16;
                                    const radialBoost=Math.min(34,dimension.members*0.55+dimension.domains*2.2);
                                    const orbit=axisRadius+radialBoost;

                                    const x=Math.cos(angle)*orbit;
                                    const y=level;
                                    const z=Math.sin(angle)*orbit;

                                    const lineGeometry=new THREE.BufferGeometry().setFromPoints([
                                        new THREE.Vector3(0,0,0),
                                        new THREE.Vector3(x,y,z)
                                    ]);
                                    const lineMaterial=new THREE.LineBasicMaterial({color:0x6fa2d4,transparent:true,opacity:0.58});
                                    const line=new THREE.Line(lineGeometry,lineMaterial);
                                    group.add(line);

                                    const sphereSize=Math.max(2.8,Math.min(14,3.2+dimension.domains*0.65+dimension.members*0.09));
                                    const sphereGeometry=new THREE.SphereGeometry(sphereSize,20,20);
                                    const sphereMaterial=new THREE.MeshStandardMaterial({
                                        color:dimension.defaults>0?0xf3c06c:0x6fd1b8,
                                        emissive:dimension.defaults>0?0x5a2f00:0x0f3e33,
                                        roughness:0.45,
                                        metalness:0.12
                                    });
                                    const sphere=new THREE.Mesh(sphereGeometry,sphereMaterial);
                                    sphere.position.set(x,y,z);
                                    sphere.userData={type:'dimension',cube,dimension};
                                    group.add(sphere);
                                    pickables.push(sphere);
                                });

                                cubeGroups.push({cube,group,cubeMesh});
                            });

                            resetCamera();
                        }

                        function fillCubeTable(cubes){
                            const sorted=[...(cubes||[])].sort((a,b)=>b.totalMembers-a.totalMembers).slice(0,120);
                            if(sorted.length===0){
                                tableBody.innerHTML='<tr><td colspan="6" class="muted">Keine Hypercubes gefunden.</td></tr>';
                                return;
                            }
                            tableBody.innerHTML=sorted.map(cube=>{
                                return '<tr>'
                                    + '<td><code>'+escapeHtml(cube.id)+'</code></td>'
                                    + '<td>'+((cube.dimensions||[]).length)+'</td>'
                                    + '<td>'+cube.totalDomains+'</td>'
                                    + '<td>'+cube.totalMembers+'</td>'
                                    + '<td>'+cube.totalDefaults+'</td>'
                                    + '<td>'+cube.primaryBindings+'</td>'
                                    + '</tr>';
                            }).join('');
                        }

                        function resizeScene(){
                            if(!renderer||!camera){
                                return;
                            }
                            const width=stage.clientWidth;
                            const height=stage.clientHeight;
                            renderer.setPixelRatio(qualityRatio());
                            renderer.setSize(width,height,false);
                            camera.aspect=width/Math.max(1,height);
                            camera.updateProjectionMatrix();
                        }

                        function animate(){
                            requestAnimationFrame(animate);
                            if(controls){
                                controls.update();
                            }
                            if(renderer&&scene&&camera){
                                renderer.render(scene,camera);
                            }
                        }

                        function onPointerMove(event){
                            if(!renderer||!camera||!raycaster||!pointer){
                                return;
                            }
                            const rect=renderer.domElement.getBoundingClientRect();
                            pointer.x=((event.clientX-rect.left)/rect.width)*2-1;
                            pointer.y=-((event.clientY-rect.top)/rect.height)*2+1;

                            raycaster.setFromCamera(pointer,camera);
                            const hits=raycaster.intersectObjects(pickables,false);
                            hovered=hits.length?hits[0].object:null;
                            if(!hovered){
                                tooltip.hidden=true;
                                return;
                            }

                            tooltip.hidden=false;
                            tooltip.style.left=Math.min(rect.width-16,event.clientX-rect.left+14)+'px';
                            tooltip.style.top=Math.min(rect.height-16,event.clientY-rect.top+14)+'px';
                            tooltip.innerHTML=tooltipText(hovered.userData);
                        }

                        function onPointerLeave(){
                            hovered=null;
                            tooltip.hidden=true;
                        }

                        function onPointerClick(){
                            if(!hovered){
                                return;
                            }
                            selected=hovered;
                            highlightSelection();
                            const data=hovered.userData||{};
                            const detailsHtml=selectionDetailsHtml(data);
                            info.innerHTML=detailsHtml;
                            renderOverlay(detailsHtml);
                        }

                        function selectionDetailsHtml(data){
                            if(data.type==='cube'){
                                return '<strong>Hypercube:</strong> <code>'+escapeHtml(data.cube.id)+'</code>'
                                    + '<div class="neighbor-list">'
                                    + '<div>Dimensionen: '+(data.cube.dimensions||[]).length+'</div>'
                                    + '<div>Domains: '+data.cube.totalDomains+'</div>'
                                    + '<div>Members: '+data.cube.totalMembers+'</div>'
                                    + '<div>Defaults: '+data.cube.totalDefaults+'</div>'
                                    + '<div>Primary-Bindings: '+data.cube.primaryBindings+'</div>'
                                    + '</div>';
                            }
                            if(data.type==='dimension'){
                                return '<strong>Dimension:</strong> <code>'+escapeHtml(data.dimension.id)+'</code>'
                                    + '<div class="neighbor-list">'
                                    + '<div>Hypercube: <code>'+escapeHtml(data.cube.id)+'</code></div>'
                                    + '<div>Domains: '+data.dimension.domains+'</div>'
                                    + '<div>Members: '+data.dimension.members+'</div>'
                                    + '<div>Defaults: '+data.dimension.defaults+'</div>'
                                    + '</div>';
                            }
                            return 'Objekt anklicken, um Details zu sehen.';
                        }

                        function renderOverlay(detailsHtml){
                            if(!overlay){
                                return;
                            }
                            const body=overlay.querySelector('.overlay-body');
                            if(body){
                                body.innerHTML=detailsHtml;
                            }
                        }

                        function syncFullscreenUi(){
                            if(!stage||!overlay){
                                return;
                            }
                            const isFullscreen=document.fullscreenElement===stage;
                            stage.classList.toggle('is-fullscreen',isFullscreen);
                        }

                        function tooltipText(data){
                            if(!data){
                                return '';
                            }
                            if(data.type==='cube'){
                                return '<strong>Hypercube</strong><br><span class="muted">Cube-ID:</span><br><code>'+escapeHtml(data.cube.id)+'</code>'
                                    + '<br>Dimensionen: '+(data.cube.dimensions||[]).length
                                    + '<br>Members: '+data.cube.totalMembers;
                            }
                            if(data.type==='dimension'){
                                return '<strong>Dimension</strong><br><span class="muted">Dimension-ID:</span><br><code>'+escapeHtml(data.dimension.id)+'</code>'
                                    + '<br><span class="muted">Cube-ID:</span><br><code>'+escapeHtml(data.cube.id)+'</code>'
                                    + '<br>Domains: '+data.dimension.domains
                                    + '<br>Members: '+data.dimension.members
                                    + '<br>Defaults: '+data.dimension.defaults;
                            }
                            return '';
                        }

                        function highlightSelection(){
                            cubeGroups.forEach(item=>{
                                item.cubeMesh.material.emissive.setHex(0x10233a);
                                item.cubeMesh.material.opacity=0.82;
                            });
                            pickables.forEach(object=>{
                                if(object.material && object.material.emissive){
                                    object.material.emissiveIntensity=0.9;
                                }
                            });

                            if(!selected || !selected.userData){
                                return;
                            }

                            const data=selected.userData;
                            if(data.type==='cube'){
                                selected.material.emissive.setHex(0x1f8fff);
                                selected.material.opacity=1;
                            }
                            if(data.type==='dimension'){
                                selected.material.emissive.setHex(0xff8f1f);
                                selected.material.emissiveIntensity=1.2;
                            }
                        }

                        function resetCamera(){
                            if(!camera||!controls){
                                return;
                            }
                            camera.position.set(210,160,260);
                            controls.target.set(0,0,0);
                            controls.update();
                        }

                        function fitToSelection(){
                            if(!selected||!camera||!controls){
                                return;
                            }
                            const worldPos=new THREE.Vector3();
                            selected.getWorldPosition(worldPos);
                            controls.target.copy(worldPos);
                            const offset=selected.userData&&selected.userData.type==='dimension'?46:72;
                            camera.position.set(worldPos.x+offset,worldPos.y+offset*0.75,worldPos.z+offset);
                            controls.update();
                        }

                        function applyCubeFilter(){
                            const query=(document.getElementById('cubeSearch').value||'').toLowerCase().trim();
                            cubeGroups.forEach(item=>{
                                const cubeId=(item.cube.id||'').toLowerCase();
                                const dimText=(item.cube.dimensions||[]).map(d=>d.id||'').join(' ').toLowerCase();
                                const visible=!query||cubeId.includes(query)||dimText.includes(query);
                                item.group.visible=visible;
                            });
                        }

                        function toggleFullscreen(){
                            if(!document.fullscreenElement){
                                stage.requestFullscreen?.();
                            } else {
                                document.exitFullscreen?.();
                            }
                        }

                        function escapeHtml(value){
                            return String(value||'')
                                .replaceAll('&','&amp;')
                                .replaceAll('<','&lt;')
                                .replaceAll('>','&gt;')
                                .replaceAll('"','&quot;')
                                .replaceAll("'",'&#39;');
                        }

                        initScene();
                        </script>
                        """;

                    String script = scriptTemplate.replace("__HYPERCUBE_DATA__", cubesJson.toString());

                return renderPage("Hypercube 3D View", body.toString(), script);
        }

    private String renderComplexityHtml(PresentationForest forest,
                                        TaxonomyMetadata metadata,
                                        Map<String, List<MappingEntry>> mappingsByConcept) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        Map<String, Integer> calcDegree = new TreeMap<>();
        for (LinkEdge edge : metadata.sampleEdges()) {
            if (!"calculation".equals(edge.layer())) {
                continue;
            }
            calcDegree.merge(normalizeConceptKey(edge.source()), 1, Integer::sum);
            calcDegree.merge(normalizeConceptKey(edge.target()), 1, Integer::sum);
        }

        Set<String> conceptKeys = new TreeSet<>(mappingsByConcept.keySet());
        conceptKeys.addAll(metadata.formulaMentionsByConcept().keySet());
        conceptKeys.addAll(calcDegree.keySet());

        List<ComplexityRow> rows = new ArrayList<>();
        for (String conceptKey : conceptKeys) {
            List<MappingEntry> entries = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = entries.isEmpty() ? conceptKey : entries.get(0).concept();

            Set<String> dimensions = new TreeSet<>(taxonomyDimensionsByConcept.getOrDefault(conceptKey, Set.of()));
            int enumSignals = 0;
            Set<String> fields = new TreeSet<>();
            for (MappingEntry entry : entries) {
                fields.add(entry.field());
                if (entry.dimensions() != null) {
                    for (DimensionSelection dimension : entry.dimensions()) {
                        if (dimension.axisQname() != null && !dimension.axisQname().isBlank()) {
                            dimensions.add(dimension.axisQname());
                        }
                    }
                }
                if (hasEnumeration(entry)) {
                    enumSignals++;
                }
            }
            if (metadata.taxonomyEnumerationsByConcept().containsKey(conceptKey)) {
                enumSignals++;
            }

            int dimensionCount = dimensions.size();

            int calc = calcDegree.getOrDefault(conceptKey, 0);
            int formulaMentions = metadata.formulaMentionsByConcept().getOrDefault(conceptKey, 0);
            int score = (dimensionCount * 3) + (enumSignals * 2) + (calc * 2) + formulaMentions;

            rows.add(new ComplexityRow(concept, score, dimensionCount, enumSignals, calc, formulaMentions, fields));
        }

        rows.sort(Comparator.comparingInt(ComplexityRow::score).reversed().thenComparing(ComplexityRow::concept));

        long highRisk = rows.stream().filter(row -> row.score() >= 20).count();
        long mediumRisk = rows.stream().filter(row -> row.score() >= 10 && row.score() < 20).count();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Complexity View: Concept Complexity Scorer</h1>")
            .append("<p class=\"lead\">Gewichteter Indikator je Konzept aus Dimensionen, Enumeration-Signalen, Calculation-Grad und Formula-Mentions. Der Score dient zur Priorisierung von Tests und Reviews.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Konzepte", rows.size()))
            .append(summaryCard("High Risk (>=20)", highRisk))
            .append(summaryCard("Medium Risk (10-19)", mediumRisk))
            .append(summaryCard("Formula-Konzepte", metadata.formulaMentionsByConcept().size()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"complexitySearch\" type=\"search\" placeholder=\"Konzept oder Feld suchen...\" oninput=\"applyComplexityFilter()\"></div>")
            .append("<section><h2>Score-Tabelle</h2><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Score</th><th>Dimensionen</th><th>Enumeration</th><th>Calc-Grad</th><th>Formula-Mentions</th><th>Felder</th></tr></thead><tbody>");

        int rowLimit = Math.min(600, rows.size());
        for (int i = 0; i < rowLimit; i++) {
            ComplexityRow row = rows.get(i);
            String search = normalizeSearch(row.concept() + " " + String.join(" ", row.fields()));
            body.append("<tr class=\"complexity-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(row.score())
                .append("</td><td>")
                .append(row.dimensionCount())
                .append("</td><td>")
                .append(row.enumerationSignals())
                .append("</td><td>")
                .append(row.calcDegree())
                .append("</td><td>")
                .append(row.formulaMentions())
                .append("</td><td>")
                .append(row.fields().isEmpty() ? "-" : escapeHtml(limitJoined(row.fields(), 6)))
                .append("</td></tr>");
        }

        if (rows.size() > rowLimit) {
            body.append("<tr><td colspan=\"7\" class=\"muted\">Nur die ersten ")
                .append(rowLimit)
                .append(" Konzepte werden angezeigt. Bitte Suche nutzen.</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyComplexityFilter(){const q=normalize(document.getElementById('complexitySearch').value.trim());"
            + "document.querySelectorAll('.complexity-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "</script>";

        return renderPage("Complexity View", body.toString(), script);
    }

    private String renderImpactHeatmapHtml(PresentationForest forest,
                                           TaxonomyMetadata metadata,
                                           Map<String, List<MappingEntry>> mappingsByConcept,
                                           Map<String, List<String>> placeholdersByField) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        List<ImpactHeatmapRow> rows = new ArrayList<>();
        Map<String, Long> sections = new TreeMap<>();

        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            Map<String, List<MappingEntry>> entriesBySection = conceptEntry.getValue().stream()
                .collect(Collectors.groupingBy(entry -> deriveSection(entry.field()), TreeMap::new, Collectors.toList()));

            for (Map.Entry<String, List<MappingEntry>> sectionEntry : entriesBySection.entrySet()) {
                String section = sectionEntry.getKey();
                List<MappingEntry> sectionMappings = sectionEntry.getValue();

                Set<String> fields = sectionMappings.stream()
                    .map(MappingEntry::field)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new));

                Set<String> placeholders = new TreeSet<>();
                for (String field : fields) {
                    List<String> values = placeholdersByField.get(field);
                    if (values != null) {
                        placeholders.addAll(values);
                    }
                }

                int mappingCount = sectionMappings.size();
                boolean conceptHasTaxonomyDimensions = taxonomyDimensionsByConcept.containsKey(conceptEntry.getKey())
                    && !taxonomyDimensionsByConcept.get(conceptEntry.getKey()).isEmpty();
                boolean conceptHasTaxonomyEnumeration = metadata.taxonomyEnumerationsByConcept().containsKey(conceptEntry.getKey());
                int dimensionSignals = (int) sectionMappings.stream()
                    .filter(entry -> hasDimensions(entry) || conceptHasTaxonomyDimensions)
                    .count();
                int enumerationSignals = (int) sectionMappings.stream()
                    .filter(entry -> hasEnumeration(entry) || conceptHasTaxonomyEnumeration)
                    .count();
                int placeholderCount = placeholders.size();
                int score = mappingCount + (dimensionSignals * 2) + (enumerationSignals * 2) + placeholderCount;

                rows.add(new ImpactHeatmapRow(
                    conceptEntry.getKey(),
                    section,
                    mappingCount,
                    dimensionSignals,
                    enumerationSignals,
                    placeholderCount,
                    score,
                    fields,
                    placeholders
                ));
                sections.merge(section, 1L, Long::sum);
            }
        }

        rows.sort(Comparator.comparingInt(ImpactHeatmapRow::score).reversed()
            .thenComparing(ImpactHeatmapRow::section)
            .thenComparing(ImpactHeatmapRow::concept));

        int maxScore = rows.stream().mapToInt(ImpactHeatmapRow::score).max().orElse(1);
        long maxMappedConcepts = sections.values().stream().mapToLong(Long::longValue).max().orElse(0);

        StringBuilder body = new StringBuilder();
        body.append("<h1>Impact Heatmap View: Konzept x Section</h1>")
            .append("<p class=\"lead\">Diese Sicht priorisiert Konzepte je Berichts-Section anhand von Mapping-Dichte, Dimensions-/Enumeration-Signalen und Placeholder-Abdeckung.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Concept-Section Paare", rows.size()))
            .append(summaryCard("Sections", sections.size()))
            .append(summaryCard("Max Impact Score", maxScore))
            .append(summaryCard("Max Concepts je Section", maxMappedConcepts))
            .append("</div>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"impactSearch\" type=\"search\" placeholder=\"Section, Konzept, Feld oder Placeholder suchen...\" oninput=\"applyImpactFilter()\">")
            .append("<label class=\"filter\">Section <select id=\"impactSection\" onchange=\"applyImpactFilter()\"><option value=\"\">Alle</option>");

        for (String section : sections.keySet()) {
            body.append("<option value=\"")
                .append(escapeHtml(section))
                .append("\">")
                .append(escapeHtml(section))
                .append("</option>");
        }

        body.append("</select></label>")
            .append("<label class=\"filter\">Min Score <input id=\"impactMinScore\" type=\"range\" min=\"0\" max=\"")
            .append(maxScore)
            .append("\" value=\"0\" oninput=\"applyImpactFilter()\"><span id=\"impactMinScoreValue\">0</span></label>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"applyImpactPreset('gaps')\">Preset: Gaps</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"applyImpactPreset('high')\">Preset: High Risk</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"saveImpactFilters()\">Filter speichern</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"loadImpactFilters()\">Filter laden</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"exportImpactCsv()\">CSV Export</button>")
            .append("</div>")
            .append("<section><h2>Heatmap Tabelle</h2><table class=\"layout-table\"><thead><tr><th>Section</th><th>Konzept</th><th>Mappings</th><th>Dimensions</th><th>Enumeration</th><th>Placeholders</th><th>Impact</th><th>Felder</th></tr></thead><tbody>");

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"8\" class=\"muted\">Keine Daten fuer die Impact-Heatmap verfuegbar.</td></tr>");
        }

        for (ImpactHeatmapRow row : rows) {
            String search = normalizeSearch(
                row.section() + " " + row.concept() + " " + String.join(" ", row.fields()) + " " + String.join(" ", row.placeholders())
            );
            int percent = maxScore <= 0 ? 0 : (int) Math.round((row.score() * 100.0) / maxScore);

            body.append("<tr class=\"impact-row\" data-section=\"")
                .append(escapeHtml(row.section()))
                .append("\" data-score=\"")
                .append(row.score())
                .append("\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.section()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(row.mappingCount())
                .append("</td><td>")
                .append(row.dimensionSignals())
                .append("</td><td>")
                .append(row.enumerationSignals())
                .append("</td><td>")
                .append(row.placeholderCount())
                .append("</td><td><div class=\"impact-bar\"><span style=\"width:")
                .append(percent)
                .append("%\"></span></div><div class=\"muted\">score ")
                .append(row.score())
                .append("</div></td><td>")
                .append(escapeHtml(limitJoined(row.fields(), 5)))
                .append("<div class=\"muted\"><a href=\"taxonomy-visualization-coverage.html#")
                .append(escapeHtml(urlFragment(row.concept())))
                .append("\">Coverage</a> | <a href=\"taxonomy-visualization-reference.html#")
                .append(escapeHtml(urlFragment(row.concept())))
                .append("\">Reference</a></div></td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<style>"
            + ".impact-bar{height:10px;background:#eaf0f7;border-radius:999px;overflow:hidden;min-width:120px;}"
            + ".impact-bar span{display:block;height:100%;background:linear-gradient(90deg,#5cb85c 0%,#f0ad4e 55%,#d9534f 100%);border-radius:999px;}"
            + "#impactMinScore{accent-color:#17324d;}"
            + "</style><script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyImpactFilter(){"
            + "const q=normalize(document.getElementById('impactSearch').value.trim());"
            + "const section=document.getElementById('impactSection').value;"
            + "const minScore=Number(document.getElementById('impactMinScore').value||0);"
            + "document.getElementById('impactMinScoreValue').textContent=String(minScore);"
            + "document.querySelectorAll('.impact-row').forEach(r=>{"
            + "const s=r.dataset.search||'';"
            + "const rowSection=r.dataset.section||'';"
            + "const score=Number(r.dataset.score||0);"
            + "const textOk=!q||s.includes(q);"
            + "const sectionOk=!section||section===rowSection;"
            + "const scoreOk=score>=minScore;"
            + "r.hidden=!(textOk&&sectionOk&&scoreOk);"
            + "});"
            + "}"
            + "function applyImpactPreset(mode){"
            + "if(mode==='gaps'){document.getElementById('impactMinScore').value='1';document.getElementById('impactSection').value='';}"
            + "if(mode==='high'){document.getElementById('impactMinScore').value='8';}"
            + "applyImpactFilter();"
            + "}"
            + "function saveImpactFilters(){localStorage.setItem('impactFilters',JSON.stringify({q:document.getElementById('impactSearch').value,section:document.getElementById('impactSection').value,min:document.getElementById('impactMinScore').value}));}"
            + "function loadImpactFilters(){try{const v=JSON.parse(localStorage.getItem('impactFilters')||'{}');document.getElementById('impactSearch').value=v.q||'';document.getElementById('impactSection').value=v.section||'';document.getElementById('impactMinScore').value=v.min||'0';}catch(e){}applyImpactFilter();}"
            + "function exportImpactCsv(){const rows=[['Section','Konzept','Mappings','Dimensions','Enumeration','Placeholders','Score','Felder']];document.querySelectorAll('.impact-row').forEach(r=>{if(r.hidden)return;const t=r.querySelectorAll('td');rows.push([t[0].innerText.trim(),t[1].innerText.trim(),t[2].innerText.trim(),t[3].innerText.trim(),t[4].innerText.trim(),t[5].innerText.trim(),t[6].innerText.trim(),t[7].innerText.trim()]);});const csv=rows.map(a=>a.map(v=>String(v).replaceAll('\\t',' ')).join('\\t')).join('\\n');const blob=new Blob([csv],{type:'text/tab-separated-values;charset=utf-8;'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='impact-heatmap.tsv';a.click();URL.revokeObjectURL(a.href);}"
            + "</script>";

        return renderPage("Impact Heatmap View", body.toString(), script);
    }

    private String renderStatsHtml(TaxonomyMetadata metadata) {
        Map<String, Integer> outDegree = new TreeMap<>();
        Map<String, Integer> inDegree = new TreeMap<>();
        Set<String> nodes = new TreeSet<>();

        for (LinkEdge edge : metadata.sampleEdges()) {
            nodes.add(edge.source());
            nodes.add(edge.target());
            outDegree.merge(edge.source(), 1, Integer::sum);
            inDegree.merge(edge.target(), 1, Integer::sum);
        }

        List<NodeStatsRow> topDegreeRows = new ArrayList<>();
        for (String node : nodes) {
            int out = outDegree.getOrDefault(node, 0);
            int in = inDegree.getOrDefault(node, 0);
            topDegreeRows.add(new NodeStatsRow(node, out, in, out + in));
        }
        topDegreeRows.sort(Comparator.comparingInt(NodeStatsRow::degree).reversed().thenComparing(NodeStatsRow::node));

        List<NodeStatsRow> sourceOnly = topDegreeRows.stream()
            .filter(row -> row.outDegree() > 0 && row.inDegree() == 0)
            .limit(40)
            .toList();
        List<NodeStatsRow> targetOnly = topDegreeRows.stream()
            .filter(row -> row.inDegree() > 0 && row.outDegree() == 0)
            .limit(40)
            .toList();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Stats View: Linkbase Edge Statistics</h1>")
            .append("<p class=\"lead\">Kompakte Struktur- und Qualitaetssicht auf Layer-Verteilung, Top-Knoten und potenzielle Randknoten aus dem Edge-Sample.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Layer gesamt", metadata.allLayers().size()))
            .append(summaryCard("Sample-Kanten", metadata.sampleEdges().size()))
            .append(summaryCard("Sample-Knoten", nodes.size()))
            .append(summaryCard("Source-only", sourceOnly.size()))
            .append(summaryCard("Target-only", targetOnly.size()))
            .append("</div>")
            .append("<section><h2>Layer-Verteilung</h2><table class=\"layout-table\"><thead><tr><th>Layer</th><th>Dateien</th><th>Kanten</th><th>Anteil Kanten</th></tr></thead><tbody>");

        long totalEdges = metadata.edgeCountByLayer().values().stream().mapToLong(Long::longValue).sum();
        for (String layer : metadata.allLayers()) {
            long edgeCount = metadata.edgeCountByLayer().getOrDefault(layer, 0L);
            long fileCount = metadata.fileCountByLayer().getOrDefault(layer, 0L);
            String share = totalEdges == 0 ? "0.0%" : String.format(Locale.ROOT, "%.1f%%", (edgeCount * 100.0) / totalEdges);
            body.append("<tr><td><code>")
                .append(escapeHtml(layer))
                .append("</code></td><td>")
                .append(fileCount)
                .append("</td><td>")
                .append(edgeCount)
                .append("</td><td>")
                .append(share)
                .append("</td></tr>");
        }
        body.append("</tbody></table></section>")
            .append("<section><h2>Top-Knoten nach Grad (Sample)</h2><table class=\"layout-table\"><thead><tr><th>Knoten</th><th>Out</th><th>In</th><th>Grad</th></tr></thead><tbody>");

        int topLimit = Math.min(120, topDegreeRows.size());
        for (int i = 0; i < topLimit; i++) {
            NodeStatsRow row = topDegreeRows.get(i);
            body.append("<tr><td><code>")
                .append(escapeHtml(row.node()))
                .append("</code></td><td>")
                .append(row.outDegree())
                .append("</td><td>")
                .append(row.inDegree())
                .append("</td><td>")
                .append(row.degree())
                .append("</td></tr>");
        }
        if (topDegreeRows.isEmpty()) {
            body.append("<tr><td colspan=\"4\" class=\"muted\">Keine Knoten im Sample vorhanden.</td></tr>");
        }
        body.append("</tbody></table></section>")
            .append("<section><h2>Struktur-Hinweise (Sample)</h2><div class=\"flow-grid\">")
            .append(renderNodeBucketCard("Source-only Knoten", sourceOnly))
            .append(renderNodeBucketCard("Target-only Knoten", targetOnly))
            .append("</div></section>");

        return renderPage("Stats View", body.toString(), "");
    }

    private String renderNodeBucketCard(String title, List<NodeStatsRow> rows) {
        StringBuilder card = new StringBuilder();
        card.append("<article class=\"flow-step\"><div class=\"title\">")
            .append(escapeHtml(title))
            .append("</div><div class=\"muted\">")
            .append(rows.size())
            .append(" Knoten</div><div class=\"node-children\">");
        if (rows.isEmpty()) {
            card.append("<div class=\"layout-row muted\">Keine Treffer im Sample.</div>");
        } else {
            int limit = Math.min(20, rows.size());
            for (int i = 0; i < limit; i++) {
                NodeStatsRow row = rows.get(i);
                card.append("<div class=\"layout-row\"><code>")
                    .append(escapeHtml(row.node()))
                    .append("</code></div>");
            }
            if (rows.size() > limit) {
                card.append("<div class=\"layout-row muted\">+")
                    .append(rows.size() - limit)
                    .append(" weitere</div>");
            }
        }
        card.append("</div></article>");
        return card.toString();
    }

    private String renderAllocationHtml(LayoutSnapshot layoutSnapshot,
                                        Map<String, List<MappingEntry>> mappingsByConcept) {
        List<AllocationRow> rows = new ArrayList<>();
        for (Map.Entry<String, String> mapping : layoutSnapshot.placeholderMappings().entrySet()) {
            String placeholder = mapping.getKey();
            String field = mapping.getValue();
            List<MappingEntry> entries = entriesForField(mappingsByConcept, field);
            String concept = entries.isEmpty() ? "-" : entries.get(0).concept();
            String section = deriveSection(field);
            rows.add(new AllocationRow(section, placeholder, field, concept));
        }

        rows.sort(Comparator.comparing(AllocationRow::section)
            .thenComparing(AllocationRow::placeholder)
            .thenComparing(AllocationRow::field));

        Map<String, Long> sectionCounts = rows.stream()
            .collect(Collectors.groupingBy(AllocationRow::section, TreeMap::new, Collectors.counting()));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Allocation View: Template zu Mapping</h1>")
            .append("<p class=\"lead\">Diese Sicht verbindet Berichtsplatzhalter mit Mapping-Feldern und Zielkonzepten. Die Gruppierung erfolgt nach Feld-Section (Praefix vor dem ersten Punkt).</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Placeholders", rows.size()))
            .append(summaryCard("Sections", sectionCounts.size()))
            .append(summaryCard("Mapped Concepts", rows.stream().filter(row -> !"-".equals(row.concept())).count()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"allocationSearch\" type=\"search\" placeholder=\"Section, Placeholder, Feld oder Konzept suchen...\" oninput=\"applyAllocationFilter()\"></div>")
            .append("<section><h2>Section Summary</h2><table class=\"layout-table\"><thead><tr><th>Section</th><th>Placeholders</th></tr></thead><tbody>");

        for (Map.Entry<String, Long> sectionCount : sectionCounts.entrySet()) {
            body.append("<tr><td><code>")
                .append(escapeHtml(sectionCount.getKey()))
                .append("</code></td><td>")
                .append(sectionCount.getValue())
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>")
            .append("<section><h2>Placeholder Allocation</h2><table class=\"layout-table\"><thead><tr><th>Section</th><th>Placeholder</th><th>Feld</th><th>Konzept</th></tr></thead><tbody>");

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"4\" class=\"muted\">Keine Placeholder-Zuordnung gefunden.</td></tr>");
        }

        for (AllocationRow row : rows) {
            String search = normalizeSearch(row.section() + " " + row.placeholder() + " " + row.field() + " " + row.concept());
            body.append("<tr class=\"allocation-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.section()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.placeholder()))
                .append("</code></td><td>")
                .append(escapeHtml(row.field()))
                .append("</td><td>")
                .append("-".equals(row.concept()) ? "-" : "<code>" + escapeHtml(row.concept()) + "</code>")
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyAllocationFilter(){const q=normalize(document.getElementById('allocationSearch').value.trim());"
            + "document.querySelectorAll('.allocation-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "</script>";

        return renderPage("Allocation View", body.toString(), script);
    }

    private String deriveSection(String field) {
        if (field == null || field.isBlank()) {
            return "other";
        }
        int dot = field.indexOf('.');
        if (dot > 0) {
            return field.substring(0, dot);
        }
        return "other";
    }

    private String renderValidationHtml(TaxonomyMetadata metadata,
                                        Map<String, List<MappingEntry>> mappingsByConcept) {
        Map<String, List<String>> formulaConceptsByFile = metadata.formulaConceptsByFile();
        List<FormulaRuleRow> ruleRows = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : formulaConceptsByFile.entrySet()) {
            List<String> concepts = entry.getValue();
            Set<String> mappedFields = new TreeSet<>();
            for (String concept : concepts) {
                List<MappingEntry> mappings = mappingsByConcept.getOrDefault(normalizeConceptKey(concept), List.of());
                for (MappingEntry mapping : mappings) {
                    mappedFields.add(mapping.field());
                }
            }
            ruleRows.add(new FormulaRuleRow(entry.getKey(), concepts, mappedFields));
        }

        ruleRows.sort(Comparator.comparingInt((FormulaRuleRow row) -> row.concepts().size()).reversed()
            .thenComparing(FormulaRuleRow::formulaFile));

        List<ValidationConceptRow> conceptRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : metadata.formulaMentionsByConcept().entrySet()) {
            String conceptKey = entry.getKey();
            List<MappingEntry> mappings = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = mappings.isEmpty() ? conceptKey : mappings.get(0).concept();
            Set<String> fields = mappings.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            conceptRows.add(new ValidationConceptRow(concept, entry.getValue(), fields));
        }
        conceptRows.sort(Comparator.comparingInt(ValidationConceptRow::mentions).reversed().thenComparing(ValidationConceptRow::concept));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Validation View: Rule Dependency</h1>")
            .append("<p class=\"lead\">Diese Sicht verbindet Formula-Regeldateien mit den darin referenzierten ESRS-Konzepten. Damit laesst sich schnell erkennen, welche Konzepte bei Regelanpassungen betroffen sein koennen.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Formula-Dateien", ruleRows.size()))
            .append(summaryCard("Konzepte in Formeln", metadata.formulaMentionsByConcept().size()))
            .append(summaryCard("Mentions gesamt", metadata.formulaMentionsByConcept().values().stream().mapToInt(Integer::intValue).sum()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"validationSearch\" type=\"search\" placeholder=\"Formula-Datei, Konzept oder Feld suchen...\" oninput=\"applyValidationFilter()\"></div>")
            .append("<section><h2>Formula-Datei -> Konzepte</h2><table class=\"layout-table\"><thead><tr><th>Formula-Datei</th><th>Konzepte</th><th>Gemappte Felder</th></tr></thead><tbody>");

        if (ruleRows.isEmpty()) {
            body.append("<tr><td colspan=\"3\" class=\"muted\">Keine Formula-Abhaengigkeiten gefunden.</td></tr>");
        }

        int ruleRowIndex = 0;
        for (FormulaRuleRow row : ruleRows) {
            String search = normalizeSearch(row.formulaFile() + " " + String.join(" ", row.concepts()) + " " + String.join(" ", row.fields()));
            String conceptsHtml = renderExpandableLineList(new TreeSet<>(row.concepts()), 10, "validation-rule-" + ruleRowIndex + "-concepts");
            String fieldsHtml = renderExpandableLineList(row.fields(), 10, "validation-rule-" + ruleRowIndex + "-fields");
            body.append("<tr class=\"validation-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.formulaFile()))
                .append("</code></td><td>")
                .append(conceptsHtml)
                .append("</td><td>")
                .append(fieldsHtml)
                .append("</td></tr>");
            ruleRowIndex++;
        }

        body.append("</tbody></table></section>")
            .append("<section><h2>Konzept-Hotspots (Mentions)</h2><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Mentions</th><th>Gemappte Felder</th></tr></thead><tbody>");

        if (conceptRows.isEmpty()) {
            body.append("<tr><td colspan=\"3\" class=\"muted\">Keine Konzept-Mentions gefunden.</td></tr>");
        }

        int conceptLimit = Math.min(400, conceptRows.size());
        for (int i = 0; i < conceptLimit; i++) {
            ValidationConceptRow row = conceptRows.get(i);
            String search = normalizeSearch(row.concept() + " " + row.mentions() + " " + String.join(" ", row.fields()));
            String fieldsHtml = renderExpandableLineList(row.fields(), 10, "validation-concept-" + i + "-fields");
            body.append("<tr class=\"validation-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(row.mentions())
                .append("</td><td>")
                .append(fieldsHtml)
                .append("</td></tr>");
        }
        if (conceptRows.size() > conceptLimit) {
            body.append("<tr><td colspan=\"3\" class=\"muted\">Nur die ersten ")
                .append(conceptLimit)
                .append(" Konzepte werden angezeigt. Bitte Suche nutzen.</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyValidationFilter(){const q=normalize(document.getElementById('validationSearch').value.trim());"
            + "document.querySelectorAll('.validation-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "function toggleExpandableList(id,btn){const extra=document.getElementById(id);if(!extra)return;const open=extra.dataset.open==='true';if(open){extra.style.display='none';extra.dataset.open='false';btn.textContent='+'+(btn.dataset.hiddenCount||'0')+' anzeigen';}else{extra.style.display='block';extra.dataset.open='true';btn.textContent='weniger anzeigen';}}"
            + "</script>";

        return renderPage("Validation View", body.toString(), script);
    }

    private String renderExpandableLineList(Set<String> values,
                                            int visibleCount,
                                            String listIdSeed) {
        if (values == null || values.isEmpty()) {
            return "-";
        }

        List<String> sorted = new ArrayList<>(values);
        int initial = Math.min(Math.max(1, visibleCount), sorted.size());
        int hiddenCount = sorted.size() - initial;
        String extraId = fieldId(listIdSeed + "-extra");

        StringBuilder html = new StringBuilder();
        html.append("<div>");
        for (int i = 0; i < initial; i++) {
            html.append("<div>")
                .append(escapeHtml(sorted.get(i)))
                .append("</div>");
        }

        if (hiddenCount > 0) {
            html.append("<div id=\"")
                .append(extraId)
                .append("\" style=\"display:none;\" data-open=\"false\">");
            for (int i = initial; i < sorted.size(); i++) {
                html.append("<div>")
                    .append(escapeHtml(sorted.get(i)))
                    .append("</div>");
            }
            html.append("</div>")
                .append("<button type=\"button\" class=\"secondary\" data-hidden-count=\"")
                .append(hiddenCount)
                .append("\" onclick=\"toggleExpandableList('")
                .append(escapeHtml(extraId))
                .append("', this)\">+")
                .append(hiddenCount)
                .append(" anzeigen</button>");
        }

        html.append("</div>");
        return html.toString();
    }

    private String renderIntersectionHtml(TaxonomyMetadata metadata) {
        HypercubeMetadata hypercubeMetadata = metadata.hypercubeMetadata();
        List<IntersectionRow> rows = new ArrayList<>();

        for (HypercubeCube cube : hypercubeMetadata.cubes()) {
            List<String> dimensions = cube.dimensions();
            if (dimensions.size() < 2) {
                continue;
            }
            for (int i = 0; i < dimensions.size(); i++) {
                for (int j = i + 1; j < dimensions.size(); j++) {
                    String first = dimensions.get(i);
                    String second = dimensions.get(j);
                    int firstMembers = memberCountForDimension(cube, first);
                    int secondMembers = memberCountForDimension(cube, second);
                    long combinations = (long) firstMembers * (long) secondMembers;
                    rows.add(new IntersectionRow(cube.cube(), first, second, firstMembers, secondMembers, combinations));
                }
            }
        }

        rows.sort(Comparator.comparingLong(IntersectionRow::combinationCount).reversed()
            .thenComparing(IntersectionRow::cube)
            .thenComparing(IntersectionRow::dimensionA)
            .thenComparing(IntersectionRow::dimensionB));

        long totalCombinations = rows.stream().mapToLong(IntersectionRow::combinationCount).sum();
        long cubesWithPairs = rows.stream().map(IntersectionRow::cube).distinct().count();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Intersection View: Dimension-Kombinationen</h1>")
            .append("<p class=\"lead\">Diese Sicht zeigt pro Hypercube die Paarkombinationen von Dimensionen und die daraus ableitbare Menge moeglicher Member-Kombinationen.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Hypercubes mit Paaren", cubesWithPairs))
            .append(summaryCard("Dimensionspaare", rows.size()))
            .append(summaryCard("Kombinationen (Summe)", totalCombinations))
            .append(summaryCard("Dimensionale Relationen", hypercubeMetadata.relationCount()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"intersectionSearch\" type=\"search\" placeholder=\"Hypercube oder Dimension suchen...\" oninput=\"applyIntersectionFilter()\"></div>")
            .append("<section><h2>Dimensionspaare pro Hypercube</h2><table class=\"layout-table\"><thead><tr><th>Hypercube</th><th>Dimension A</th><th>Dimension B</th><th>Member A</th><th>Member B</th><th>A x B</th></tr></thead><tbody>");

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"6\" class=\"muted\">Keine Dimensionspaare gefunden. Es werden mindestens zwei Dimensionen je Hypercube benoetigt.</td></tr>");
        }

        for (IntersectionRow row : rows) {
            String search = normalizeSearch(row.cube() + " " + row.dimensionA() + " " + row.dimensionB());
            body.append("<tr class=\"intersection-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.cube()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimensionA()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimensionB()))
                .append("</code></td><td>")
                .append(row.membersA())
                .append("</td><td>")
                .append(row.membersB())
                .append("</td><td>")
                .append(row.combinationCount())
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyIntersectionFilter(){const q=normalize(document.getElementById('intersectionSearch').value.trim());"
            + "document.querySelectorAll('.intersection-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "</script>";

        return renderPage("Intersection View", body.toString(), script);
    }

    private String renderHypercubeDimensionInventoryHtml(TaxonomyMetadata metadata) {
        HypercubeMetadata hypercubeMetadata = metadata.hypercubeMetadata();
        List<HypercubeDimensionInventoryRow> rows = new ArrayList<>();

        for (HypercubeCube cube : hypercubeMetadata.cubes()) {
            int primaryBindings = cube.primaryItemsAll().size() + cube.primaryItemsNotAll().size();
            for (String dimension : cube.dimensions()) {
                int domains = cube.domainsPerDimension().getOrDefault(dimension, List.of()).size();
                int members = memberCountForDimension(cube, dimension);
                int defaults = cube.defaultsPerDimension().getOrDefault(dimension, List.of()).size();
                boolean maybeTypedAxis = domains == 0;
                rows.add(new HypercubeDimensionInventoryRow(
                    cube.cube(),
                    dimension,
                    domains,
                    members,
                    defaults,
                    primaryBindings,
                    maybeTypedAxis
                ));
            }
        }

        rows.sort(Comparator.comparingInt(HypercubeDimensionInventoryRow::members).reversed()
            .thenComparing(HypercubeDimensionInventoryRow::cube)
            .thenComparing(HypercubeDimensionInventoryRow::dimension));

        int maxMembers = rows.stream().mapToInt(HypercubeDimensionInventoryRow::members).max().orElse(0);
        long typedLikeCount = rows.stream().filter(HypercubeDimensionInventoryRow::maybeTypedAxis).count();
        long missingDefaultCount = rows.stream().filter(row -> row.defaults() == 0).count();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Hypercube Dimension Inventar</h1>")
            .append("<p class=\"lead\">Inventarsicht je Hypercube-Achse mit zentralen Kennzahlen fuer Domains, Members, Defaults und Primary-Bindings. Ideal fuer Gap- und Konsistenzchecks.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Hypercubes", hypercubeMetadata.cubes().size()))
            .append(summaryCard("Dimensionen", rows.size()))
            .append(summaryCard("Ohne Default", missingDefaultCount))
            .append(summaryCard("Moeglich Typed Axis", typedLikeCount))
            .append("</div>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"hdiSearch\" type=\"search\" placeholder=\"Hypercube oder Dimension suchen...\" oninput=\"applyHdiFilter()\">")
            .append("<label class=\"filter\">Min Members <input id=\"hdiMinMembers\" type=\"range\" min=\"0\" max=\"")
            .append(maxMembers)
            .append("\" value=\"0\" oninput=\"applyHdiFilter()\"><span id=\"hdiMinMembersValue\">0</span></label>")
            .append("<label class=\"filter\"><input id=\"hdiNoDefault\" type=\"checkbox\" onchange=\"applyHdiFilter()\"> Nur ohne Default</label>")
            .append("<label class=\"filter\"><input id=\"hdiTypedAxis\" type=\"checkbox\" onchange=\"applyHdiFilter()\"> Nur moeglich typed axis</label>")
            .append("</div>")
            .append("<section><h2>Dimensionstabelle</h2><table class=\"layout-table\"><thead><tr><th>Hypercube</th><th>Dimension</th><th>Domains</th><th>Members</th><th>Defaults</th><th>Primary-Bindings</th><th>Typed?</th></tr></thead><tbody>");

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"7\" class=\"muted\">Keine Hypercube-Dimensionsdaten gefunden.</td></tr>");
        }

        for (HypercubeDimensionInventoryRow row : rows) {
            String search = normalizeSearch(row.cube() + " " + row.dimension());
            int memberPercent = maxMembers <= 0 ? 0 : (int) Math.round((row.members() * 100.0) / maxMembers);
            body.append("<tr class=\"hdi-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\" data-members=\"")
                .append(row.members())
                .append("\" data-defaults=\"")
                .append(row.defaults())
                .append("\" data-typed=\"")
                .append(row.maybeTypedAxis())
                .append("\"><td><code>")
                .append(escapeHtml(row.cube()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimension()))
                .append("</code></td><td>")
                .append(row.domains())
                .append("</td><td><div class=\"hdi-bar\"><span style=\"width:")
                .append(memberPercent)
                .append("%\"></span></div><div class=\"muted\">")
                .append(row.members())
                .append("</div></td><td>")
                .append(row.defaults())
                .append("</td><td>")
                .append(row.primaryBindings())
                .append("</td><td>")
                .append(statusPill(!row.maybeTypedAxis(), row.maybeTypedAxis() ? "moeglich" : "explizit"))
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<style>"
            + ".hdi-bar{height:10px;background:#eaf0f7;border-radius:999px;overflow:hidden;min-width:120px;}"
            + ".hdi-bar span{display:block;height:100%;background:linear-gradient(90deg,#84b6f4 0%,#1e5f99 100%);border-radius:999px;}"
            + "#hdiMinMembers{accent-color:#17324d;}"
            + "</style><script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyHdiFilter(){"
            + "const q=normalize(document.getElementById('hdiSearch').value.trim());"
            + "const minMembers=Number(document.getElementById('hdiMinMembers').value||0);"
            + "const noDefault=document.getElementById('hdiNoDefault').checked;"
            + "const typedOnly=document.getElementById('hdiTypedAxis').checked;"
            + "document.getElementById('hdiMinMembersValue').textContent=String(minMembers);"
            + "document.querySelectorAll('.hdi-row').forEach(r=>{"
            + "const s=r.dataset.search||'';"
            + "const members=Number(r.dataset.members||0);"
            + "const defaults=Number(r.dataset.defaults||0);"
            + "const typed=(r.dataset.typed||'false')==='true';"
            + "const textOk=!q||s.includes(q);"
            + "const membersOk=members>=minMembers;"
            + "const defaultOk=!noDefault||defaults===0;"
            + "const typedOk=!typedOnly||typed;"
            + "r.hidden=!(textOk&&membersOk&&defaultOk&&typedOk);"
            + "});"
            + "}"
            + "</script>";

        return renderPage("Hypercube Dimension Inventar", body.toString(), script);
    }

    private String renderMappingFlowHtml(PresentationForest forest,
                                         Map<String, List<MappingEntry>> mappingsByConcept,
                                         TaxonomyMetadata metadata) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        Map<String, Set<String>> cubesByDimension = new TreeMap<>();
        for (HypercubeCube cube : metadata.hypercubeMetadata().cubes()) {
            for (String dimension : cube.dimensions()) {
                cubesByDimension.computeIfAbsent(normalizeConceptKey(dimension), key -> new TreeSet<>()).add(cube.cube());
            }
        }

        List<MappingFlowRow> rows = new ArrayList<>();
        Set<String> sections = new TreeSet<>();
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            for (MappingEntry mapping : conceptEntry.getValue()) {
                String section = deriveSection(mapping.field());
                sections.add(section);

                Set<String> dimensions = new TreeSet<>();
                Set<String> dimensionKeys = new TreeSet<>();
                Set<String> cubes = new TreeSet<>();
                if (mapping.dimensions() != null) {
                    for (DimensionSelection dimension : mapping.dimensions()) {
                        if (dimension.axisQname() == null || dimension.axisQname().isBlank()) {
                            continue;
                        }
                        String axis = dimension.axisQname();
                        dimensions.add(toDisplayQName(axis));
                        dimensionKeys.add(normalizeConceptKey(axis));
                    }
                }

                if (dimensionKeys.isEmpty()) {
                    Set<String> taxonomyAxes = taxonomyDimensionsByConcept.getOrDefault(conceptEntry.getKey(), Set.of());
                    for (String axis : taxonomyAxes) {
                        dimensionKeys.add(normalizeConceptKey(axis));
                        dimensions.add(toDisplayQName(axis));
                    }
                }

                for (String axisKey : dimensionKeys) {
                        Set<String> owningCubes = cubesByDimension.get(axisKey);
                        if (owningCubes != null) {
                            cubes.addAll(owningCubes);
                        }
                }

                rows.add(new MappingFlowRow(
                    section,
                    mapping.field(),
                    mapping.concept(),
                    mapping.period(),
                    mapping.unit(),
                    dimensions,
                    cubes,
                    hasEnumeration(mapping)
                ));
            }
        }

        rows.sort(Comparator.comparing((MappingFlowRow row) -> row.cubes().size()).reversed()
            .thenComparing((MappingFlowRow row) -> row.dimensions().size()).reversed()
            .thenComparing(MappingFlowRow::section)
            .thenComparing(MappingFlowRow::field));

        long withCubeMatch = rows.stream().filter(row -> !row.cubes().isEmpty()).count();
        long withDimensions = rows.stream().filter(row -> !row.dimensions().isEmpty()).count();
        long withEnumeration = rows.stream().filter(MappingFlowRow::enumeration).count();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Mapping Flow View: Feld -> Konzept -> Hypercube</h1>")
            .append("<p class=\"lead\">Sankey-orientierte Flows als Tabelle: Welche Felder auf welche Konzepte und weiter auf welche Hypercubes/Achsen wirken.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Flow-Zeilen", rows.size()))
            .append(summaryCard("Mit Hypercube-Match", withCubeMatch))
            .append(summaryCard("Mit Dimensionen", withDimensions))
            .append(summaryCard("Mit Enumeration", withEnumeration))
            .append("</div>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"flowSearch\" type=\"search\" placeholder=\"Section, Feld, Konzept, Dimension oder Hypercube suchen...\" oninput=\"applyMappingFlowFilter()\">")
            .append("<label class=\"filter\">Section <select id=\"flowSection\" onchange=\"applyMappingFlowFilter()\"><option value=\"\">Alle</option>");

        for (String section : sections) {
            body.append("<option value=\"")
                .append(escapeHtml(section))
                .append("\">")
                .append(escapeHtml(section))
                .append("</option>");
        }

        body.append("</select></label>")
            .append("<label class=\"filter\"><input id=\"flowDimOnly\" type=\"checkbox\" onchange=\"applyMappingFlowFilter()\"> Nur mit Dimensionen</label>")
            .append("<label class=\"filter\"><input id=\"flowCubeOnly\" type=\"checkbox\" onchange=\"applyMappingFlowFilter()\"> Nur mit Hypercube-Match</label>")
            .append("</div>")
            .append("<section><h2>Flow-Tabelle</h2><table class=\"layout-table\"><thead><tr><th>Section</th><th>Feld</th><th>Konzept</th><th>Periode</th><th>Einheit</th><th>Dimensionen</th><th>Hypercubes</th><th>Enumeration</th></tr></thead><tbody>");

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"8\" class=\"muted\">Keine Mapping-Flow-Daten gefunden.</td></tr>");
        }

        int rowIndex = 0;
        for (MappingFlowRow row : rows) {
            String search = normalizeSearch(
                row.section() + " " + row.field() + " " + row.concept() + " " + String.join(" ", row.dimensions()) + " " + String.join(" ", row.cubes())
            );
            String dimensionsHtml = renderExpandableLineList(row.dimensions(), 10, "mapping-flow-" + rowIndex + "-dimensions");
            String cubesHtml = renderExpandableLineList(row.cubes(), 10, "mapping-flow-" + rowIndex + "-cubes");
            body.append("<tr class=\"mapping-flow-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\" data-section=\"")
                .append(escapeHtml(row.section()))
                .append("\" data-has-dim=\"")
                .append(!row.dimensions().isEmpty())
                .append("\" data-has-cube=\"")
                .append(!row.cubes().isEmpty())
                .append("\"><td><code>")
                .append(escapeHtml(row.section()))
                .append("</code></td><td>")
                .append(escapeHtml(row.field()))
                .append("</td><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(escapeHtml(row.period() == null || row.period().isBlank() ? "-" : row.period()))
                .append("</td><td>")
                .append(escapeHtml(row.unit() == null || row.unit().isBlank() ? "-" : row.unit()))
                .append("</td><td>")
                .append(dimensionsHtml)
                .append("</td><td>")
                .append(cubesHtml)
                .append("</td><td>")
                .append(statusPill(row.enumeration(), row.enumeration() ? "ja" : "nein"))
                .append("</td></tr>");
            rowIndex++;
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyMappingFlowFilter(){"
            + "const q=normalize(document.getElementById('flowSearch').value.trim());"
            + "const section=document.getElementById('flowSection').value;"
            + "const dimOnly=document.getElementById('flowDimOnly').checked;"
            + "const cubeOnly=document.getElementById('flowCubeOnly').checked;"
            + "document.querySelectorAll('.mapping-flow-row').forEach(r=>{"
            + "const s=r.dataset.search||'';"
            + "const rowSection=r.dataset.section||'';"
            + "const hasDim=(r.dataset.hasDim||'false')==='true';"
            + "const hasCube=(r.dataset.hasCube||'false')==='true';"
            + "const textOk=!q||s.includes(q);"
            + "const sectionOk=!section||section===rowSection;"
            + "const dimOk=!dimOnly||hasDim;"
            + "const cubeOk=!cubeOnly||hasCube;"
            + "r.hidden=!(textOk&&sectionOk&&dimOk&&cubeOk);"
            + "});"
            + "}"
            + "function toggleExpandableList(id,btn){const extra=document.getElementById(id);if(!extra)return;const open=extra.dataset.open==='true';if(open){extra.style.display='none';extra.dataset.open='false';btn.textContent='+'+(btn.dataset.hiddenCount||'0')+' anzeigen';}else{extra.style.display='block';extra.dataset.open='true';btn.textContent='weniger anzeigen';}}"
            + "</script>";

        return renderPage("Mapping Flow View", body.toString(), script);
    }

    private String renderConceptBacklogHtml(PresentationForest forest,
                                            Map<String, List<MappingEntry>> mappingsByConcept,
                                            Map<String, List<String>> placeholdersByField,
                                            TaxonomyMetadata metadata) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        List<ConceptBacklogRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            List<MappingEntry> entries = conceptEntry.getValue();
            String concept = entries.isEmpty() ? conceptEntry.getKey() : entries.get(0).concept();
            Set<String> fields = entries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            Set<String> placeholders = new TreeSet<>();
            for (String field : fields) {
                List<String> values = placeholdersByField.get(field);
                if (values != null) {
                    placeholders.addAll(values);
                }
            }
            boolean hasLayout = !placeholders.isEmpty();
            boolean conceptHasTaxonomyDimensions = taxonomyDimensionsByConcept.containsKey(conceptEntry.getKey())
                && !taxonomyDimensionsByConcept.get(conceptEntry.getKey()).isEmpty();
            int dimSignals = (int) entries.stream().filter(entry -> hasDimensions(entry) || conceptHasTaxonomyDimensions).count();
            int enumSignals = (int) entries.stream().filter(TaxonomyVisualizationExporter::hasEnumeration).count()
                + (metadata.taxonomyEnumerationsByConcept().containsKey(conceptEntry.getKey()) ? 1 : 0);
            int calc = metadata.formulaMentionsByConcept().getOrDefault(normalizeConceptKey(concept), 0);
            int riskScore = fields.size() + dimSignals * 2 + enumSignals + calc;
            rows.add(new ConceptBacklogRow(concept, fields.size(), hasLayout, dimSignals, enumSignals, calc, riskScore, fields, placeholders));
        }

        rows.sort(Comparator.comparingInt(ConceptBacklogRow::riskScore).reversed().thenComparing(ConceptBacklogRow::concept));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Concept Backlog View</h1>")
            .append("<p class=\"lead\">Priorisierte Arbeitsliste je Konzept mit Risikoscore, Layoutabdeckung und Dimensions-/Enumerationssignalen.</p>")
            .append("<div class=\"toolbar\"><input id=\"backlogSearch\" type=\"search\" placeholder=\"Konzept oder Feld suchen...\" oninput=\"applyBacklogFilter()\"><label class=\"filter\"><input id=\"backlogNoLayout\" type=\"checkbox\" onchange=\"applyBacklogFilter()\"> Nur ohne Layout</label><button type=\"button\" class=\"secondary\" onclick=\"applyBacklogPreset('high')\">Preset: High Risk</button><button type=\"button\" class=\"secondary\" onclick=\"saveBacklogFilters()\">Filter speichern</button><button type=\"button\" class=\"secondary\" onclick=\"loadBacklogFilters()\">Filter laden</button><button type=\"button\" class=\"secondary\" onclick=\"exportBacklogCsv()\">CSV Export</button></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Risk</th><th>Layout</th><th>Dim</th><th>Enum</th><th>Formula</th><th>Felder</th></tr></thead><tbody>");

        for (ConceptBacklogRow row : rows) {
            String search = normalizeSearch(row.concept() + " " + String.join(" ", row.fields()));
            body.append("<tr class=\"backlog-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\" data-layout=\"")
                .append(row.hasLayout())
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(row.riskScore())
                .append("</td><td>")
                .append(statusPill(row.hasLayout(), row.hasLayout() ? "ja" : "nein"))
                .append("</td><td>")
                .append(row.dimensionSignals())
                .append("</td><td>")
                .append(row.enumerationSignals())
                .append("</td><td>")
                .append(row.formulaMentions())
                .append("</td><td>")
                .append(escapeHtml(limitJoined(row.fields(), 5)))
                .append("<div class=\"muted\"><a href=\"taxonomy-visualization-coverage.html#")
                .append(escapeHtml(urlFragment(row.concept())))
                .append("\">Coverage</a> | <a href=\"taxonomy-visualization-traceability-matrix.html#")
                .append(escapeHtml(urlFragment(row.concept())))
                .append("\">Traceability</a></div></td></tr>");
        }

        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyBacklogFilter(){const q=normalize(document.getElementById('backlogSearch').value.trim());const noLayout=document.getElementById('backlogNoLayout').checked;document.querySelectorAll('.backlog-row').forEach(r=>{const s=r.dataset.search||'';const layout=(r.dataset.layout||'false')==='true';r.hidden=!((!q||s.includes(q))&&(!noLayout||!layout));});}function applyBacklogPreset(mode){if(mode==='high'){document.getElementById('backlogSearch').value='';document.getElementById('backlogNoLayout').checked=false;document.querySelectorAll('.backlog-row').forEach(r=>{const risk=Number((r.children[1]||{}).innerText||0);r.hidden=risk<8;});return;}applyBacklogFilter();}function saveBacklogFilters(){localStorage.setItem('backlogFilters',JSON.stringify({q:document.getElementById('backlogSearch').value,noLayout:document.getElementById('backlogNoLayout').checked}));}function loadBacklogFilters(){try{const v=JSON.parse(localStorage.getItem('backlogFilters')||'{}');document.getElementById('backlogSearch').value=v.q||'';document.getElementById('backlogNoLayout').checked=!!v.noLayout;}catch(e){}applyBacklogFilter();}function exportBacklogCsv(){const rows=[['Konzept','Risk','Layout','Dim','Enum','Formula','Felder']];document.querySelectorAll('.backlog-row').forEach(r=>{if(r.hidden)return;const t=r.querySelectorAll('td');rows.push([t[0].innerText.trim(),t[1].innerText.trim(),t[2].innerText.trim(),t[3].innerText.trim(),t[4].innerText.trim(),t[5].innerText.trim(),t[6].innerText.trim()]);});const tsv=rows.map(a=>a.map(v=>String(v).replaceAll('\\t',' ')).join('\\t')).join('\\n');const blob=new Blob([tsv],{type:'text/tab-separated-values;charset=utf-8;'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='concept-backlog.tsv';a.click();URL.revokeObjectURL(a.href);}</script>";
        return renderPage("Concept Backlog View", body.toString(), script);
    }

    private String renderScopePeriodAnalysisHtml(PresentationForest forest,
                                                 TaxonomyMetadata metadata,
                                                 Map<String, List<MappingEntry>> mappingsByConcept) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        List<ScopePeriodRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            boolean conceptHasTaxonomyDimensions = taxonomyDimensionsByConcept.containsKey(conceptEntry.getKey())
                && !taxonomyDimensionsByConcept.get(conceptEntry.getKey()).isEmpty();
            boolean conceptHasTaxonomyEnumeration = metadata.taxonomyEnumerationsByConcept().containsKey(conceptEntry.getKey());
            List<MappingEntry> entries = conceptEntry.getValue();
            for (MappingEntry entry : entries) {
                rows.add(new ScopePeriodRow(
                    deriveSection(entry.field()),
                    entry.field(),
                    entry.concept(),
                    entry.period() == null || entry.period().isBlank() ? "-" : entry.period(),
                    entry.unit() == null || entry.unit().isBlank() ? "-" : entry.unit(),
                    hasDimensions(entry) || conceptHasTaxonomyDimensions,
                    hasEnumeration(entry) || conceptHasTaxonomyEnumeration
                ));
            }
        }

        rows.sort(Comparator.comparing(ScopePeriodRow::section).thenComparing(ScopePeriodRow::period).thenComparing(ScopePeriodRow::field));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Scope & Period Analysis</h1>")
            .append("<div class=\"toolbar\"><input id=\"scopeSearch\" type=\"search\" placeholder=\"Section, Feld, Konzept, Periode suchen...\" oninput=\"applyScopeFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Section</th><th>Feld</th><th>Konzept</th><th>Periode</th><th>Einheit</th><th>Dim</th><th>Enum</th></tr></thead><tbody>");

        for (ScopePeriodRow row : rows) {
            String search = normalizeSearch(row.section() + " " + row.field() + " " + row.concept() + " " + row.period() + " " + row.unit());
            body.append("<tr class=\"scope-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.section()))
                .append("</code></td><td>")
                .append(escapeHtml(row.field()))
                .append("</td><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(escapeHtml(row.period()))
                .append("</td><td>")
                .append(escapeHtml(row.unit()))
                .append("</td><td>")
                .append(statusPill(row.hasDimensions(), row.hasDimensions() ? "ja" : "nein"))
                .append("</td><td>")
                .append(statusPill(row.hasEnumeration(), row.hasEnumeration() ? "ja" : "nein"))
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyScopeFilter(){const q=normalize(document.getElementById('scopeSearch').value.trim());document.querySelectorAll('.scope-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Scope & Period Analysis", body.toString(), script);
    }

    private String renderRuleCoverageMatrixHtml(TaxonomyMetadata metadata,
                                                Map<String, List<MappingEntry>> mappingsByConcept) {
        List<RuleCoverageRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : metadata.formulaConceptsByFile().entrySet()) {
            for (String concept : entry.getValue()) {
                List<MappingEntry> mapped = mappingsByConcept.getOrDefault(normalizeConceptKey(concept), List.of());
                Set<String> fields = mapped.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
                rows.add(new RuleCoverageRow(entry.getKey(), concept, !fields.isEmpty(), fields));
            }
        }

        rows.sort(Comparator.comparing(RuleCoverageRow::formulaFile).thenComparing(RuleCoverageRow::concept));
        StringBuilder body = new StringBuilder();
        body.append("<h1>Rule Coverage Matrix</h1>")
            .append("<div class=\"toolbar\"><input id=\"ruleSearch\" type=\"search\" placeholder=\"Formula-Datei oder Konzept suchen...\" oninput=\"applyRuleFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Formula</th><th>Konzept</th><th>Mapped</th><th>Felder</th></tr></thead><tbody>");

        for (RuleCoverageRow row : rows) {
            String search = normalizeSearch(row.formulaFile() + " " + row.concept() + " " + String.join(" ", row.fields()));
            body.append("<tr class=\"rule-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.formulaFile()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(statusPill(row.mapped(), row.mapped() ? "ja" : "nein"))
                .append("</td><td>")
                .append(escapeHtml(row.fields().isEmpty() ? "-" : limitJoined(row.fields(), 5)))
                .append("</td></tr>");
        }
        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyRuleFilter(){const q=normalize(document.getElementById('ruleSearch').value.trim());document.querySelectorAll('.rule-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Rule Coverage Matrix", body.toString(), script);
    }

    private String renderIntersectionRiskHtml(TaxonomyMetadata metadata) {
        HypercubeMetadata hypercubeMetadata = metadata.hypercubeMetadata();
        List<IntersectionRiskRow> rows = new ArrayList<>();
        for (HypercubeCube cube : hypercubeMetadata.cubes()) {
            List<String> dimensions = cube.dimensions();
            for (int i = 0; i < dimensions.size(); i++) {
                for (int j = i + 1; j < dimensions.size(); j++) {
                    String a = dimensions.get(i);
                    String b = dimensions.get(j);
                    int membersA = memberCountForDimension(cube, a);
                    int membersB = memberCountForDimension(cube, b);
                    long combos = (long) membersA * membersB;
                    long risk = combos + (long) Math.max(0, membersA - 1) + Math.max(0, membersB - 1);
                    rows.add(new IntersectionRiskRow(cube.cube(), a, b, combos, risk));
                }
            }
        }
        rows.sort(Comparator.comparingLong(IntersectionRiskRow::riskScore).reversed());

        StringBuilder body = new StringBuilder();
        body.append("<h1>Intersection Risk View</h1>")
            .append("<div class=\"toolbar\"><input id=\"riskSearch\" type=\"search\" placeholder=\"Hypercube oder Dimension suchen...\" oninput=\"applyRiskFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Hypercube</th><th>Dimension A</th><th>Dimension B</th><th>Kombinationen</th><th>Risk Score</th></tr></thead><tbody>");

        for (IntersectionRiskRow row : rows) {
            String search = normalizeSearch(row.cube() + " " + row.dimensionA() + " " + row.dimensionB());
            body.append("<tr class=\"risk-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.cube()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimensionA()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimensionB()))
                .append("</code></td><td>")
                .append(row.combinations())
                .append("</td><td>")
                .append(row.riskScore())
                .append("</td></tr>");
        }
        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyRiskFilter(){const q=normalize(document.getElementById('riskSearch').value.trim());document.querySelectorAll('.risk-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Intersection Risk View", body.toString(), script);
    }

    private String renderTraceabilityMatrixHtml(Map<String, List<MappingEntry>> mappingsByConcept,
                                                Map<String, List<String>> placeholdersByField,
                                                Map<String, List<String>> conceptReferences,
                                                TaxonomyMetadata metadata) {
        Set<String> conceptKeys = new TreeSet<>(mappingsByConcept.keySet());
        conceptKeys.addAll(conceptReferences.keySet());

        List<TraceabilityMatrixRow> rows = new ArrayList<>();
        for (String conceptKey : conceptKeys) {
            List<MappingEntry> entries = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = entries.isEmpty() ? conceptKey : entries.get(0).concept();
            Set<String> fields = entries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            Set<String> placeholders = new TreeSet<>();
            for (String field : fields) {
                List<String> values = placeholdersByField.get(field);
                if (values != null) {
                    placeholders.addAll(values);
                }
            }
            List<String> refs = conceptReferences.getOrDefault(conceptKey, List.of());
            boolean taxonomyEnum = taxonomyEnumerationForConcept(metadata, concept) != null;
            rows.add(new TraceabilityMatrixRow(concept, refs, fields, placeholders, taxonomyEnum));
        }

        rows.sort(Comparator.comparing((TraceabilityMatrixRow row) -> row.references().size()).reversed().thenComparing(TraceabilityMatrixRow::concept));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Traceability Matrix View</h1>")
            .append("<div class=\"toolbar\"><input id=\"traceSearch\" type=\"search\" placeholder=\"Konzept, Referenz, Feld oder Placeholder suchen...\" oninput=\"applyTraceFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Referenzen</th><th>Felder</th><th>Placeholders</th><th>Taxonomie Enum</th></tr></thead><tbody>");

        for (TraceabilityMatrixRow row : rows) {
            String search = normalizeSearch(row.concept() + " " + String.join(" ", row.references()) + " " + String.join(" ", row.fields()) + " " + String.join(" ", row.placeholders()));
            body.append("<tr class=\"trace-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(escapeHtml(row.references().isEmpty() ? "-" : limitJoined(new TreeSet<>(row.references()), 6)))
                .append("</td><td>")
                .append(escapeHtml(row.fields().isEmpty() ? "-" : limitJoined(row.fields(), 5)))
                .append("</td><td>")
                .append(escapeHtml(row.placeholders().isEmpty() ? "-" : limitJoined(row.placeholders(), 5)))
                .append("</td><td>")
                .append(statusPill(row.taxonomyEnumeration(), row.taxonomyEnumeration() ? "ja" : "nein"))
                .append("</td></tr>");
        }
        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyTraceFilter(){const q=normalize(document.getElementById('traceSearch').value.trim());document.querySelectorAll('.trace-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Traceability Matrix View", body.toString(), script);
    }

    private String renderDimensionCooccurrenceHtml(TaxonomyMetadata metadata) {
        Map<String, Integer> pairCount = new TreeMap<>();
        for (HypercubeCube cube : metadata.hypercubeMetadata().cubes()) {
            List<String> dims = cube.dimensions();
            for (int i = 0; i < dims.size(); i++) {
                for (int j = i + 1; j < dims.size(); j++) {
                    String a = dims.get(i);
                    String b = dims.get(j);
                    String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
                    pairCount.merge(key, 1, Integer::sum);
                }
            }
        }

        List<DimensionCooccurrenceRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pairCount.entrySet()) {
            int split = entry.getKey().indexOf('|');
            rows.add(new DimensionCooccurrenceRow(entry.getKey().substring(0, split), entry.getKey().substring(split + 1), entry.getValue()));
        }
        rows.sort(Comparator.comparingInt(DimensionCooccurrenceRow::count).reversed().thenComparing(DimensionCooccurrenceRow::dimensionA));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Dimension Co-Occurrence View</h1>")
            .append("<div class=\"toolbar\"><input id=\"coSearch\" type=\"search\" placeholder=\"Dimension suchen...\" oninput=\"applyCoFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Dimension A</th><th>Dimension B</th><th>Haeufigkeit</th></tr></thead><tbody>");

        for (DimensionCooccurrenceRow row : rows) {
            String search = normalizeSearch(row.dimensionA() + " " + row.dimensionB());
            body.append("<tr class=\"co-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.dimensionA()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimensionB()))
                .append("</code></td><td>")
                .append(row.count())
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyCoFilter(){const q=normalize(document.getElementById('coSearch').value.trim());document.querySelectorAll('.co-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Dimension Co-Occurrence View", body.toString(), script);
    }

    private String renderDefaultMemberQualityHtml(TaxonomyMetadata metadata) {
        List<DefaultMemberQualityRow> rows = new ArrayList<>();
        for (HypercubeCube cube : metadata.hypercubeMetadata().cubes()) {
            for (String dimension : cube.dimensions()) {
                int defaults = cube.defaultsPerDimension().getOrDefault(dimension, List.of()).size();
                int domains = cube.domainsPerDimension().getOrDefault(dimension, List.of()).size();
                String status = defaults == 0 ? "missing" : (defaults == 1 ? "ok" : "conflict");
                rows.add(new DefaultMemberQualityRow(cube.cube(), dimension, domains, defaults, status));
            }
        }
        rows.sort(Comparator.comparing(DefaultMemberQualityRow::status).thenComparing(DefaultMemberQualityRow::cube).thenComparing(DefaultMemberQualityRow::dimension));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Default Member Quality View</h1>")
            .append("<div class=\"toolbar\"><input id=\"defSearch\" type=\"search\" placeholder=\"Hypercube oder Dimension suchen...\" oninput=\"applyDefaultFilter()\"><label class=\"filter\"><input id=\"defIssues\" type=\"checkbox\" onchange=\"applyDefaultFilter()\"> Nur Issues</label></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Hypercube</th><th>Dimension</th><th>Domains</th><th>Defaults</th><th>Status</th></tr></thead><tbody>");

        for (DefaultMemberQualityRow row : rows) {
            String search = normalizeSearch(row.cube() + " " + row.dimension() + " " + row.status());
            boolean issue = !"ok".equals(row.status());
            body.append("<tr class=\"def-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\" data-issue=\"")
                .append(issue)
                .append("\"><td><code>")
                .append(escapeHtml(row.cube()))
                .append("</code></td><td><code>")
                .append(escapeHtml(row.dimension()))
                .append("</code></td><td>")
                .append(row.domains())
                .append("</td><td>")
                .append(row.defaults())
                .append("</td><td>")
                .append(statusPill(!issue, row.status()))
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyDefaultFilter(){const q=normalize(document.getElementById('defSearch').value.trim());const issues=document.getElementById('defIssues').checked;document.querySelectorAll('.def-row').forEach(r=>{const s=r.dataset.search||'';const issue=(r.dataset.issue||'false')==='true';r.hidden=!((!q||s.includes(q))&&(!issues||issue));});}</script>";
        return renderPage("Default Member Quality View", body.toString(), script);
    }

    private String renderExternalSchemasHtml(List<ExternalSchemaReference> externalSchemaReferences,
                                             List<ExternalSchemaType> externalSchemaTypes,
                                             List<ExternalSchemaEdge> externalSchemaEdges,
                                             List<ExternalSchemaSubstitution> externalSchemaSubstitutions) {
        StringBuilder body = new StringBuilder();
        List<ExternalSchemaReference> references = externalSchemaReferences == null ? List.of() : externalSchemaReferences;
        List<ExternalSchemaType> types = externalSchemaTypes == null ? List.of() : externalSchemaTypes;
        List<ExternalSchemaEdge> edges = externalSchemaEdges == null ? List.of() : externalSchemaEdges;
        body.append("<h1 id=\"externalSchemaTop\">External Schema References</h1>")
            .append("<p class=\"lead\">Externe XBRL-Schemata, welche die ESRS-Taxonomie referenziert. Diese Sicht dient als Nachweis der verwendeten Standard-Namespaces und Linkbase-Referenzen.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Externe Schemas", references.size()))
            .append(summaryCard("DTR Types", countByKind(references, "DTR type namespace")))
            .append(summaryCard("Linkbase", countByKind(references, "XBRL linkbase namespace")))
            .append(summaryCard("XLink", countByKind(references, "XLink namespace")))
            .append(summaryCard("XBRL Dimensions", countByKind(references, "XBRL dimensions namespace")))
            .append(summaryCard("Analysierte XSD-Typen", types.size()))
            .append(summaryCard("XSD-Importe", edges.size()))
            .append(summaryCard("Substitution Groups", externalSchemaSubstitutions.size()))
            .append("</div>")
            .append("<nav class=\"view-jump-links\" aria-label=\"Externe Schema-Auswertungen\"><strong>Schnellzugriff:</strong><div><a href=\"#externalSubstitutions\">Substitution Groups</a></div><div><a href=\"#externalImports\">Import-/Include-Matrix</a></div><div><a href=\"#externalRankings\">Typ-Rankings</a></div><div><a href=\"#externalFacets\">Facet-/Enumeration-Analyse</a></div><div><a href=\"#externalTypes\">Typ-Inventar</a></div><div><a href=\"#externalNamespaces\">Namespace-Liste</a></div></nav>")
            .append("<section id=\"externalSubstitutions\"><h2>Substitution-Group-Tabelle</h2><div class=\"toolbar\"><input id=\"externalSubstitutionSearch\" type=\"search\" placeholder=\"Element, Gruppe, Typ oder Namespace suchen...\" oninput=\"applyExternalSchemaTableFilters()\"><label class=\"filter\">Gruppe <select id=\"externalSubstitutionGroup\" onchange=\"applyExternalSchemaTableFilters()\"><option value=\"\">Alle</option>");
        externalSchemaSubstitutions.stream().map(ExternalSchemaSubstitution::substitutionGroup).distinct().sorted().forEach(group -> body.append("<option value=\"")
            .append(escapeHtml(group)).append("\">").append(escapeHtml(group)).append("</option>"));
        body.append("</select></label></div><table id=\"externalSubstitutionTable\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalSubstitutionTable\" data-sort-column=\"0\">Substitution Group</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalSubstitutionTable\" data-sort-column=\"1\">Element</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalSubstitutionTable\" data-sort-column=\"2\">Typ</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalSubstitutionTable\" data-sort-column=\"3\">Namespace</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalSubstitutionTable\" data-sort-column=\"4\">Abstrakt</button></th></tr></thead><tbody>");
        for (ExternalSchemaSubstitution substitution : externalSchemaSubstitutions) {
            String search = (substitution.substitutionGroup() + " " + substitution.element() + " " + substitution.type() + " " + substitution.namespace()).toLowerCase(Locale.ROOT);
            body.append("<tr class=\"external-substitution-row\" data-group=\"").append(escapeHtml(substitution.substitutionGroup())).append("\" data-search=\"")
                .append(escapeHtml(search)).append("\"><td><code>").append(escapeHtml(substitution.substitutionGroup())).append("</code></td><td><code>")
                .append(escapeHtml(substitution.element())).append("</code></td><td><code>").append(escapeHtml(substitution.type())).append("</code></td><td><code>")
                .append(escapeHtml(substitution.namespace())).append("</code></td><td>").append(substitution.abstractElement() ? "ja" : "nein").append("</td></tr>");
        }
        if (externalSchemaSubstitutions.isEmpty()) body.append("<tr><td colspan=\"5\">Keine substitutionGroup-Elemente gefunden.</td></tr>");
        body.append("</tbody></table></section>")
            .append("<section id=\"externalImports\"><h2>Import-/Include-Matrix</h2><p class=\"lead\">Zeilen importieren oder inkludieren die in den Spalten aufgefuehrten Namespaces bzw. SchemaLocations.</p><div class=\"table-scroll\"><table id=\"externalImportMatrix\" class=\"layout-table\"><thead><tr><th>Quelle \\ Ziel</th>");
        Set<String> matrixNamespaces = new TreeSet<>();
        for (ExternalSchemaEdge edge : edges) {
            matrixNamespaces.add(edge.source());
            matrixNamespaces.add(edge.target());
        }
        for (String namespace : matrixNamespaces) {
            body.append("<th><code>").append(escapeHtml(namespace)).append("</code></th>");
        }
        body.append("</tr></thead><tbody>");
        for (String source : matrixNamespaces) {
            body.append("<tr><th><code>").append(escapeHtml(source)).append("</code></th>");
            for (String target : matrixNamespaces) {
                String relation = edges.stream().filter(edge -> source.equals(edge.source()) && target.equals(edge.target()))
                    .map(ExternalSchemaEdge::relation).distinct().sorted().collect(Collectors.joining(" / "));
                body.append("<td>").append(relation.isBlank() ? "" : escapeHtml(relation)).append("</td>");
            }
            body.append("</tr>");
        }
        if (matrixNamespaces.isEmpty()) body.append("<tr><td>Keine Import-/Include-Kanten gefunden.</td></tr>");
        body.append("</tbody></table></div></section>")
            .append("<section id=\"externalRankings\"><h2>Typkategorie- und Basistyp-Ranking</h2><div class=\"split-grid\"><div><h3>Typkategorien</h3><table id=\"externalTypeCategoryRanking\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeCategoryRanking\" data-sort-column=\"0\">Kategorie</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeCategoryRanking\" data-sort-column=\"1\">Anzahl</button></th><th>Anteil</th></tr></thead><tbody>");
        Map<String, Long> categoryCounts = types.stream().collect(Collectors.groupingBy(ExternalSchemaType::category, TreeMap::new, Collectors.counting()));
        for (Map.Entry<String, Long> entry : categoryCounts.entrySet()) {
            body.append("<tr><td>").append(escapeHtml(entry.getKey())).append("</td><td>").append(entry.getValue()).append("</td><td>")
                .append(formatPercentage(entry.getValue(), types.size())).append("</td></tr>");
        }
        if (categoryCounts.isEmpty()) body.append("<tr><td colspan=\"3\">Keine Typkategorien vorhanden.</td></tr>");
        body.append("</tbody></table></div><div><h3>Basistypen</h3><table id=\"externalBaseTypeRanking\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalBaseTypeRanking\" data-sort-column=\"0\">Basistyp</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalBaseTypeRanking\" data-sort-column=\"1\">Anzahl</button></th><th>Anteil</th></tr></thead><tbody>");
        Map<String, Long> baseCounts = types.stream().collect(Collectors.groupingBy(ExternalSchemaType::base, TreeMap::new, Collectors.counting()));
        baseCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).forEach(entry -> body.append("<tr><td><code>")
            .append(escapeHtml(entry.getKey())).append("</code></td><td>").append(entry.getValue()).append("</td><td>")
            .append(formatPercentage(entry.getValue(), types.size())).append("</td></tr>"));
        if (baseCounts.isEmpty()) body.append("<tr><td colspan=\"3\">Keine Basistypen vorhanden.</td></tr>");
        body.append("</tbody></table></div></div></section>")
            .append("<section id=\"externalFacets\"><h2>Facet-/Enumeration-Analyse</h2><div class=\"split-grid\"><div><h3>Facet-Haeufigkeit</h3><table id=\"externalFacetRanking\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalFacetRanking\" data-sort-column=\"0\">Facet</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalFacetRanking\" data-sort-column=\"1\">Typen</button></th></tr></thead><tbody>");
        Map<String, Long> facetCounts = new TreeMap<>();
        for (ExternalSchemaType type : types) {
            for (String facet : splitFacets(type.facets())) {
                facetCounts.merge(facet, 1L, Long::sum);
            }
        }
        facetCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).forEach(entry -> body.append("<tr><td>")
            .append(escapeHtml(entry.getKey())).append("</td><td>").append(entry.getValue()).append("</td></tr>"));
        if (facetCounts.isEmpty()) body.append("<tr><td colspan=\"2\">Keine Facets vorhanden.</td></tr>");
        body.append("</tbody></table></div><div><h3>Enumeration-Groesse</h3><table id=\"externalEnumerationRanking\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalEnumerationRanking\" data-sort-column=\"0\">Typ</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalEnumerationRanking\" data-sort-column=\"1\">Werte</button></th></tr></thead><tbody>");
        types.stream().map(type -> Map.entry(type, enumerationSize(type.facets())))
            .filter(entry -> entry.getValue() >= 0)
            .sorted(Map.Entry.<ExternalSchemaType, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().name()))
            .forEach(entry -> body.append("<tr><td><code>").append(escapeHtml(entry.getKey().namespace() + ":" + entry.getKey().name())).append("</code></td><td>")
                .append(entry.getValue()).append("</td></tr>"));
        if (types.stream().noneMatch(type -> enumerationSize(type.facets()) >= 0)) body.append("<tr><td colspan=\"2\">Keine Enumerationen vorhanden.</td></tr>");
        body.append("</tbody></table></div></div></section>")
            .append("<section id=\"externalTypes\"><h2>Typ-Inventar</h2><div class=\"toolbar\"><input id=\"externalTypeSearch\" type=\"search\" placeholder=\"Typ, Namespace, Basistyp oder Facet suchen...\" oninput=\"applyExternalSchemaTableFilters()\"><label class=\"filter\">Kategorie <select id=\"externalTypeCategory\" onchange=\"applyExternalSchemaTableFilters()\"><option value=\"\">Alle</option>");
        types.stream().map(ExternalSchemaType::category).distinct().sorted().forEach(category -> body.append("<option value=\"")
            .append(escapeHtml(category)).append("\">").append(escapeHtml(category)).append("</option>"));
        body.append("</select></label><label class=\"filter\">Status <select id=\"externalTypeStatus\" onchange=\"applyExternalSchemaTableFilters()\"><option value=\"\">Alle</option><option value=\"loaded\">geladen</option><option value=\"unavailable\">nicht verfügbar</option><option value=\"parse error\">Parse-Fehler</option></select></label><button type=\"button\" class=\"secondary\" onclick=\"resetExternalSchemaTypeFilters()\">Filter zurücksetzen</button></div>")
            .append("<table id=\"externalTypeTable\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeTable\" data-sort-column=\"0\">Kategorie</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeTable\" data-sort-column=\"1\">Typ</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeTable\" data-sort-column=\"2\">Basistyp</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeTable\" data-sort-column=\"3\">Facets</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalTypeTable\" data-sort-column=\"4\">Status</button></th></tr></thead><tbody>");
        for (ExternalSchemaType type : types) {
            body.append("<tr class=\"external-type-row\" data-category=\"").append(escapeHtml(type.category())).append("\" data-status=\"")
                .append(escapeHtml(type.status())).append("\" data-search=\"").append(escapeHtml((type.namespace() + " " + type.name() + " " + type.base() + " " + type.facets()).toLowerCase(Locale.ROOT))).append("\"><td>")
                .append(escapeHtml(type.category())).append("</td><td><code>")
                .append(escapeHtml(type.namespace() + ":" + type.name())).append("</code></td><td>")
                .append(escapeHtml(type.base())).append("</td><td>").append(escapeHtml(type.facets()))
                .append("</td><td>").append(escapeHtml(type.status())).append("</td></tr>");
        }
        if (types.isEmpty()) body.append("<tr><td colspan=\"5\">Keine externen XSD-Typen analysiert.</td></tr>");
        body.append("</tbody></table></section>")
            .append("<section id=\"externalNamespaces\"><h2>Namespace-Liste</h2><div class=\"toolbar\"><input id=\"externalNamespaceSearch\" type=\"search\" placeholder=\"Namespace, Location oder Quelle suchen...\" oninput=\"applyExternalSchemaTableFilters()\"><label class=\"filter\">Typ <select id=\"externalNamespaceKind\" onchange=\"applyExternalSchemaTableFilters()\"><option value=\"\">Alle</option>");
        references.stream().map(ExternalSchemaReference::kind).distinct().sorted().forEach(kind -> body.append("<option value=\"")
            .append(escapeHtml(kind)).append("\">").append(escapeHtml(kind)).append("</option>"));
        body.append("</select></label><button type=\"button\" class=\"secondary\" onclick=\"resetExternalNamespaceFilters()\">Filter zurücksetzen</button></div><table id=\"externalNamespaceTable\" class=\"layout-table\"><thead><tr><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalNamespaceTable\" data-sort-column=\"0\">Typ</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalNamespaceTable\" data-sort-column=\"1\">Namespace</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalNamespaceTable\" data-sort-column=\"2\">SchemaLocation/Hinweis</button></th><th><button type=\"button\" class=\"sort-button\" data-sort-table=\"externalNamespaceTable\" data-sort-column=\"3\">Quelle</button></th></tr></thead><tbody>");

        if (references.isEmpty()) {
            body.append("<tr><td colspan=\"4\">Keine externen Schemas gefunden.</td></tr>");
        } else {
            for (ExternalSchemaReference ref : references) {
                body.append("<tr class=\"external-namespace-row\" data-kind=\"").append(escapeHtml(ref.kind())).append("\" data-search=\"").append(escapeHtml((ref.namespace() + " " + ref.schemaLocation() + " " + ref.source()).toLowerCase(Locale.ROOT))).append("\">")
                    .append("<td>").append(escapeHtml(ref.kind())).append("</td>")
                    .append("<td><code>").append(escapeHtml(ref.namespace())).append("</code></td>")
                    .append("<td><code>").append(escapeHtml(ref.schemaLocation())).append("</code></td>")
                    .append("<td>").append(escapeHtml(ref.source())).append("</td>")
                    .append("</tr>");
            }
        }

        body.append("</tbody></table></section><a class=\"back-to-top\" style=\"position:fixed;right:22px;bottom:22px;z-index:20;background:#17324d;color:#fff;text-decoration:none;border-radius:999px;padding:10px 15px;box-shadow:0 6px 18px rgba(23,50,77,.2);font-weight:600\" href=\"#externalSchemaTop\" aria-label=\"Nach oben springen\">Nach oben</a>");
        return renderPage("External Schema References", body.toString(), renderExternalSchemaTableScript());
    }

    private String renderExternalSchemaTableScript() {
        return """
            <script>
            function resetExternalSchemaTypeFilters(){
                const search=document.getElementById('externalTypeSearch'); if(search) search.value='';
                const category=document.getElementById('externalTypeCategory'); if(category) category.value='';
                const status=document.getElementById('externalTypeStatus'); if(status) status.value='';
                applyExternalSchemaTableFilters();
            }
            function resetExternalNamespaceFilters(){
                const search=document.getElementById('externalNamespaceSearch'); if(search) search.value='';
                const kind=document.getElementById('externalNamespaceKind'); if(kind) kind.value='';
                applyExternalSchemaTableFilters();
            }
            function applyExternalSchemaTableFilters(){
                const substitutionQuery=((document.getElementById('externalSubstitutionSearch')||{}).value||'').toLowerCase().trim();
                const substitutionGroup=((document.getElementById('externalSubstitutionGroup')||{}).value||'');
                document.querySelectorAll('.external-substitution-row').forEach(row=>{const match=(!substitutionQuery||(row.dataset.search||'').includes(substitutionQuery))&&(!substitutionGroup||row.dataset.group===substitutionGroup);row.hidden=!match;});
                const typeQuery=((document.getElementById('externalTypeSearch')||{}).value||'').toLowerCase().trim();
                const typeCategory=((document.getElementById('externalTypeCategory')||{}).value||'');
                const typeStatus=((document.getElementById('externalTypeStatus')||{}).value||'');
                document.querySelectorAll('.external-type-row').forEach(row=>{const match=(!typeQuery||(row.dataset.search||'').includes(typeQuery))&&(!typeCategory||row.dataset.category===typeCategory)&&(!typeStatus||row.dataset.status===typeStatus);row.hidden=!match;});
                const namespaceQuery=((document.getElementById('externalNamespaceSearch')||{}).value||'').toLowerCase().trim();
                const namespaceKind=((document.getElementById('externalNamespaceKind')||{}).value||'');
                document.querySelectorAll('.external-namespace-row').forEach(row=>{const match=(!namespaceQuery||(row.dataset.search||'').includes(namespaceQuery))&&(!namespaceKind||row.dataset.kind===namespaceKind);row.hidden=!match;});
            }
            function sortTable(tableId, columnIndex, direction) {
                const table = document.getElementById(tableId);
                if (!table) return;
                const tbody = table.querySelector('tbody');
                if (!tbody) return;
                const rows = Array.from(tbody.querySelectorAll('tr')).filter(r => !r.hidden);
                rows.sort((a, b) => {
                    const left = (a.children[columnIndex]?.textContent || '').trim().toLowerCase();
                    const right = (b.children[columnIndex]?.textContent || '').trim().toLowerCase();
                    const result = left.localeCompare(right, undefined, {numeric: true, sensitivity: 'base'});
                    return direction === 'asc' ? result : -result;
                });
                rows.forEach(row => tbody.appendChild(row));
            }
            document.addEventListener('click', function(event) {
                const button = event.target.closest('.sort-button');
                if (!button) return;
                const tableId = button.dataset.sortTable;
                const columnIndex = Number(button.dataset.sortColumn || 0);
                const table = document.getElementById(tableId);
                if (!table) return;
                const current = table.dataset.sortState || '';
                const next = current === 'asc:' + columnIndex ? 'desc:' + columnIndex : 'asc:' + columnIndex;
                table.dataset.sortState = next;
                sortTable(tableId, columnIndex, next.startsWith('asc') ? 'asc' : 'desc');
            });
            </script>
            """;
    }

    private String renderExternalSchemaGraphScript(List<ExternalSchemaReference> references,
                                                   List<ExternalSchemaType> types,
                                                   List<ExternalSchemaEdge> schemaEdges) {
        StringBuilder nodes = new StringBuilder("[");
        StringBuilder edges = new StringBuilder("[");
        Set<String> groups = new HashSet<>();
        int edgeIndex = 0;
        for (ExternalSchemaReference ref : references) {
            String schemaId = "schema:" + ref.namespace();
            String groupId = "group:" + ref.kind();
            if (nodes.length() > 1) nodes.append(',');
            nodes.append("{id:'").append(escapeJs(schemaId)).append("',label:'").append(escapeJs(ref.namespace()))
                .append("',type:'schema',kind:'").append(escapeJs(ref.kind())).append("',location:'")
                .append(escapeJs(ref.schemaLocation())).append("',source:'").append(escapeJs(ref.source())).append("'}");
            if (groups.add(groupId)) {
                nodes.append(',').append("{id:'").append(escapeJs(groupId)).append("',label:'").append(escapeJs(ref.kind()))
                    .append("',type:'group',kind:'").append(escapeJs(ref.kind())).append("',location:'',source:''}");
            }
            if (edgeIndex++ > 0) edges.append(',');
            edges.append("{s:'").append(escapeJs(groupId)).append("',t:'").append(escapeJs(schemaId)).append("'}");
        }
        for (ExternalSchemaType type : types) {
            String typeId = "type:" + type.namespace() + ":" + type.name();
            String categoryId = "category:" + type.category();
            String schemaId = "schema:" + type.namespace();
            if (nodes.length() > 1) nodes.append(',');
            nodes.append("{id:'").append(escapeJs(typeId)).append("',label:'").append(escapeJs(type.name()))
                .append("',type:'type',kind:'").append(escapeJs(type.category())).append("',base:'")
                .append(escapeJs(type.base())).append("',facets:'").append(escapeJs(type.facets())).append("',location:'")
                .append(escapeJs(type.source())).append("',source:'").append(escapeJs(type.status())).append("'}");
            if (groups.add(categoryId)) {
                nodes.append(',').append("{id:'").append(escapeJs(categoryId)).append("',label:'").append(escapeJs(type.category()))
                    .append("',type:'group',kind:'").append(escapeJs(type.category())).append("',location:'',source:''}");
            }
            if (edgeIndex++ > 0) edges.append(',');
            edges.append("{s:'").append(escapeJs(categoryId)).append("',t:'").append(escapeJs(typeId)).append("'}");
            if (references.stream().anyMatch(reference -> reference.namespace().equals(type.namespace()))) {
                edges.append(',').append("{s:'").append(escapeJs(schemaId)).append("',t:'").append(escapeJs(typeId)).append("'}");
            }
        }
        for (ExternalSchemaEdge edge : schemaEdges) {
            String sourceId = "schema:" + edge.source();
            String targetId = "schema:" + edge.target();
            if (references.stream().anyMatch(reference -> reference.namespace().equals(edge.source()) || reference.namespace().equals(edge.target()))) {
                edges.append(',').append("{s:'").append(escapeJs(sourceId)).append("',t:'").append(escapeJs(targetId)).append("'}");
            }
        }
        nodes.append(']');
        edges.append(']');
        return """
            <script>
            const externalSchemaNodes=%s;
            const externalSchemaEdges=%s;
            function clearExternalSchemaSearch(){const e=document.getElementById('externalSchemaSearch');if(e)e.value='';drawExternalSchemaGraph();}
                        function applyExternalSchemaTableFilters(){
                            const typeQuery=((document.getElementById('externalTypeSearch')||{}).value||'').toLowerCase().trim();
                            const typeCategory=((document.getElementById('externalTypeCategory')||{}).value||'');
                            const typeStatus=((document.getElementById('externalTypeStatus')||{}).value||'');
                            document.querySelectorAll('.external-type-row').forEach(row=>{const match=(!typeQuery||(row.dataset.search||'').includes(typeQuery))&&(!typeCategory||row.dataset.category===typeCategory)&&(!typeStatus||row.dataset.status===typeStatus);row.hidden=!match;});
                            const namespaceQuery=((document.getElementById('externalNamespaceSearch')||{}).value||'').toLowerCase().trim();
                            const namespaceKind=((document.getElementById('externalNamespaceKind')||{}).value||'');
                            document.querySelectorAll('.external-namespace-row').forEach(row=>{const match=(!namespaceQuery||(row.dataset.search||'').includes(namespaceQuery))&&(!namespaceKind||row.dataset.kind===namespaceKind);row.hidden=!match;});
                        }
            function drawExternalSchemaGraph(){
              const svg=document.getElementById('externalSchemaGraph');if(!svg)return;
              const query=((document.getElementById('externalSchemaSearch')||{}).value||'').toLowerCase();
                            const category=((document.getElementById('externalSchemaCategory')||{}).value||'');
                            const status=((document.getElementById('externalSchemaStatus')||{}).value||'');
                            const matches=n=>!query||[n.label,n.location,n.source,n.kind,n.base,n.facets].join(' ').toLowerCase().includes(query);
                            const visibleTypes=externalSchemaNodes.filter(n=>n.type==='type'&&matches(n)&&(!category||n.kind===category)&&(!status||n.source===status));
                            const visibleSchemaIds=new Set(visibleTypes.map(n=>n.id.replace(/^type:/,'').split(':').slice(0,-1).join(':')).map(n=>'schema:'+n));
                            const isVisible=n=>{if(n.type==='type')return visibleTypes.includes(n);if(n.type==='group')return !category||n.kind===category;return !query||matches(n)||visibleSchemaIds.has(n.id);};
                            const visible=externalSchemaNodes.filter(isVisible);
              const ids=new Set(visible.map(n=>n.id));
              const edges=externalSchemaEdges.filter(e=>ids.has(e.s)&&ids.has(e.t));
              const groups=visible.filter(n=>n.type==='group'),schemas=visible.filter(n=>n.type==='schema'||n.type==='type'),positions=new Map();
                            const typeNodes=schemas.filter(n=>n.type==='type');
                            const schemaNodes=schemas.filter(n=>n.type==='schema');
                            groups.forEach((n,i)=>positions.set(n.id,{x:35,y:26+i*52}));
                            schemaNodes.forEach((n,i)=>positions.set(n.id,{x:220,y:26+i*28}));
                            const maxPerColumn=12;
                            typeNodes.forEach((n,i)=>{
                                const col=Math.floor(i/maxPerColumn);
                                const row=i%%maxPerColumn;
                                positions.set(n.id,{x:500+col*160,y:26+row*24});
                            });
                            const graphWidth=Math.max(980, 500 + Math.max(1, Math.ceil(typeNodes.length/maxPerColumn))*160 + 110);
                            const graphHeight=Math.max(220, 26 + Math.min(maxPerColumn, Math.max(1, typeNodes.length || 1))*24 + 30);
                            svg.setAttribute('viewBox','0 0 '+graphWidth+' '+graphHeight);svg.style.width=graphWidth+'px';svg.style.height=graphHeight+'px';
              svg.innerHTML='';
              edges.forEach(e=>{const a=positions.get(e.s),b=positions.get(e.t);if(!a||!b)return;const direction=a.x<b.x?1:-1;const line=document.createElementNS('http://www.w3.org/2000/svg','line');line.setAttribute('x1',direction>0?b.x:a.x+24);line.setAttribute('y1',b.y);line.setAttribute('x2',direction>0?a.x+24:b.x);line.setAttribute('y2',a.y);line.setAttribute('class','graph-edge');svg.appendChild(line);});
                            visible.forEach(n=>{const p=positions.get(n.id),g=document.createElementNS('http://www.w3.org/2000/svg','g');if(!p)return;const details=()=>{document.getElementById('externalSchemaInfo').innerHTML='<strong>'+n.label+'</strong><br>Kategorie: '+n.kind+(n.base?'<br>Basistyp: <code>'+n.base+'</code>':'')+(n.facets?'<br>Facets: '+n.facets:'')+(n.location?'<br>Quelle: '+n.location:'')+(n.source?'<br>Status: '+n.source:'');};g.addEventListener('click',(event)=>{event.stopPropagation();const isVisible=text.style.display!=='none';text.style.display=isVisible?'none':'inline';details();});const title=document.createElementNS('http://www.w3.org/2000/svg','title');title.textContent=n.label;g.appendChild(title);const circle=document.createElementNS('http://www.w3.org/2000/svg','circle');circle.setAttribute('cx',p.x);circle.setAttribute('cy',p.y);circle.setAttribute('r',n.type==='group'?14:n.type==='type'?9:12);circle.setAttribute('fill',n.type==='group'?'#0b5fff':n.type==='type'?'#f59e0b':'#00a878');g.appendChild(circle);const text=document.createElementNS('http://www.w3.org/2000/svg','text');text.setAttribute('x',p.x+16);text.setAttribute('y',p.y-8);text.setAttribute('class','external-graph-label');text.setAttribute('font-size','10');text.setAttribute('style','white-space:nowrap;display:none;');const label=n.label||'';const span=document.createElementNS('http://www.w3.org/2000/svg','tspan');span.setAttribute('x',p.x+16);span.setAttribute('dy','0');span.textContent=label;text.appendChild(span);g.appendChild(text);svg.appendChild(g);});
            }
            drawExternalSchemaGraph();
                        applyExternalSchemaTableFilters();
            </script>
            """.formatted(nodes, edges);
    }

    private long countByKind(List<ExternalSchemaReference> externalSchemaReferences, String kind) {
        if (externalSchemaReferences == null || externalSchemaReferences.isEmpty()) {
            return 0;
        }
        return externalSchemaReferences.stream().filter(ref -> kind.equals(ref.kind())).count();
    }

    private String formatPercentage(long count, int total) {
        return total == 0 ? "0.0 %" : String.format(Locale.ROOT, "%.1f %%", count * 100.0 / total);
    }

    private List<String> splitFacets(String facets) {
        if (facets == null || facets.isBlank() || "-".equals(facets) || "abstract".equals(facets)) return List.of();
        return Arrays.stream(facets.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.startsWith("enumeration(") ? "enumeration" : value)
            .toList();
    }

    private int enumerationSize(String facets) {
        if (facets == null) return -1;
        Matcher matcher = Pattern.compile("enumeration\\((\\d+)\\)").matcher(facets);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private String renderEnumDomainValidityHtml(Map<String, List<MappingEntry>> mappingsByConcept,
                                                TaxonomyMetadata metadata) {
        Map<String, EnumDomainValidityRow> rowsByDomain = new TreeMap<>();
        Map<String, Set<String>> taxonomyAllowedValuesByDomain = new TreeMap<>();
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            for (MappingEntry entry : conceptEntry.getValue()) {
                if (entry.enumerationDomain() == null || entry.enumerationDomain().isBlank()) {
                    continue;
                }
                EnumDomainValidityRow row = rowsByDomain.computeIfAbsent(entry.enumerationDomain(), key -> new EnumDomainValidityRow(key, new TreeSet<>(), new TreeSet<>(), new TreeSet<>()));
                row.concepts().add(conceptEntry.getKey());
                row.fields().add(entry.field());
                if (entry.allowedValues() != null) {
                    row.allowedValues().addAll(entry.allowedValues());
                }
            }
        }

        if (metadata != null && metadata.taxonomyEnumerationsByConcept() != null) {
            for (Map.Entry<String, TaxonomyEnumeration> enumEntry : metadata.taxonomyEnumerationsByConcept().entrySet()) {
                TaxonomyEnumeration taxonomyEnumeration = enumEntry.getValue();
                if (taxonomyEnumeration == null || taxonomyEnumeration.domain() == null || taxonomyEnumeration.domain().isBlank()) {
                    continue;
                }
                String domain = taxonomyEnumeration.domain().trim();
                EnumDomainValidityRow row = rowsByDomain.computeIfAbsent(domain, key -> new EnumDomainValidityRow(key, new TreeSet<>(), new TreeSet<>(), new TreeSet<>()));
                String concept = enumEntry.getKey();
                row.concepts().add(concept);
                for (MappingEntry mapping : mappingsByConcept.getOrDefault(concept, List.of())) {
                    row.fields().add(mapping.field());
                    if (mapping.allowedValues() != null) {
                        row.allowedValues().addAll(mapping.allowedValues());
                    }
                }
            }
        }

        if (metadata != null && metadata.domainMembersByDomain() != null) {
            for (Map.Entry<String, List<String>> memberEntry : metadata.domainMembersByDomain().entrySet()) {
                String domain = memberEntry.getKey();
                if (domain == null || domain.isBlank()) {
                    continue;
                }
                String normalizedDomainKey = normalizeConceptKey(domain.trim());
                Set<String> values = taxonomyAllowedValuesByDomain.computeIfAbsent(normalizedDomainKey, key -> new TreeSet<>());
                for (String member : memberEntry.getValue()) {
                    if (member != null && !member.isBlank()) {
                        values.add(toDisplayQName(member));
                    }
                }
            }
        }

        for (Map.Entry<String, EnumDomainValidityRow> rowEntry : rowsByDomain.entrySet()) {
            Set<String> taxonomyValues = taxonomyAllowedValuesByDomain.getOrDefault(normalizeConceptKey(rowEntry.getKey()), Set.of());
            rowEntry.getValue().allowedValues().addAll(taxonomyValues);
        }

        List<EnumDomainValidityRow> rows = new ArrayList<>(rowsByDomain.values());
        rows.sort(Comparator.comparingInt((EnumDomainValidityRow row) -> row.concepts().size()).reversed().thenComparing(EnumDomainValidityRow::domain));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Enum Domain Validity View</h1>")
            .append("<div class=\"toolbar\"><input id=\"enumDomainSearch\" type=\"search\" placeholder=\"Domain, Konzept oder Feld suchen...\" oninput=\"applyEnumDomainFilter()\"></div>")
            .append("<section><table class=\"layout-table\"><thead><tr><th>Domain</th><th>Konzepte</th><th>Felder</th><th>Allowed Values</th></tr></thead><tbody>");

        for (EnumDomainValidityRow row : rows) {
            String search = normalizeSearch(row.domain() + " " + String.join(" ", row.concepts()) + " " + String.join(" ", row.fields()) + " " + String.join(" ", row.allowedValues()));
            body.append("<tr class=\"enum-domain-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.domain()))
                .append("</code></td><td>")
                .append(row.concepts().size())
                .append("</td><td>")
                .append(row.fields().size())
                .append("</td><td>")
                .append(escapeHtml(row.allowedValues().isEmpty() ? "-" : limitJoined(row.allowedValues(), 6)))
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");
        String script = "<script>function normalize(t){return (t||'').toLowerCase();}function applyEnumDomainFilter(){const q=normalize(document.getElementById('enumDomainSearch').value.trim());document.querySelectorAll('.enum-domain-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}</script>";
        return renderPage("Enum Domain Validity View", body.toString(), script);
    }

    private int memberCountForDimension(HypercubeCube cube, String dimension) {
        int count = 0;
        List<String> domains = cube.domainsPerDimension().getOrDefault(dimension, List.of());
        for (String domain : domains) {
            count += cube.membersPerDomain().getOrDefault(domain, List.of()).size();
        }
        if (count == 0) {
            count = 1;
        }
        return count;
    }

    private String renderCalculationHtml(TaxonomyMetadata metadata,
                                         Map<String, List<MappingEntry>> mappingsByConcept) {
        List<LinkEdge> calcEdges = metadata.sampleEdges().stream()
            .filter(edge -> "calculation".equals(edge.layer()))
            .toList();

        Map<String, Integer> calcDegree = new TreeMap<>();
        for (LinkEdge edge : calcEdges) {
            calcDegree.merge(normalizeConceptKey(edge.source()), 1, Integer::sum);
            calcDegree.merge(normalizeConceptKey(edge.target()), 1, Integer::sum);
        }

        Set<String> conceptKeys = new TreeSet<>();
        conceptKeys.addAll(calcDegree.keySet());
        conceptKeys.addAll(metadata.formulaMentionsByConcept().keySet());

        List<CalculationImpactRow> rows = new ArrayList<>();
        for (String conceptKey : conceptKeys) {
            List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = mappedEntries.isEmpty() ? conceptKey : mappedEntries.get(0).concept();
            Set<String> fields = mappedEntries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            rows.add(new CalculationImpactRow(
                concept,
                calcDegree.getOrDefault(conceptKey, 0),
                metadata.formulaMentionsByConcept().getOrDefault(conceptKey, 0),
                fields
            ));
        }

        rows.sort(Comparator
            .comparingInt(CalculationImpactRow::calcDegree).reversed()
            .thenComparing(Comparator.comparingInt(CalculationImpactRow::formulaMentions).reversed())
            .thenComparing(CalculationImpactRow::concept));

        int topWindow = Math.min(20, rows.size());
        long nonZeroCalcInTopWindow = rows.stream().limit(topWindow).filter(row -> row.calcDegree() > 0).count();
        boolean suspiciousTopZeros = !calcEdges.isEmpty() && topWindow > 0 && nonZeroCalcInTopWindow == 0;

        StringBuilder body = new StringBuilder();
        body.append("<h1>Calculation View: Dependency und Formula-Impact</h1>")
            .append("<p class=\"lead\">Analytische Sicht auf Calculation-Kanten (Sample) und Konzeptverwendungen in Formula-Dateien. Nutze sie fuer Impact-Analysen bei Mapping-Aenderungen.</p>")
            .append("<div class=\"layout-row muted\"><strong>Calc-Degree</strong>Anzahl der Calculation-Kanten, in denen ein Konzept als Quelle oder Ziel vorkommt (im Sample). Hoeherer Wert bedeutet: staerker in Rechenbeziehungen verknuepft und potenziell hoeherer Impact bei Aenderungen.</div>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Calculation-Kanten (Sample)", calcEdges.size()))
            .append(summaryCard("Calculation-Konzepte", calcDegree.size()))
            .append(summaryCard("Formula-Konzepte", metadata.formulaMentionsByConcept().size()))
            .append(summaryCard("Impact-Kandidaten", rows.stream().filter(r -> r.calcDegree() > 0 || r.formulaMentions() > 0).count()))
            .append("</div>")
            .append(suspiciousTopZeros
                ? "<div class=\"layout-row muted\"><strong>Hinweis</strong>Obwohl Calculation-Kanten im Sample vorhanden sind, haben die ersten Treffer aktuell Calc-Degree 0. Bitte Sortierung/Filter und Datengrundlage pruefen.</div>"
                : "")
            .append("<div class=\"toolbar\"><input id=\"calcSearch\" type=\"search\" placeholder=\"Konzept, Feld oder Metrik suchen...\" oninput=\"applyCalcFilter()\"></div>")
            .append("<section><h2>Konzept-Impact</h2><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Calc-Degree</th><th>Formula-Mentions</th><th>Gemappte Felder</th></tr></thead><tbody>");

        int rowLimit = Math.min(500, rows.size());
        for (int i = 0; i < rowLimit; i++) {
            CalculationImpactRow row = rows.get(i);
            String search = normalizeSearch(row.concept() + " " + row.calcDegree() + " " + row.formulaMentions() + " " + String.join(" ", row.fields()));
            body.append("<tr class=\"calc-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(row.calcDegree())
                .append("</td><td>")
                .append(row.formulaMentions())
                .append("</td><td>")
                .append(row.fields().isEmpty() ? "-" : escapeHtml(limitJoined(row.fields(), 6)))
                .append("</td></tr>");
        }
        if (rows.size() > rowLimit) {
            body.append("<tr><td colspan=\"4\" class=\"muted\">Nur die ersten ")
                .append(rowLimit)
                .append(" Konzepte werden angezeigt. Bitte Suche nutzen, um weiter einzuschraenken.</td></tr>");
        }

        body.append("</tbody></table></section>")
            .append("<section><h2>Calculation-Kanten (Sample)</h2><div class=\"node-children\">");

        if (calcEdges.isEmpty()) {
            body.append("<div class=\"layout-row muted\">Keine Calculation-Kanten im Sample gefunden.</div>");
        } else {
            int edgeLimit = Math.min(220, calcEdges.size());
            for (int i = 0; i < edgeLimit; i++) {
                LinkEdge edge = calcEdges.get(i);
                body.append("<div class=\"layout-row\"><code>")
                    .append(escapeHtml(edge.source()))
                    .append("</code> -> <code>")
                    .append(escapeHtml(edge.target()))
                    .append("</code></div>");
            }
            if (calcEdges.size() > edgeLimit) {
                body.append("<div class=\"layout-row muted\">+ ")
                    .append(calcEdges.size() - edgeLimit)
                    .append(" weitere Calculation-Kanten im Sample.</div>");
            }
        }
        body.append("</div></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyCalcFilter(){const q=normalize(document.getElementById('calcSearch').value.trim());"
            + "document.querySelectorAll('.calc-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "</script>";

        return renderPage("Calculation View", body.toString(), script);
    }

    private String renderReferenceHtml(Map<String, List<MappingEntry>> mappingsByConcept,
                                       Map<String, List<String>> placeholdersByField,
                                       Map<String, List<String>> conceptReferences) {
        Set<String> concepts = new TreeSet<>(mappingsByConcept.keySet());
        concepts.addAll(conceptReferences.keySet());

        List<ReferenceRow> rows = new ArrayList<>();
        for (String conceptKey : concepts) {
            List<MappingEntry> entries = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = entries.isEmpty() ? conceptKey : entries.get(0).concept();
            List<String> references = conceptReferences.getOrDefault(conceptKey, List.of());

            if (references.isEmpty()) {
                continue;
            }

            Set<String> fields = entries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            Set<String> placeholders = new TreeSet<>();
            for (String field : fields) {
                List<String> values = placeholdersByField.get(field);
                if (values != null) {
                    placeholders.addAll(values);
                }
            }
            rows.add(new ReferenceRow(concept, references, fields, placeholders));
        }

        StringBuilder body = new StringBuilder();
        body.append("<h1>Reference View: ESRS Traceability</h1>")
            .append("<p class=\"lead\">Nachvollziehbarkeit von Konzepten zur Normgrundlage: pro Konzept werden die verknuepften ESRS-/Regulations-Referenzen angezeigt.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Konzepte mit Referenzen", rows.size()))
            .append(summaryCard("Referenzzeilen gesamt", rows.stream().mapToLong(row -> row.references().size()).sum()))
            .append(summaryCard("Mit Mapping-Feldern", rows.stream().filter(row -> !row.fields().isEmpty()).count()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"referenceSearch\" type=\"search\" placeholder=\"Konzept, ESRS Referenz, Feld oder Placeholder suchen...\" oninput=\"applyReferenceFilter()\"></div>")
            .append("<section><h2>Konzept -> Referenzen</h2><div class=\"concept-list\">\n");

        if (rows.isEmpty()) {
            body.append("<div class=\"layout-row muted\">Keine Konzept-Referenzen gefunden. Erzeuge ggf. zuerst output/arelle-concept-reference.csv.</div>");
        }

        for (ReferenceRow row : rows) {
            String search = normalizeSearch(
                row.concept() + " "
                    + String.join(" ", row.references()) + " "
                    + String.join(" ", row.fields()) + " "
                    + String.join(" ", row.placeholders())
            );

            body.append("<article class=\"concept-item reference-card\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><h3><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></h3><div class=\"node-meta\">")
                .append(metaCell("Referenzen", limitJoined(new TreeSet<>(row.references()), 10)))
                .append(metaCell("Felder", row.fields().isEmpty() ? "-" : limitJoined(row.fields(), 6)))
                .append(metaCell("Placeholders", row.placeholders().isEmpty() ? "-" : limitJoined(row.placeholders(), 6)))
                .append("</div></article>");
        }

        body.append("</div></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyReferenceFilter(){const q=normalize(document.getElementById('referenceSearch').value.trim());"
            + "document.querySelectorAll('.reference-card').forEach(c=>{const s=c.dataset.search||'';c.hidden=q&&!s.includes(q);});}"
            + "</script>";
        return renderPage("Reference View", body.toString(), script);
    }

    private String renderCoverageHtml(PresentationForest forest,
                                      Map<String, List<MappingEntry>> mappingsByConcept,
                                      Map<String, List<String>> placeholdersByField,
                                      TaxonomyMetadata metadata) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        List<CoverageRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            List<MappingEntry> entries = conceptEntry.getValue();
            String concept = entries.isEmpty() ? conceptEntry.getKey() : entries.get(0).concept();
            boolean hasLayout = entries.stream().anyMatch(entry -> {
                List<String> placeholders = placeholdersByField.get(entry.field());
                return placeholders != null && !placeholders.isEmpty();
            });
            boolean hasDimensions = (taxonomyDimensionsByConcept.containsKey(conceptEntry.getKey())
                && !taxonomyDimensionsByConcept.get(conceptEntry.getKey()).isEmpty())
                || entries.stream().anyMatch(TaxonomyVisualizationExporter::hasDimensions);
            boolean hasEnumeration = entries.stream().anyMatch(TaxonomyVisualizationExporter::hasEnumeration)
                || metadata.taxonomyEnumerationsByConcept().containsKey(conceptEntry.getKey());

            Set<String> fields = entries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            Set<String> placeholders = new TreeSet<>();
            for (String field : fields) {
                List<String> values = placeholdersByField.get(field);
                if (values != null) {
                    placeholders.addAll(values);
                }
            }

            rows.add(new CoverageRow(concept, fields, placeholders, hasLayout, hasDimensions, hasEnumeration));
        }

        long total = rows.size();
        long withLayout = rows.stream().filter(CoverageRow::hasLayout).count();
        long withDimensions = rows.stream().filter(CoverageRow::hasDimensions).count();
        long withEnumeration = rows.stream().filter(CoverageRow::hasEnumeration).count();
        long withoutLayout = total - withLayout;

        StringBuilder body = new StringBuilder();
        body.append("<h1>Coverage View: Mapping-Abdeckung</h1>")
            .append("<p class=\"lead\">Schnelle Vollstaendigkeitspruefung je Konzept: Ist es gemappt, im Layout platziert und mit Enumeration/Dimension angereichert?</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Gemappte Konzepte", total))
            .append(summaryCard("Mit Layout", withLayout))
            .append(summaryCard("Ohne Layout", withoutLayout))
            .append(summaryCard("Mit Enumeration", withEnumeration))
            .append(summaryCard("Mit Dimensionen", withDimensions))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"coverageSearch\" type=\"search\" placeholder=\"Konzept, Feld oder Placeholder suchen...\" oninput=\"applyCoverageFilter()\"></div>")
            .append("<section><h2>Konzeptabdeckung</h2><table class=\"layout-table\"><thead><tr><th>Konzept</th><th>Mapping</th><th>Layout</th><th>Enumeration</th><th>Dimensionen</th><th>Felder</th><th>Placeholders</th></tr></thead><tbody>");

        for (CoverageRow row : rows) {
            String search = normalizeSearch(row.concept() + " " + String.join(" ", row.fields()) + " " + String.join(" ", row.placeholders()));
            body.append("<tr class=\"coverage-row\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><td><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></td><td>")
                .append(statusPill(true, "ja"))
                .append("</td><td>")
                .append(statusPill(row.hasLayout(), row.hasLayout() ? "ja" : "nein"))
                .append("</td><td>")
                .append(statusPill(row.hasEnumeration(), row.hasEnumeration() ? "ja" : "nein"))
                .append("</td><td>")
                .append(statusPill(row.hasDimensions(), row.hasDimensions() ? "ja" : "nein"))
                .append("</td><td>")
                .append(escapeHtml(limitJoined(row.fields(), 6)))
                .append("</td><td>")
                .append(escapeHtml(row.placeholders().isEmpty() ? "-" : limitJoined(row.placeholders(), 6)))
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyCoverageFilter(){const q=normalize(document.getElementById('coverageSearch').value.trim());"
            + "document.querySelectorAll('.coverage-row').forEach(r=>{const s=r.dataset.search||'';r.hidden=q&&!s.includes(q);});}"
            + "</script>";
        return renderPage("Coverage View", body.toString(), script);
    }

    private String renderEnumerationHtml(Map<String, List<MappingEntry>> mappingsByConcept,
                                         Map<String, List<String>> placeholdersByField,
                                         TaxonomyMetadata metadata) {
        Set<String> concepts = new TreeSet<>(mappingsByConcept.keySet());
        concepts.addAll(metadata.taxonomyEnumerationsByConcept().keySet());

        List<EnumerationRow> rows = new ArrayList<>();
        for (String conceptKey : concepts) {
            List<MappingEntry> entries = mappingsByConcept.getOrDefault(conceptKey, List.of());
            String concept = entries.isEmpty() ? conceptKey : entries.get(0).concept();
            TaxonomyEnumeration taxonomyEnumeration = taxonomyEnumerationForConcept(metadata, concept);

            Set<String> mappingDomains = entries.stream()
                .map(MappingEntry::enumerationDomain)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

            Set<String> allowedValues = new TreeSet<>();
            for (MappingEntry entry : entries) {
                if (entry.allowedValues() != null) {
                    allowedValues.addAll(entry.allowedValues());
                }
            }

            if (taxonomyEnumeration == null && mappingDomains.isEmpty() && allowedValues.isEmpty()) {
                continue;
            }

            Set<String> fields = entries.stream().map(MappingEntry::field).collect(Collectors.toCollection(TreeSet::new));
            Set<String> placeholders = new TreeSet<>();
            for (String field : fields) {
                List<String> values = placeholdersByField.get(field);
                if (values != null) {
                    placeholders.addAll(values);
                }
            }

            rows.add(new EnumerationRow(
                concept,
                taxonomyEnumeration,
                mappingDomains,
                allowedValues,
                fields,
                placeholders
            ));
        }

        StringBuilder body = new StringBuilder();
        body.append("<h1>Enumeration View: Domains und Allowed Values</h1>")
            .append("<p class=\"lead\">Diese Ansicht kombiniert Mapping-Enumerationen mit Taxonomie-Hinweisen (enum2:item/set, domain, linkrole).</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Enumerations-Konzepte", rows.size()))
            .append(summaryCard("Mit Taxonomie-Hinweis", rows.stream().filter(row -> row.taxonomyEnumeration() != null).count()))
            .append(summaryCard("Mit Allowed Values", rows.stream().filter(row -> !row.allowedValues().isEmpty()).count()))
            .append("</div>")
            .append("<div class=\"toolbar\"><input id=\"enumSearch\" type=\"search\" placeholder=\"Konzept, Domain, Value oder Feld suchen...\" oninput=\"applyEnumFilter()\"></div>")
            .append("<section><h2>Enumeration-Browser</h2><div class=\"concept-list\">\n");

        for (EnumerationRow row : rows) {
            String taxonomyText = taxonomyText(row.taxonomyEnumeration());
            String search = normalizeSearch(
                row.concept() + " "
                    + String.join(" ", row.mappingDomains()) + " "
                    + String.join(" ", row.allowedValues()) + " "
                    + String.join(" ", row.fields()) + " "
                    + taxonomyText
            );

            body.append("<article class=\"concept-item enum-card\" data-search=\"")
                .append(escapeHtml(search))
                .append("\"><h3><code>")
                .append(escapeHtml(row.concept()))
                .append("</code></h3><div class=\"node-meta\">")
                .append(metaCell("Taxonomie", taxonomyText))
                .append(metaCell("Mapping-Domain(s)", row.mappingDomains().isEmpty() ? "-" : limitJoined(row.mappingDomains(), 6)))
                .append(metaCell("Allowed Values", row.allowedValues().isEmpty() ? "-" : limitJoined(row.allowedValues(), 8)))
                .append(metaCell("Felder", row.fields().isEmpty() ? "-" : limitJoined(row.fields(), 6)))
                .append(metaCell("Placeholders", row.placeholders().isEmpty() ? "-" : limitJoined(row.placeholders(), 6)))
                .append("</div></article>");
        }

        body.append("</div></section>");

        String script = "<script>"
            + "function normalize(t){return (t||'').toLowerCase();}"
            + "function applyEnumFilter(){const q=normalize(document.getElementById('enumSearch').value.trim());"
            + "document.querySelectorAll('.enum-card').forEach(c=>{const s=c.dataset.search||'';c.hidden=q&&!s.includes(q);});}"
            + "</script>";
        return renderPage("Enumeration View", body.toString(), script);
    }

    private String renderHypercubeHtml(TaxonomyMetadata metadata) {
        HypercubeMetadata hypercubeMetadata = metadata.hypercubeMetadata();
        StringBuilder body = new StringBuilder();
        body.append("<h1>Hypercube View: Dimensionale Taxonomie</h1>")
            .append("<p class=\"lead\">Sicht auf XBRL-Dimensionsbeziehungen aus den Definition-Linkbases: all/notAll, hypercube-dimension, dimension-domain, domain-member und dimension-default.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Hypercubes", hypercubeMetadata.cubes().size()))
            .append(summaryCard("Dimensionale Relationen", hypercubeMetadata.relationCount()))
            .append("</div>")
            .append("<div class=\"hypercube-legend\">")
            .append("<span class=\"legend-pill binding-all\">all: Kontext muss Hypercube enthalten</span>")
            .append("<span class=\"legend-pill binding-not-all\">notAll: Negativ-/Ausschlussbindung</span>")
            .append("</div>")
            .append("<section><h2>Hypercube-Struktur</h2><div class=\"role-list\">\n");

        if (hypercubeMetadata.cubes().isEmpty()) {
            body.append("<div class=\"layout-row muted\">Keine Hypercube-Beziehungen gefunden.</div>");
        }

        for (HypercubeCube cube : hypercubeMetadata.cubes()) {
            body.append("<details class=\"role\" open><summary><span><code>")
                .append(escapeHtml(cube.cube()))
                .append("</code></span><span class=\"role-meta\">")
                .append(cube.dimensions().size()).append(" Dimension(en), ")
                .append(cube.primaryItemsAll().size() + cube.primaryItemsNotAll().size()).append(" Primary Item Bindings</span></summary>")
                .append("<div class=\"node-children\">")
                .append(renderBindingList("Primary (all)", cube.primaryItemsAll(), "binding-all"))
                .append(renderBindingList("Primary (notAll)", cube.primaryItemsNotAll(), "binding-not-all"))
                .append("<div class=\"facet-grid\">");

            for (String dimension : cube.dimensions()) {
                body.append(renderDimensionFacet(cube, dimension));
            }

            body.append("</div></div></details>");
        }

        body.append("</div></section>");
        return renderPage("Hypercube View", body.toString(), "");
    }

    private String renderBindingList(String label, List<String> values, String cssClass) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"layout-row binding-group\"><strong>")
            .append(escapeHtml(label))
            .append("</strong><div class=\"binding-list\">");
        int limit = Math.min(30, values.size());
        for (int i = 0; i < limit; i++) {
            html.append("<span class=\"binding-pill ")
                .append(escapeHtml(cssClass))
                .append("\"><code>")
                .append(escapeHtml(values.get(i)))
                .append("</code></span>");
        }
        if (values.size() > limit) {
            html.append("<span class=\"muted\">+")
                .append(values.size() - limit)
                .append(" weitere</span>");
        }
        html.append("</div></div>");
        return html.toString();
    }

    private String renderDimensionFacet(HypercubeCube cube, String dimension) {
        StringBuilder html = new StringBuilder();
        List<String> defaults = cube.defaultsPerDimension().getOrDefault(dimension, List.of());
        List<String> domains = cube.domainsPerDimension().getOrDefault(dimension, List.of());

        html.append("<article class=\"facet-card\"><header class=\"facet-head\"><div class=\"node-title\"><code>")
            .append(escapeHtml(dimension))
            .append("</code></div><div class=\"facet-meta\">")
            .append("<span class=\"legend-pill\">Domains: ")
            .append(domains.size())
            .append("</span>")
            .append("<span class=\"legend-pill\">Defaults: ")
            .append(defaults.size())
            .append("</span></div></header>");

        if (!defaults.isEmpty()) {
            html.append("<div class=\"layout-row\"><strong>Default Member</strong><div class=\"facet-members\">");
            int defaultsLimit = Math.min(8, defaults.size());
            for (int i = 0; i < defaultsLimit; i++) {
                html.append("<span class=\"legend-pill\"><code>")
                    .append(escapeHtml(defaults.get(i)))
                    .append("</code></span>");
            }
            if (defaults.size() > defaultsLimit) {
                html.append("<span class=\"muted\">+")
                    .append(defaults.size() - defaultsLimit)
                    .append(" weitere</span>");
            }
            html.append("</div></div>");
        }

        if (domains.isEmpty()) {
            html.append("<div class=\"layout-row muted\">Keine Domain-Verknüpfung für diese Dimension gefunden.</div></article>");
            return html.toString();
        }

        for (String domain : domains) {
            List<String> members = cube.membersPerDomain().getOrDefault(domain, List.of());
            html.append("<div class=\"facet-domain\"><div class=\"facet-domain-title\"><code>")
                .append(escapeHtml(domain))
                .append("</code><span class=\"muted\">")
                .append(members.size())
                .append(" Member</span></div><div class=\"facet-members\">");
            if (members.isEmpty()) {
                html.append("<span class=\"muted\">Keine Domain-Member im Sample gefunden.</span>");
            } else {
                int limit = Math.min(24, members.size());
                for (int i = 0; i < limit; i++) {
                    html.append("<span class=\"legend-pill\"><code>")
                        .append(escapeHtml(members.get(i)))
                        .append("</code></span>");
                }
                if (members.size() > limit) {
                    html.append("<span class=\"muted\">+")
                        .append(members.size() - limit)
                        .append(" weitere</span>");
                }
            }
            html.append("</div></div>");
        }

        html.append("</article>");
        return html.toString();
    }

    private String renderTreeHtml(PresentationForest forest,
                                  TaxonomyMetadata metadata,
                                  Map<String, List<MappingEntry>> mappingsByConcept,
                                  Map<String, List<String>> placeholdersByField,
                                  LayoutSnapshot layoutSnapshot) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        long fieldsWithDimensions = countFieldsWithDimensions(mappingsByConcept, taxonomyDimensionsByConcept);
        long enumFields = mappingsByConcept.values().stream().flatMap(List::stream)
            .filter(TaxonomyVisualizationExporter::hasEnumeration).count();

        StringBuilder body = new StringBuilder();
        body.append("<h1>Tree View: Präsentationshierarchie</h1>")
            .append("<p class=\"lead\">Navigation über die ESRS-Hierarchie mit Drilldown auf Knoten-Metadaten und Mapping-Infos.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Rollen", forest.roleCount()))
            .append(summaryCard("Knoten", forest.nodeCount()))
            .append(summaryCard("Konzepte", mappingsByConcept.size()))
            .append(summaryCard("Enumeration-Felder", enumFields))
            .append(summaryCard("Felder mit Dimensionen", fieldsWithDimensions))
            .append(summaryCard("Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append("</div>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"taxonomySearch\" type=\"search\" placeholder=\"Suchen nach Rolle, Konzept, Feld oder Placeholder...\" oninput=\"applyFilters()\">")
            .append("<label class=\"filter\"><input id=\"filterMapped\" type=\"checkbox\" onchange=\"applyFilters()\"> nur gemappte Knoten</label>")
            .append("<label class=\"filter\"><input id=\"filterEnumeration\" type=\"checkbox\" onchange=\"applyFilters()\"> nur Enumerationen</label>")
            .append(fieldsWithDimensions == 0
                ? "<span class=\"muted\">Keine Dimensionen im Mapping vorhanden; der Dimensionsfilter ist ausgeblendet.</span>"
                : "<label class=\"filter\"><input id=\"filterDimensional\" type=\"checkbox\" onchange=\"applyFilters()\"> nur Dimensionen</label>")
            .append("<button type=\"button\" onclick=\"expandAll(true)\">Alles öffnen</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"expandAll(false)\">Alles schließen</button>")
            .append("</div>")
            .append("<section><h2>Präsentationshierarchie</h2><div class=\"role-list\" id=\"hierarchyRoot\">");

        for (PresentationRoleGraph role : forest.roles()) {
            body.append("<details class=\"role taxonomy-node\" open data-search=\"")
                .append(escapeHtml(normalizeSearch(role.searchText())))
                .append("\" data-has-mapping=\"")
                .append(role.hasMappedConcepts())
                .append("\" data-has-dimensions=\"")
                .append(role.hasDimensionalMappings(mappingsByConcept, taxonomyDimensionsByConcept))
                .append("\" data-has-enumeration=\"")
                .append(role.hasEnumerationMappings(mappingsByConcept, metadata))
                .append("\"><summary><span>")
                .append(escapeHtml(role.displayLabel()))
                .append("</span><span class=\"role-meta\">")
                .append(role.rootLabels().size()).append(" Wurzelknoten, ")
                .append(role.nodeCount()).append(" Knoten</span></summary><div class=\"node-children\">");

            for (String rootLabel : role.rootLabels()) {
                renderNode(body, role, rootLabel, metadata, mappingsByConcept, placeholdersByField, taxonomyDimensionsByConcept, new LinkedHashSet<>(), 0);
            }
            body.append("</div></details>");
        }

        body.append("</div></section>");

        String script = "<script>"
            + "function normalize(text){return (text||'').toLowerCase();}"
            + "function matchesNode(node, query, mappedOnly, dimensionalOnly, enumerationOnly){"
            + "const search=node.dataset.search||''; if(query&&!search.includes(query))return false;"
            + "if(mappedOnly&&node.dataset.hasMapping!=='true')return false;"
            + "if(dimensionalOnly&&node.dataset.hasDimensions!=='true')return false;"
            + "if(enumerationOnly&&node.dataset.hasEnumeration!=='true')return false; return true;}"
            + "function updateNode(node,q,m,d,e){const cc=Array.from(node.children).find(c=>c.classList&&c.classList.contains('node-children'));let cv=false;"
            + "if(cc){Array.from(cc.children).forEach(ch=>{if(ch.classList&&ch.classList.contains('taxonomy-node')){if(updateNode(ch,q,m,d,e))cv=true;}});}"
            + "const sm=matchesNode(node,q,m,d,e);const v=sm||cv;node.hidden=!v;if(!v)node.open=false;else if(q||m||d||e)node.open=true;return v;}"
            + "function applyFilters(){const q=normalize(document.getElementById('taxonomySearch').value.trim());"
            + "const m=document.getElementById('filterMapped').checked;const dc=document.getElementById('filterDimensional');"
            + "const d=dc?dc.checked:false;const e=document.getElementById('filterEnumeration').checked;"
            + "document.querySelectorAll('details.role').forEach(r=>updateNode(r,q,m,d,e));}"
            + "function expandAll(o){document.querySelectorAll('details').forEach(n=>n.open=o);}"
            + "window.addEventListener('DOMContentLoaded',applyFilters);"
            + "</script>";

        return renderPage("Tree View", body.toString(), script);
    }

    private String renderMatrixHtml(Map<String, List<MappingEntry>> mappingsByConcept,
                                    Map<String, List<String>> placeholdersByField,
                                    LayoutSnapshot layoutSnapshot) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>Matrix View: Konzepte und Mapping</h1>")
            .append("<p class=\"lead\">Analytische Sicht für Mapping, Konzeptbezug und Placeholder-Zuordnungen.</p>")
            .append("<div class=\"toolbar\"><input id=\"matrixSearch\" type=\"search\" placeholder=\"Suchen in Konzepten und Layout-Zuordnung...\" oninput=\"applyMatrixFilter()\"></div>")
            .append("<section><h2>Konzeptindex</h2><div class=\"concept-list\" id=\"conceptIndex\">");

        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            List<MappingEntry> conceptFields = conceptEntry.getValue();
            String conceptDisplay = conceptFields.isEmpty() ? conceptEntry.getKey() : conceptFields.get(0).concept();
            boolean hasLayout = conceptFields.stream()
                .map(MappingEntry::field)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(field -> placeholdersByField.containsKey(field) && !placeholdersByField.get(field).isEmpty());
            body.append("<article class=\"concept-item search-card\" data-search=\"")
                .append(escapeHtml(normalizeSearch(conceptSearchText(conceptDisplay, conceptFields, placeholdersByField))))
                .append("\"><h3><code>").append(escapeHtml(conceptDisplay)).append("</code></h3><div class=\"muted\">")
                .append(conceptFields.size()).append(" Feldzuordnung(en) ")
                .append(statusPill(hasLayout, hasLayout ? "Layout vorhanden" : "Layout fehlt"))
                .append("</div><div>");
            for (MappingEntry mappingEntry : conceptFields) {
                body.append("<div>").append(escapeHtml(mappingEntry.field())).append("</div>");
            }
            body.append("</div></article>");
        }

        body.append("</div></section><section><h2>Layout-Zuordnung</h2>")
            .append("<table class=\"layout-table\"><thead><tr><th>Placeholder</th><th>Feld</th><th>Konzept</th></tr></thead><tbody>");

        for (Map.Entry<String, String> mapping : layoutSnapshot.placeholderMappings().entrySet()) {
            List<MappingEntry> mappedEntries = entriesForField(mappingsByConcept, mapping.getValue());
            String conceptLabel = mappedEntries.isEmpty() ? "-" : mappedEntries.get(0).concept();
            body.append("<tr class=\"search-row\" data-search=\"")
                .append(escapeHtml(normalizeSearch(mapping.getKey() + " " + mapping.getValue() + " " + conceptLabel)))
                .append("\"><td><code>").append(escapeHtml(mapping.getKey())).append("</code></td><td>")
                .append(escapeHtml(mapping.getValue())).append("</td><td>")
                .append(mappedEntries.isEmpty() ? "-" : "<code>" + escapeHtml(conceptLabel) + "</code>")
                .append("</td></tr>");
        }

        body.append("</tbody></table></section>");

        String script = "<script>function normalize(t){return (t||'').toLowerCase();}"
            + "function applyMatrixFilter(){const q=normalize(document.getElementById('matrixSearch').value.trim());"
            + "document.querySelectorAll('.search-card,.search-row').forEach(el=>{const s=el.dataset.search||'';el.hidden=q&&!s.includes(q);});}"
            + "</script>";

        return renderPage("Matrix View", body.toString(), script);
    }

    private String renderGraphHtml(TaxonomyMetadata metadata,
                                   Map<String, List<MappingEntry>> mappingsByConcept) {
        StringBuilder body = new StringBuilder();
        Map<String, GraphNodeMeta> nodeMeta = buildGraphNodeMeta(metadata, mappingsByConcept);
        body.append("<h1>Graph View: Dependency Explorer</h1>")
            .append("<p class=\"lead\">Interaktive Graphansicht mit Layer-Filtern, Zoom/Pan und Nachbarschafts-Highlight per Klick.</p>")
            .append("<div class=\"layer-toolbar\" id=\"layerToggles\">");
        for (String layer : metadata.allLayers()) {
            body.append("<label class=\"filter\"><input type=\"checkbox\" class=\"layer-toggle\" value=\"")
                .append(escapeHtml(layer))
                .append("\" checked onchange=\"drawGraph()\"> ")
                .append(escapeHtml(layer))
                .append("</label>");
        }
                body.append("<input id=\"graphSearch\" type=\"search\" class=\"graph-search\" placeholder=\"Knoten suchen (Konzept, Feld, Domain...)\" oninput=\"drawGraph()\">")
                        .append("<button type=\"button\" class=\"secondary\" onclick=\"clearGraphSearch()\">Suche löschen</button>");
        body.append("<label class=\"filter\"><input type=\"checkbox\" id=\"graphShowLabels\" onchange=\"drawGraph()\"> Labels anzeigen</label>")
            .append("<label class=\"filter\"><input type=\"checkbox\" id=\"graphClusterThemes\" checked onchange=\"drawGraph()\"> nach Thema gruppieren</label>")
            .append("</div><div class=\"theme-legend\" id=\"themeLegend\"></div>")
            .append("<div class=\"graph-wrap\"><svg id=\"dependencyGraph\" class=\"graph\" viewBox=\"0 0 1800 980\"></svg></div>")
                        .append("<div class=\"node-info\" id=\"nodeInfo\">Knoten anklicken, um Details zu sehen (Thema aus Mapping-Domäne, Grad, Layer, Nachbarn).</div>")
            .append("<p class=\"muted\">Hinweis: Darstellung basiert auf einem layer-balancierten Edge-Sample zur Performance-Stabilität.</p>");

                StringBuilder edgesJson = new StringBuilder();
                edgesJson.append('[');
        for (int i = 0; i < metadata.sampleEdges().size(); i++) {
            LinkEdge edge = metadata.sampleEdges().get(i);
            if (i > 0) {
                                edgesJson.append(',');
            }
                        edgesJson.append("{s:\"").append(escapeJs(edge.source())).append("\",t:\"").append(escapeJs(edge.target())).append("\",layer:\"").append(escapeJs(edge.layer())).append("\"}");
        }
                edgesJson.append(']');

                StringBuilder nodeMetaJson = new StringBuilder();
                nodeMetaJson.append('{');
                int metaIndex = 0;
                for (Map.Entry<String, GraphNodeMeta> entry : nodeMeta.entrySet()) {
                        if (metaIndex++ > 0) {
                                nodeMetaJson.append(',');
                        }
                        nodeMetaJson.append("\"").append(escapeJs(entry.getKey())).append("\":")
                                .append("{theme:\"").append(escapeJs(entry.getValue().theme())).append("\",")
                                .append("search:\"").append(escapeJs(entry.getValue().search())).append("\",")
                                .append("domains:\"").append(escapeJs(entry.getValue().domains())).append("\"}");
                }
                nodeMetaJson.append('}');

                String script = """
                        <script>
                        const edges=%s;
                        const nodeMeta=%s;
                        const themeColorCache=new Map();
                        const distinctThemePalette=[
                            '#0B5FFF','#FF6B00','#00A878','#D7263D','#7B61FF','#1F8A70','#C1121F','#118AB2','#F4A261','#3A86FF',
                            '#8338EC','#06D6A0','#EF476F','#2B2D42','#E76F51','#2A9D8F','#264653','#B5179E','#4CC9F0','#FB8500',
                            '#43AA8B','#F94144','#277DA1','#90BE6D','#F3722C','#577590','#9D4EDD','#F72585','#4361EE','#3A0CA3',
                            '#4D908E','#BC4749','#1D3557','#E63946','#8D99AE','#A7C957','#6A4C93','#1982C4','#FF595E','#FFCA3A',
                            '#8AC926','#1982C4','#6A4C93','#FF924C','#52B788','#D00000','#00B4D8','#9B5DE5'
                        ];
                        let selectedNode='';
                        function normalize(t){return (t||'').toLowerCase();}
                        function stableHash(text){let h=2166136261;for(let i=0;i<text.length;i++){h^=text.charCodeAt(i);h=(h*16777619)>>>0;}return h>>>0;}
                        function layerRank(layer){const order=['presentation','definition','dimension','calculation','label','reference','other'];const idx=order.indexOf(layer);return idx<0?order.length:idx;}
                        function setSvgVisible(el,visible){el.style.display=visible?'':'none';}
                        function curvePath(p1,p2,bend){const mx=(p1.x+p2.x)/2,my=(p1.y+p2.y)/2;const dx=p2.x-p1.x,dy=p2.y-p1.y;const len=Math.max(1,Math.hypot(dx,dy));const nx=-dy/len,ny=dx/len;const cx=mx+nx*bend,cy=my+ny*bend;return `M ${p1.x.toFixed(2)} ${p1.y.toFixed(2)} Q ${cx.toFixed(2)} ${cy.toFixed(2)} ${p2.x.toFixed(2)} ${p2.y.toFixed(2)}`;}
                        function fallbackTheme(name){const n=(name||'');const stripped=n.includes('_')?n.substring(n.indexOf('_')+1):n;const m=stripped.match(/^[A-Z]+(?=[A-Z][a-z]|$)|^[A-Z]?[a-z]+|^[a-z]+/);return (m?m[0]:'other').toLowerCase();}
                        function buildThemeColorMap(){
                            const baseThemes=Array.from(new Set(Object.values(nodeMeta).map(m=>(m&&m.theme)?m.theme:'other'))).sort();
                            baseThemes.forEach((theme,i)=>{
                                if(i<distinctThemePalette.length){
                                    themeColorCache.set(theme,distinctThemePalette[i]);
                                    return;
                                }
                                const overflow=i-distinctThemePalette.length;
                                const hue=(overflow*137.508)%%360;
                                const sat=(overflow%%3===0)?82:((overflow%%3===1)?74:68);
                                const lig=(overflow%%2===0)?46:58;
                                themeColorCache.set(theme,`hsl(${hue} ${sat}%% ${lig}%%)`);
                            });
                        }
                        function colorForTheme(theme){
                            const key=(theme&&theme.trim())?theme:'other';
                            if(!themeColorCache.has(key)){
                                const i=themeColorCache.size;
                                if(i<distinctThemePalette.length){
                                    themeColorCache.set(key,distinctThemePalette[i]);
                                } else {
                                    const overflow=i-distinctThemePalette.length;
                                    const hue=(overflow*137.508)%%360;
                                    const sat=(overflow%%3===0)?82:((overflow%%3===1)?74:68);
                                    const lig=(overflow%%2===0)?46:58;
                                    themeColorCache.set(key,`hsl(${hue} ${sat}%% ${lig}%%)`);
                                }
                            }
                            return themeColorCache.get(key);
                        }
                        function shortName(name,max){return name.length<=max?name:(name.substring(0,max-1)+'…');}
                        function escHtml(value){return (value||'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');}
                        function metaFor(name){return nodeMeta[name]||{theme:fallbackTheme(name),search:normalize(name),domains:'-'};}
                        function setInfo(html){const el=document.getElementById('nodeInfo');if(el)el.innerHTML=html;}
                        function clearGraphSearch(){const inp=document.getElementById('graphSearch');if(inp){inp.value='';}drawGraph();}
                        function drawGraph(){
                            const svg=document.getElementById('dependencyGraph');
                            if(!svg)return;
                            svg.innerHTML='';
                            const selectedLayers=new Set(Array.from(document.querySelectorAll('.layer-toggle:checked')).map(e=>e.value));
                            const showAllLabels=!!(document.getElementById('graphShowLabels')&&document.getElementById('graphShowLabels').checked);
                            const clusterThemes=!!(document.getElementById('graphClusterThemes')&&document.getElementById('graphClusterThemes').checked);
                            const query=normalize((document.getElementById('graphSearch')||{value:''}).value.trim());
                            let layerEdges=edges.filter(e=>selectedLayers.has(e.layer));
                            if(!layerEdges.length){
                                const ns='http://www.w3.org/2000/svg';
                                const t=document.createElementNS(ns,'text');
                                t.setAttribute('x','30');t.setAttribute('y','42');t.setAttribute('fill','#5b7086');t.setAttribute('font-size','15');
                                t.textContent='Keine Kanten fuer die aktuell gewaehlten Layer.';
                                svg.appendChild(t);
                                setInfo('Keine Daten fuer die aktuelle Layer-Auswahl.');
                                const legend=document.getElementById('themeLegend');if(legend)legend.innerHTML='';
                                return;
                            }

                            const adjAll=new Map();
                            layerEdges.forEach(e=>{if(!adjAll.has(e.s))adjAll.set(e.s,new Set());if(!adjAll.has(e.t))adjAll.set(e.t,new Set());adjAll.get(e.s).add(e.t);adjAll.get(e.t).add(e.s);});

                            let focusNodes=[];
                            if(query){
                                const candidates=Array.from(adjAll.keys()).filter(n=>{const meta=metaFor(n);return normalize(meta.search).includes(query) || normalize(n).includes(query);});
                                focusNodes=candidates;
                                if(candidates.length){
                                    const keep=new Set(candidates);
                                    candidates.forEach(n=>{(adjAll.get(n)||new Set()).forEach(m=>keep.add(m));});
                                    const queryEdges=layerEdges.filter(e=>keep.has(e.s)||keep.has(e.t));
                                    if(queryEdges.length){
                                        layerEdges=queryEdges;
                                    }
                                }
                            }

                            const byLayer=new Map();
                            layerEdges.forEach(e=>{if(!byLayer.has(e.layer))byLayer.set(e.layer,[]);byLayer.get(e.layer).push(e);});
                            const sampledEdges=[];
                            const activeLayerOrder=Array.from(selectedLayers).sort();
                            const perLayerBudget=query?110:70;
                            const totalBudget=query?420:280;
                            for(const layer of activeLayerOrder){
                                const arr=byLayer.get(layer)||[];
                                for(let i=0;i<arr.length && i<perLayerBudget && sampledEdges.length<totalBudget;i++){
                                    sampledEdges.push(arr[i]);
                                }
                            }
                            const effectiveEdges=sampledEdges.length?sampledEdges:layerEdges;
                            const names=new Map();
                            effectiveEdges.forEach(e=>{if(!names.has(e.s))names.set(e.s,names.size);if(!names.has(e.t))names.set(e.t,names.size);});
                            const nodes=Array.from(names.keys());
                            const idx=new Map(nodes.map((n,i)=>[n,i]));
                            const filteredEdges=effectiveEdges.filter(e=>idx.has(e.s)&&idx.has(e.t));
                            if(!filteredEdges.length){
                                setInfo('Suche hat keine Kanten im aktuellen Layer-Sample gefunden.');
                                const legend=document.getElementById('themeLegend');if(legend)legend.innerHTML='';
                                return;
                            }

                            const ns='http://www.w3.org/2000/svg';
                            const g=document.createElementNS(ns,'g');
                            svg.appendChild(g);
                            const width=1800,height=980,pad=52;
                            let seed=1337;
                            const rand=()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;};
                            const degree=new Array(nodes.length).fill(0);
                            filteredEdges.forEach(e=>{degree[idx.get(e.s)]++;degree[idx.get(e.t)]++;});

                            const themeByNode=new Map(nodes.map(n=>[n,metaFor(n).theme]));
                            const distinctThemes=Array.from(new Set(Array.from(themeByNode.values()))).sort();
                            const anchorByTheme=new Map();
                            const ringRadius=Math.min(width,height)*0.34;
                            distinctThemes.forEach((theme,i)=>{
                                const angle=(Math.PI*2*i)/Math.max(1,distinctThemes.length)-Math.PI/2;
                                anchorByTheme.set(theme,{x:width/2 + Math.cos(angle)*ringRadius,y:height/2 + Math.sin(angle)*ringRadius*0.72});
                            });

                            const pos=nodes.map((name)=>{
                                const anchor=anchorByTheme.get(themeByNode.get(name))||{x:width/2,y:height/2};
                                const jitter=28+rand()*46;
                                const angle=rand()*Math.PI*2;
                                return {
                                    x:Math.max(pad,Math.min(width-pad,anchor.x+Math.cos(angle)*jitter)),
                                    y:Math.max(pad,Math.min(height-pad,anchor.y+Math.sin(angle)*jitter)),
                                    vx:0,
                                    vy:0
                                };
                            });

                            for(let iter=0;iter<220;iter++){
                                const cooling=1-(iter/220);
                                for(let i=0;i<pos.length;i++){
                                    for(let j=i+1;j<pos.length;j++){
                                        let dx=pos[j].x-pos[i].x,dy=pos[j].y-pos[i].y;
                                        let d2=dx*dx+dy*dy+0.01;
                                        let force=Math.min(18,2600/d2);
                                        let d=Math.sqrt(d2);
                                        let fx=(dx/d)*force,fy=(dy/d)*force;
                                        pos[i].vx-=fx;pos[i].vy-=fy;pos[j].vx+=fx;pos[j].vy+=fy;

                                        const minSep=15+Math.min(7,(Math.max(degree[i],degree[j])*0.12));
                                        if(d<minSep){
                                            const push=(minSep-d)*0.22;
                                            const px=(dx/d)*push,py=(dy/d)*push;
                                            pos[i].vx-=px;pos[i].vy-=py;pos[j].vx+=px;pos[j].vy+=py;
                                        }
                                    }
                                }
                                for(const e of filteredEdges){
                                    const si=idx.get(e.s),ti=idx.get(e.t);
                                    let dx=pos[ti].x-pos[si].x,dy=pos[ti].y-pos[si].y;
                                    let d=Math.sqrt(dx*dx+dy*dy)+0.001;
                                    const interTheme=themeByNode.get(e.s)!==themeByNode.get(e.t);
                                    let target=(interTheme?112:84)+Math.min(66,Math.abs(degree[si]-degree[ti])*1.1);
                                    let spring=(d-target)*0.011;
                                    let fx=(dx/d)*spring,fy=(dy/d)*spring;
                                    pos[si].vx+=fx;pos[si].vy+=fy;pos[ti].vx-=fx;pos[ti].vy-=fy;
                                }
                                for(let i=0;i<pos.length;i++){
                                    const nodeName=nodes[i];
                                    const nodeTheme=themeByNode.get(nodeName);
                                    const anchor=anchorByTheme.get(nodeTheme)||{x:width/2,y:height/2};
                                    const gx=(width/2-pos[i].x)*0.0012,gy=(height/2-pos[i].y)*0.0012;
                                    const tx=(anchor.x-pos[i].x)*(clusterThemes?0.0048:0.0011),ty=(anchor.y-pos[i].y)*(clusterThemes?0.0048:0.0011);
                                    pos[i].vx=(pos[i].vx+gx+tx)*((0.83-(1-cooling)*0.08));
                                    pos[i].vy=(pos[i].vy+gy+ty)*((0.83-(1-cooling)*0.08));
                                    pos[i].x=Math.max(pad,Math.min(width-pad,pos[i].x+pos[i].vx));
                                    pos[i].y=Math.max(pad,Math.min(height-pad,pos[i].y+pos[i].vy));
                                }
                            }

                            const layerColors={presentation:'#245b8f',calculation:'#be5d00',definition:'#3a7f2a',label:'#6a4c93',reference:'#00798c',dimension:'#7a6a00',other:'#6b7280'};
                            const nodeTheme=themeByNode;
                            const nodeColor=new Map(nodes.map(n=>[n,colorForTheme(nodeTheme.get(n))]));
                            const themeCounts=new Map();
                            nodeTheme.forEach(v=>themeCounts.set(v,(themeCounts.get(v)||0)+1));
                            const legend=document.getElementById('themeLegend');
                            if(legend){
                                const top=Array.from(themeCounts.entries()).sort((a,b)=>b[1]-a[1]).slice(0,10);
                                legend.innerHTML=top.map(([theme,count])=>`<span class="theme-chip"><span class="dot" style="background:${colorForTheme(theme)}"></span>${theme} (${count})</span>`).join('');
                            }

                            const adj=new Map();nodes.forEach(n=>adj.set(n,new Set()));
                            filteredEdges.forEach(e=>{adj.get(e.s).add(e.t);adj.get(e.t).add(e.s);});
                            const layerByNode=new Map(nodes.map(n=>[n,new Set()]));
                            filteredEdges.forEach(e=>{layerByNode.get(e.s).add(e.layer);layerByNode.get(e.t).add(e.layer);});
                            const labelByNode=new Map();

                            const halos=document.createElementNS(ns,'g');
                            halos.setAttribute('opacity','0.22');
                            g.appendChild(halos);
                            const nodesByTheme=new Map();
                            nodes.forEach(name=>{const theme=nodeTheme.get(name)||'other';if(!nodesByTheme.has(theme))nodesByTheme.set(theme,[]);nodesByTheme.get(theme).push(name);});
                            nodesByTheme.forEach((themeNodes,theme)=>{
                                if(themeNodes.length<3)return;
                                let cx=0,cy=0;
                                themeNodes.forEach(name=>{const p=pos[idx.get(name)];cx+=p.x;cy+=p.y;});
                                cx/=themeNodes.length;cy/=themeNodes.length;
                                let maxDx=0,maxDy=0;
                                themeNodes.forEach(name=>{const p=pos[idx.get(name)];maxDx=Math.max(maxDx,Math.abs(p.x-cx));maxDy=Math.max(maxDy,Math.abs(p.y-cy));});
                                const halo=document.createElementNS(ns,'ellipse');
                                halo.setAttribute('cx',cx);halo.setAttribute('cy',cy);
                                halo.setAttribute('rx',Math.min(260,Math.max(48,maxDx+28)));
                                halo.setAttribute('ry',Math.min(180,Math.max(34,maxDy+22)));
                                halo.setAttribute('fill',colorForTheme(theme));
                                halo.setAttribute('fill-opacity','0.12');
                                halo.setAttribute('stroke',colorForTheme(theme));
                                halo.setAttribute('stroke-opacity','0.18');
                                halo.setAttribute('stroke-width','1.2');
                                halos.appendChild(halo);
                            });

                            filteredEdges.forEach(e=>{
                                const si=idx.get(e.s),ti=idx.get(e.t);
                                const p1=pos[si],p2=pos[ti];
                                const layerOffset=(layerRank(e.layer)-3)*6;
                                const pairHash=(stableHash(e.s+'|'+e.t+'|'+e.layer)%%9)-4;
                                const themeOffset=(nodeTheme.get(e.s)===nodeTheme.get(e.t))?8:20;
                                const bend=layerOffset+pairHash*3+themeOffset;
                                const path=document.createElementNS(ns,'path');
                                path.setAttribute('d',curvePath(p1,p2,bend));
                                path.setAttribute('fill','none');
                                path.setAttribute('stroke',layerColors[e.layer]||layerColors.other);
                                path.setAttribute('stroke-width','1.35');
                                path.setAttribute('stroke-opacity','0.24');
                                path.dataset.s=e.s;path.dataset.t=e.t;
                                path.setAttribute('class','edge');
                                g.appendChild(path);
                            });

                            nodes.forEach((name,i)=>{
                                const p=pos[i];
                                const c=document.createElementNS(ns,'circle');
                                c.setAttribute('cx',p.x);c.setAttribute('cy',p.y);c.setAttribute('r','4.1');
                                c.setAttribute('fill',nodeColor.get(name));c.setAttribute('fill-opacity','0.95');
                                c.dataset.n=name;c.dataset.theme=nodeTheme.get(name);c.style.cursor='pointer';
                                const tt=document.createElementNS(ns,'title');tt.textContent=name;c.appendChild(tt);
                                c.addEventListener('click',()=>{selectedNode=(selectedNode===name)?'':name;highlight();});
                                g.appendChild(c);

                                const t=document.createElementNS(ns,'text');
                                t.setAttribute('x',p.x+7);t.setAttribute('y',p.y-5);t.setAttribute('font-size','10');
                                t.setAttribute('fill','#17324d');t.setAttribute('opacity','0.88');
                                t.dataset.n=name;t.textContent=showAllLabels?name:shortName(name,34);
                                g.appendChild(t);
                                labelByNode.set(name,t);
                            });

                            let scale=1,tx=0,ty=0,drag=false,sx=0,sy=0;
                            if(query && focusNodes.length){
                                const keptFocus=focusNodes.filter(n=>idx.has(n));
                                if(keptFocus.length){
                                    let cx=0,cy=0;
                                    keptFocus.forEach(n=>{const p=pos[idx.get(n)];cx+=p.x;cy+=p.y;});
                                    cx/=keptFocus.length;cy/=keptFocus.length;
                                    scale=1.22;
                                    tx=(width/2)-cx*scale;
                                    ty=(height/2)-cy*scale;
                                    if(!selectedNode || !idx.has(selectedNode)){
                                        selectedNode=keptFocus[0];
                                    }
                                }
                            }

                            function updateLabelVisibility(){
                                const neighbors=selectedNode?adj.get(selectedNode):null;
                                labelByNode.forEach((labelEl,name)=>{
                                    const i=idx.get(name);
                                    if(selectedNode){
                                        const near=name===selectedNode||(neighbors&&neighbors.has(name));
                                        setSvgVisible(labelEl,(near || scale>=2.1));
                                        return;
                                    }
                                    if(showAllLabels){
                                        labelEl.textContent=name;
                                        setSvgVisible(labelEl,true);
                                        return;
                                    }
                                    labelEl.textContent=shortName(name,34);
                                    if(scale>=2.0){setSvgVisible(labelEl,true);return;}
                                    if(scale>=1.55){setSvgVisible(labelEl,(i%%3===0));return;}
                                    if(scale>=1.2){setSvgVisible(labelEl,(i%%7===0));return;}
                                    setSvgVisible(labelEl,false);
                                });
                            }

                            function highlight(){
                                const neighbors=selectedNode?adj.get(selectedNode):null;
                                g.querySelectorAll('.edge').forEach(l=>{
                                    if(!selectedNode){l.setAttribute('stroke-opacity','0.24');l.setAttribute('stroke-width','1.35');return;}
                                    const hit=l.dataset.s===selectedNode||l.dataset.t===selectedNode;
                                    l.setAttribute('stroke-opacity',hit?'0.92':'0.05');
                                    l.setAttribute('stroke-width',hit?'2.2':'1.0');
                                });
                                g.querySelectorAll('circle').forEach(c=>{
                                    const n=c.dataset.n;
                                    if(!selectedNode){
                                        c.setAttribute('r','4.1');
                                        c.setAttribute('fill',nodeColor.get(n));
                                        c.setAttribute('fill-opacity','0.95');
                                        return;
                                    }
                                    const hit=n===selectedNode||(neighbors&&neighbors.has(n));
                                    c.setAttribute('r',n===selectedNode?'6.2':(hit?'4.9':'2.4'));
                                    c.setAttribute('fill',n===selectedNode?'#be5d00':(hit?nodeColor.get(n):'#c7d4e2'));
                                    c.setAttribute('fill-opacity',hit?'1':'0.55');
                                });
                                updateLabelVisibility();
                                if(!selectedNode){
                                    setInfo('Knoten anklicken, um Details zu sehen (Thema aus Mapping-Domäne, Grad, Layer, Nachbarn).');
                                    return;
                                }
                                const neigh=Array.from(neighbors||[]);
                                const layers=Array.from(layerByNode.get(selectedNode)||[]).sort();
                                const meta=metaFor(selectedNode);
                                const neighborsHtml=neigh.length?neigh.map(n=>`<div class="neighbor-item">${escHtml(n)}</div>`).join(''):'<div class="neighbor-item">-</div>';
                                setInfo(`<strong>${escHtml(selectedNode)}</strong><br><span class="muted">Thema (Domain):</span> ${escHtml(meta.theme)} &nbsp; <span class="muted">Grad:</span> ${neigh.length} &nbsp; <span class="muted">Layer:</span> ${escHtml(layers.join(', ')||'-')}<br><span class="muted">Domains:</span> ${escHtml(meta.domains||'-')}<br><span class="muted">Nachbarn:</span><div class="neighbor-list">${neighborsHtml}</div>`);
                            }

                            function apply(){
                                g.setAttribute('transform',`translate(${tx} ${ty}) scale(${scale})`);
                                updateLabelVisibility();
                            }

                            svg.onwheel=(ev)=>{ev.preventDefault();scale=Math.max(0.35,Math.min(3.6,scale*(ev.deltaY<0?1.08:0.92)));apply();};
                            svg.onmousedown=(ev)=>{drag=true;sx=ev.clientX;sy=ev.clientY;};
                            svg.onmouseup=()=>{drag=false;};
                            svg.onmouseleave=()=>{drag=false;};
                            svg.onmousemove=(ev)=>{if(!drag)return;tx+=ev.clientX-sx;ty+=ev.clientY-sy;sx=ev.clientX;sy=ev.clientY;apply();};
                            apply();
                            highlight();
                        }
                        buildThemeColorMap();
                        window.addEventListener('DOMContentLoaded',drawGraph);
                        </script>
                        """.formatted(edgesJson, nodeMetaJson);

                return renderPage("Graph View", body.toString(), script);
    }

    private String renderLayerHtml(TaxonomyMetadata metadata) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>Layer View: Linkbase-Layer und Unterelemente</h1>")
            .append("<p class=\"lead\">Übersicht pro Layer mit aufklappbaren Unterelementen (Kanten-Sample je Layer).</p>")
            .append("<table class=\"layout-table\"><thead><tr><th>Layer</th><th>Dateien</th><th>Kanten</th><th>Details</th></tr></thead><tbody>");

        Map<String, List<LinkEdge>> byLayer = sampleEdgesByLayer(metadata.sampleEdges());
        for (String layer : metadata.allLayers()) {
            List<LinkEdge> edges = byLayer.getOrDefault(layer, List.of());
            body.append("<tr><td><code>").append(escapeHtml(layer)).append("</code></td><td>")
                .append(metadata.fileCountByLayer().getOrDefault(layer, 0L)).append("</td><td>")
                .append(metadata.edgeCountByLayer().getOrDefault(layer, 0L)).append("</td><td>")
                .append("<details><summary>Unterelemente einblenden</summary><div class=\"node-children\">");
            if (edges.isEmpty()) {
                body.append("<div class=\"layout-row muted\">Keine Kanten im Sample verfügbar.</div>");
            } else {
                int limit = Math.min(80, edges.size());
                for (int i = 0; i < limit; i++) {
                    LinkEdge edge = edges.get(i);
                    body.append("<div class=\"layout-row\"><code>")
                        .append(escapeHtml(edge.source()))
                        .append("</code> ➜ <code>")
                        .append(escapeHtml(edge.target()))
                        .append("</code></div>");
                }
                if (edges.size() > limit) {
                    body.append("<div class=\"layout-row muted\">… ").append(edges.size() - limit).append(" weitere Kanten im Sample</div>");
                }
            }
            body.append("</div></details></td></tr>");
        }
        body.append("</tbody></table>")
            .append("<section><h2>Externe Verlinkungen (href, Sample)</h2><div class=\"node-children\">");
        for (String href : metadata.hrefTargets()) {
            body.append("<div class=\"layout-row\"><code>").append(escapeHtml(href)).append("</code></div>");
        }
        body.append("</div></section>");
        return renderPage("Layer View", body.toString(), "");
    }

    private String renderFlowHtml(PresentationForest forest,
                                  TaxonomyMetadata metadata,
                                  Map<String, List<MappingEntry>> mappingsByConcept,
                                  LayoutSnapshot layoutSnapshot) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>Flow View: Reporting-Journey</h1>")
            .append("<p class=\"lead\">Prozesssicht von Input über Semantik bis Disclosure.</p>")
            .append("<div class=\"flow-grid\">")
            .append(flowStep("1", "Datensammlung", "Input-Felder", entriesCount(mappingsByConcept)))
            .append(flowStep("2", "Mapping", "Gemappte Konzepte", mappingsByConcept.size()))
            .append(flowStep("3", "Navigation", "Präsentationsrollen", forest.roleCount()))
            .append(flowStep("4", "Semantik", "Calc/Def-Kanten", metadata.edgeCountByLayer().getOrDefault("calculation", 0L) + metadata.edgeCountByLayer().getOrDefault("definition", 0L)))
            .append(flowStep("5", "Disclosure", "Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append("</div>");
        return renderPage("Flow View", body.toString(), "");
    }

    private Map<String, List<LinkEdge>> sampleEdgesByLayer(List<LinkEdge> edges) {
        Map<String, List<LinkEdge>> grouped = new TreeMap<>();
        for (LinkEdge edge : edges) {
            grouped.computeIfAbsent(edge.layer(), key -> new ArrayList<>()).add(edge);
        }
        return grouped;
    }

    private Map<String, GraphNodeMeta> buildGraphNodeMeta(TaxonomyMetadata metadata,
                                                           Map<String, List<MappingEntry>> mappingsByConcept) {
        Map<String, GraphNodeMeta> meta = new TreeMap<>();
        Set<String> nodes = new TreeSet<>();
        for (LinkEdge edge : metadata.sampleEdges()) {
            nodes.add(edge.source());
            nodes.add(edge.target());
        }

        for (String node : nodes) {
            List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(normalizeConceptKey(node), List.of());
            TaxonomyEnumeration taxonomyEnumeration = taxonomyEnumerationForConcept(metadata, node);
            List<String> domains = deriveDomainThemes(mappedEntries, taxonomyEnumeration);
            String theme = domains.isEmpty() ? deriveThemeFromNodeName(node) : domains.get(0);
            String domainsJoined = domains.isEmpty() ? "-" : String.join(", ", domains);

            StringBuilder search = new StringBuilder(node.toLowerCase(Locale.ROOT));
            search.append(' ').append(theme).append(' ').append(domainsJoined.toLowerCase(Locale.ROOT));
            for (MappingEntry entry : mappedEntries) {
                if (entry.field() != null) {
                    search.append(' ').append(entry.field().toLowerCase(Locale.ROOT));
                }
                if (entry.concept() != null) {
                    search.append(' ').append(entry.concept().toLowerCase(Locale.ROOT));
                }
                if (entry.type() != null) {
                    search.append(' ').append(entry.type().toLowerCase(Locale.ROOT));
                }
                if (entry.period() != null) {
                    search.append(' ').append(entry.period().toLowerCase(Locale.ROOT));
                }
                if (entry.unit() != null) {
                    search.append(' ').append(entry.unit().toLowerCase(Locale.ROOT));
                }
            }

            meta.put(node, new GraphNodeMeta(theme, search.toString(), domainsJoined));
        }
        return meta;
    }

    private List<String> deriveDomainThemes(List<MappingEntry> mappedEntries,
                                            TaxonomyEnumeration taxonomyEnumeration) {
        Set<String> domains = new LinkedHashSet<>();
        if (mappedEntries != null) {
            for (MappingEntry entry : mappedEntries) {
                addDomainToken(domains, entry.enumerationDomain());
                if (entry.dimensions() != null) {
                    for (DimensionSelection dimension : entry.dimensions()) {
                        addDomainToken(domains, dimension.axisQname());
                        addDomainToken(domains, dimension.memberQname());
                    }
                }
            }
        }

        if (taxonomyEnumeration != null) {
            addDomainToken(domains, taxonomyEnumeration.domain());
        }

        return new ArrayList<>(domains);
    }

    private TaxonomyEnumeration taxonomyEnumerationForConcept(TaxonomyMetadata metadata, String conceptQname) {
        if (metadata == null || metadata.taxonomyEnumerationsByConcept() == null || conceptQname == null || conceptQname.isBlank()) {
            return null;
        }
        return metadata.taxonomyEnumerationsByConcept().get(normalizeConceptKey(conceptQname));
    }

    private void addDomainToken(Set<String> domains, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        String token = rawValue.trim();
        int colon = token.indexOf(':');
        if (colon >= 0 && colon < token.length() - 1) {
            token = token.substring(colon + 1);
        }
        if (token.startsWith("esrs_")) {
            token = token.substring(5);
        }
        token = token.replaceAll("[^A-Za-z0-9]+", " ").trim().toLowerCase(Locale.ROOT);
        if (token.isBlank()) {
            return;
        }
        String compact = token.replace(' ', '-');
        domains.add(compact.length() > 40 ? compact.substring(0, 40) : compact);
    }

    private String deriveThemeFromNodeName(String node) {
        if (node == null || node.isBlank()) {
            return "other";
        }
        String value = node;
        if (value.startsWith("esrs_")) {
            value = value.substring(5);
        }
        int underscore = value.indexOf('_');
        if (underscore > 0) {
            value = value.substring(0, underscore);
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return cleaned.isBlank() ? "other" : cleaned;
    }

    private String viewCard(String title, String href, String description) {
        return "<article class=\"flow-step\"><div class=\"title\">" + escapeHtml(title)
            + "</div><div class=\"muted\">" + escapeHtml(description)
            + "</div><div><a href=\"" + escapeHtml(href) + "\">Öffnen</a></div></article>";
    }

    private String dashboardCard(String title, String href, String searchTags, String group) {
        String normalizedGroup = group == null ? "other" : group.toLowerCase(Locale.ROOT);
        String normalizedSearch = normalizeSearch(title + " " + searchTags + " " + group);
        return "<article class=\"flow-step dash-card\" data-group=\"" + escapeHtml(normalizedGroup)
            + "\" data-search=\"" + escapeHtml(normalizedSearch)
            + "\"><div class=\"title\">" + escapeHtml(title)
            + "</div><div class=\"muted\">" + escapeHtml(group)
            + "</div><div><a href=\"" + escapeHtml(href) + "\">Öffnen</a></div></article>";
    }

    private String fileNameOnly(Path path) {
        return path.getFileName().toString();
    }

    private String renderPage(String title, String body, String script) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(escapeHtml(title)).append("</title>")
            .append("<style>")
            .append("body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:linear-gradient(180deg,#f4f7fb 0,#ffffff 40%);color:#17324d;}")
            .append("main{max-width:2440px;margin:0 auto;padding:26px 28px 44px;}")
            .append("h1{margin:0 0 8px;font-size:2rem;}h2{margin:0 0 12px;font-size:1.25rem;}section{margin-top:24px;}")
            .append("p.lead{margin:0 0 20px;color:#4a6278;max-width:980px;line-height:1.5;}")
            .append(".summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin:22px 0;}")
            .append(".card{background:#fff;border:1px solid #d9e3ee;border-radius:16px;padding:16px;box-shadow:0 8px 24px rgba(23,50,77,.06);}")
            .append(".card .value{font-size:1.6rem;font-weight:700;display:block;margin-bottom:4px;}")
            .append(".toolbar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:16px 0 12px;}")
            .append(".toolbar input[type=search]{flex:1;min-width:280px;border:1px solid #cfdbe8;border-radius:999px;padding:11px 14px;font-size:1rem;}")
            .append("label.filter{display:inline-flex;align-items:center;gap:8px;background:#fff;border:1px solid #d9e3ee;border-radius:999px;padding:9px 12px;}")
            .append("button{border:0;border-radius:999px;padding:10px 16px;background:#17324d;color:#fff;font-weight:600;cursor:pointer;}")
            .append("button.secondary{background:#d7e2ef;color:#17324d;}")
            .append(".role-list{display:grid;gap:14px;}details.role{background:#fff;border:1px solid #d9e3ee;border-radius:16px;padding:10px 12px;}")
            .append("details.role>summary{cursor:pointer;font-weight:700;list-style:none;display:flex;align-items:center;justify-content:space-between;gap:12px;}details.role>summary::-webkit-details-marker{display:none;}")
            .append(".role-meta{color:#5b7086;font-weight:500;font-size:.92rem;}.node-children{display:grid;gap:10px;margin-top:10px;padding-left:16px;border-left:2px solid #edf2f7;}")
            .append("details.taxonomy-node{background:#f8fbfe;border:1px solid #d9e3ee;border-radius:14px;padding:10px 12px;}")
            .append("details.taxonomy-node>summary{cursor:pointer;list-style:none;display:flex;align-items:flex-start;gap:10px;justify-content:space-between;}details.taxonomy-node>summary::-webkit-details-marker{display:none;}")
            .append(".node-title{font-weight:700;}.node-code{font-family:Consolas,monospace;color:#21527e;}.node-badge{display:inline-block;padding:2px 8px;border-radius:999px;background:#e9f1fb;color:#234462;font-size:.78rem;margin-left:8px;}")
            .append(".node-meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px 16px;font-size:.94rem;margin-top:10px;}")
            .append(".node-meta div,.concept-item,.layout-row{background:#fff;border:1px solid #e5edf5;border-radius:10px;padding:8px 10px;}")
            .append(".node-meta strong,.concept-item strong,.layout-row strong{display:block;font-size:.75rem;color:#5b7086;text-transform:uppercase;letter-spacing:.04em;margin-bottom:2px;}")
            .append(".meta-list{display:grid;gap:4px;}")
            .append(".meta-list-line{line-height:1.35;}")
            .append(".meta-list-toggle{margin-top:6px;}")
            .append(".meta-list-toggle summary{cursor:pointer;color:#1e5f99;font-weight:600;list-style:none;}")
            .append(".meta-list-toggle summary::-webkit-details-marker{display:none;}")
            .append(".meta-list-toggle summary::before{content:'+';display:inline-block;margin-right:6px;}")
            .append(".meta-list-toggle[open] summary::before{content:'-';}")
            .append(".concept-list{display:grid;gap:10px;}.concept-item h3{margin:0 0 6px;font-size:1rem;}")
            .append(".layout-table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d9e3ee;border-radius:14px;overflow:hidden;}")
            .append(".layout-table th,.layout-table td{text-align:left;padding:10px 12px;border-bottom:1px solid #edf2f7;vertical-align:top;white-space:normal;overflow-wrap:anywhere;word-break:break-word;max-width:100ch;}")
            .append(".layout-table th{background:#f3f7fb;font-size:.8rem;text-transform:uppercase;letter-spacing:.04em;color:#5b7086;}")
            .append(".graph-wrap{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;overflow:auto;}svg.graph{width:100%;min-width:1100px;height:760px;background:linear-gradient(180deg,#fbfdff,#f4f8fc);border-radius:10px;}")
            .append(".flow-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;}.flow-step{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;}")
            .append(".flow-step .title{font-weight:700;margin:4px 0;}.flow-step .count{font-size:1.3rem;font-weight:700;color:#17324d;}.flow-step .step{font-size:.75rem;color:#5b7086;text-transform:uppercase;letter-spacing:.05em;}")
            .append(".layer-toolbar{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 10px;}")
            .append(".graph-search{min-width:300px;flex:1;border:1px solid #cfdbe8;border-radius:999px;padding:9px 12px;font-size:.95rem;}")
            .append(".theme-legend{display:flex;flex-wrap:wrap;gap:8px;margin:6px 0 12px;}")
            .append(".theme-chip{display:inline-flex;align-items:center;gap:6px;background:#fff;border:1px solid #d9e3ee;border-radius:999px;padding:5px 10px;font-size:.84rem;color:#355066;}")
            .append(".theme-chip .dot{width:10px;height:10px;border-radius:999px;display:inline-block;}")
            .append(".hypercube-legend{display:flex;flex-wrap:wrap;gap:8px;margin:6px 0 14px;}")
            .append(".legend-pill{display:inline-flex;align-items:flex-start;gap:6px;background:#fff;border:1px solid #d9e3ee;border-radius:999px;padding:4px 10px;font-size:.82rem;color:#355066;max-width:100%;}")
            .append(".binding-pill{display:inline-flex;align-items:flex-start;background:#fff;border:1px solid #d9e3ee;border-radius:999px;padding:4px 8px;max-width:100%;}")
            .append(".binding-pill.binding-all,.legend-pill.binding-all{background:#e7f6ea;border-color:#9ed8aa;color:#1b5e32;}")
            .append(".binding-pill.binding-not-all,.legend-pill.binding-not-all{background:#ffefdc;border-color:#f1b775;color:#8a3c00;}")
            .append(".binding-group{display:grid;gap:8px;}")
            .append(".binding-list{display:flex;flex-wrap:wrap;gap:6px;align-items:center;}")
            .append(".facet-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:12px;}")
            .append(".facet-card{background:#f8fbfe;border:1px solid #d9e3ee;border-radius:14px;padding:10px 12px;display:grid;gap:10px;}")
            .append(".facet-head{display:flex;justify-content:space-between;align-items:flex-start;gap:8px;}")
            .append(".facet-meta{display:flex;flex-wrap:wrap;gap:6px;justify-content:flex-end;max-width:60%;}")
            .append(".facet-domain{background:#fff;border:1px solid #e5edf5;border-radius:10px;padding:8px 10px;display:grid;gap:8px;}")
            .append(".facet-domain-title{display:flex;justify-content:space-between;align-items:flex-start;gap:8px;}")
            .append(".facet-members{display:flex;flex-wrap:wrap;gap:6px;}")
            .append(".binding-pill code,.legend-pill code,.facet-domain-title code{white-space:normal;overflow-wrap:anywhere;word-break:break-word;max-width:100%;}")
            .append(".facet-domain-title code{display:block;max-width:100%;}")
            .append(".layout-row code{white-space:normal;overflow-wrap:anywhere;word-break:break-word;}")
            .append("code,.node-code{display:inline-block;max-width:100ch;white-space:normal;overflow-wrap:anywhere;word-break:break-word;vertical-align:top;}")
            .append(".flow-step .title,.flow-step .muted,.card,.node-meta div,.concept-item,.layout-row{overflow-wrap:anywhere;word-break:break-word;}")
            .append("@media (max-width: 900px){.facet-head{flex-direction:column;}.facet-meta{max-width:100%;justify-content:flex-start;}}")
            .append("@media (min-width: 2200px){.summary{grid-template-columns:repeat(auto-fit,minmax(210px,1fr));}.flow-grid{grid-template-columns:repeat(auto-fit,minmax(280px,1fr));}}")
            .append(".node-info{margin-top:10px;background:#fff;border:1px solid #d9e3ee;border-radius:10px;padding:10px 12px;line-height:1.45;color:#28445f;}")
            .append(".neighbor-list{margin-top:4px;display:grid;gap:3px;}")
            .append(".neighbor-item{font-family:Consolas,monospace;font-size:.9rem;overflow-wrap:anywhere;}")
            .append("a{color:#1e5f99;text-decoration:none;}code{background:#eef4fa;padding:2px 6px;border-radius:6px;}.muted{color:#5b7086;}")
            .append("</style></head><body><main>")
            .append(body)
            .append(script)
            .append("</main></body></html>");
        return html.toString();
    }

    private String renderHtml(PresentationForest forest,
                              TaxonomyMetadata metadata,
                              Map<String, List<MappingEntry>> mappingsByConcept,
                              Map<String, List<String>> placeholdersByField,
                              LayoutSnapshot layoutSnapshot) {
        Map<String, Set<String>> taxonomyDimensionsByConcept = buildTaxonomyDimensionsByConcept(metadata, forest);
        long fieldsWithDimensions = countFieldsWithDimensions(mappingsByConcept, taxonomyDimensionsByConcept);
        long totalDimensions = mappingsByConcept.values().stream().flatMap(List::stream)
            .mapToLong(entry -> entry.dimensions() == null ? 0 : entry.dimensions().size()).sum();
        long enumFields = mappingsByConcept.values().stream().flatMap(List::stream)
            .filter(TaxonomyVisualizationExporter::hasEnumeration).count();

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>ESRS Taxonomie-Explorer</title>")
            .append("<style>")
            .append("body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:linear-gradient(180deg,#f4f7fb 0,#ffffff 40%);color:#17324d;}")
            .append("main{max-width:2440px;margin:0 auto;padding:26px 28px 44px;}")
            .append("h1{margin:0 0 8px;font-size:2rem;}")
            .append("p.lead{margin:0 0 24px;color:#4a6278;max-width:980px;line-height:1.5;}")
            .append(".summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin:24px 0;}")
            .append(".card{background:#fff;border:1px solid #d9e3ee;border-radius:16px;padding:16px;box-shadow:0 8px 24px rgba(23,50,77,.06);}")
            .append(".card .value{font-size:1.6rem;font-weight:700;display:block;margin-bottom:4px;}")
            .append(".toolbar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:20px 0 12px;}")
            .append(".toolbar input[type=search]{flex:1;min-width:280px;border:1px solid #cfdbe8;border-radius:999px;padding:11px 14px;font-size:1rem;}")
            .append("label.filter{display:inline-flex;align-items:center;gap:8px;background:#fff;border:1px solid #d9e3ee;border-radius:999px;padding:9px 12px;}")
            .append("button{border:0;border-radius:999px;padding:10px 16px;background:#17324d;color:#fff;font-weight:600;cursor:pointer;}")
            .append("button.secondary{background:#d7e2ef;color:#17324d;}")
            .append("section{margin-top:28px;}")
            .append("section h2{margin:0 0 12px;font-size:1.25rem;}")
            .append(".role-list{display:grid;gap:14px;}")
            .append("details.role{background:#ffffff;border:1px solid #d9e3ee;border-radius:16px;padding:10px 12px;}")
            .append("details.role > summary{cursor:pointer;font-weight:700;list-style:none;display:flex;align-items:center;justify-content:space-between;gap:12px;}")
            .append("details.role > summary::-webkit-details-marker{display:none;}")
            .append(".role-meta{color:#5b7086;font-weight:500;font-size:.92rem;}")
            .append(".node-children{display:grid;gap:10px;margin-top:10px;padding-left:16px;border-left:2px solid #edf2f7;}")
            .append("details.taxonomy-node{background:#f8fbfe;border:1px solid #d9e3ee;border-radius:14px;padding:10px 12px;}")
            .append("details.taxonomy-node > summary{cursor:pointer;list-style:none;display:flex;align-items:flex-start;gap:10px;justify-content:space-between;}")
            .append("details.taxonomy-node > summary::-webkit-details-marker{display:none;}")
            .append(".node-title{font-weight:700;}")
            .append(".node-code{font-family:Consolas,monospace;color:#21527e;}")
            .append(".node-badge{display:inline-block;padding:2px 8px;border-radius:999px;background:#e9f1fb;color:#234462;font-size:.78rem;margin-left:8px;}")
            .append(".node-meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px 16px;font-size:.94rem;margin-top:10px;}")
            .append(".node-meta div,.concept-item,.layout-row{background:#fff;border:1px solid #e5edf5;border-radius:10px;padding:8px 10px;}")
            .append(".node-meta strong,.concept-item strong,.layout-row strong{display:block;font-size:.75rem;color:#5b7086;text-transform:uppercase;letter-spacing:.04em;margin-bottom:2px;}")
            .append(".pill{display:inline-block;padding:2px 8px;border-radius:999px;background:#e9f1fb;color:#234462;font-size:.8rem;margin:2px 6px 2px 0;}")
            .append(".concept-list{display:grid;gap:10px;}")
            .append(".concept-item h3{margin:0 0 6px;font-size:1rem;}")
            .append(".layout-table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d9e3ee;border-radius:14px;overflow:hidden;}")
            .append(".layout-table th,.layout-table td{text-align:left;padding:10px 12px;border-bottom:1px solid #edf2f7;vertical-align:top;white-space:normal;overflow-wrap:anywhere;word-break:break-word;max-width:100ch;}")
            .append(".layout-table th{background:#f3f7fb;font-size:.8rem;text-transform:uppercase;letter-spacing:.04em;color:#5b7086;}")
            .append(".graph-wrap{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;overflow:auto;}")
            .append("svg.graph{width:100%;min-width:860px;height:520px;background:linear-gradient(180deg,#fbfdff,#f4f8fc);border-radius:10px;}")
            .append(".flow-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;}")
            .append(".flow-step{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;position:relative;}")
            .append(".flow-step .step{font-size:.75rem;color:#5b7086;text-transform:uppercase;letter-spacing:.05em;}")
            .append(".flow-step .title{font-weight:700;margin:4px 0;}")
            .append(".flow-step .count{font-size:1.3rem;font-weight:700;color:#17324d;}")
            .append(".tabs{display:flex;flex-wrap:wrap;gap:8px;margin:18px 0 8px;}")
            .append(".tab-btn{border:1px solid #c7d7e6;background:#fff;color:#17324d;border-radius:999px;padding:8px 14px;font-weight:600;cursor:pointer;}")
            .append(".tab-btn.active{background:#17324d;color:#fff;border-color:#17324d;}")
            .append("section.viz-panel{display:none;}")
            .append("section.viz-panel.active{display:block;}")
            .append(".layer-toolbar{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 10px;}")
            .append("a{color:#1e5f99;text-decoration:none;}")
            .append("code,.node-code{display:inline-block;max-width:100ch;white-space:normal;overflow-wrap:anywhere;word-break:break-word;background:#eef4fa;padding:2px 6px;border-radius:6px;vertical-align:top;}")
            .append(".muted{color:#5b7086;}")
            .append(".flow-step .title,.flow-step .muted,.card,.node-meta div,.concept-item,.layout-row{overflow-wrap:anywhere;word-break:break-word;}")
            .append("@media (min-width: 2200px){.summary{grid-template-columns:repeat(auto-fit,minmax(210px,1fr));}.flow-grid{grid-template-columns:repeat(auto-fit,minmax(280px,1fr));}}")
            .append("</style>")
            .append("</head><body><main>")
            .append("<h1>ESRS Taxonomie-Explorer</h1>")
            .append("<p class=\"lead\">Hierarchie, Mapping, Layout-Zuordnung und Konzeptbeziehungen aus dem lokalen ESRS-Paket. Die Hierarchie kommt jetzt direkt aus den Presentation Linkbases, nicht mehr aus Feldnamen.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Präsentationsrollen", forest.roleCount()))
            .append(summaryCard("Knoten", forest.nodeCount()))
            .append(summaryCard("Felder", entriesCount(mappingsByConcept)))
            .append(summaryCard("Konzepte", mappingsByConcept.size()))
            .append(summaryCard("Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append(summaryCard("Felder mit Dimensionen", fieldsWithDimensions))
            .append(summaryCard("Dimensionen gesamt", totalDimensions))
            .append(summaryCard("Enumeration-Felder", enumFields))
            .append(summaryCard("XSD Elemente", metadata.xsdElementCount()))
            .append(summaryCard("XSD Importe/Includes", metadata.xsdImportCount()))
            .append(summaryCard("Verlinkungen (href)", metadata.hrefTargets().size()))
            .append("</div>")
            .append("<div class=\"toolbar\">")
            .append("<input id=\"taxonomySearch\" type=\"search\" placeholder=\"Suchen nach Rolle, Konzept, Feld oder Placeholder...\" oninput=\"applyFilters()\">")
            .append("<label class=\"filter\"><input id=\"filterMapped\" type=\"checkbox\" onchange=\"applyFilters()\"> nur gemappte Knoten</label>")
            .append("<label class=\"filter\"><input id=\"filterEnumeration\" type=\"checkbox\" onchange=\"applyFilters()\"> nur Enumerationen</label>")
            .append("<button type=\"button\" onclick=\"expandAll(true)\">Alles öffnen</button>")
            .append("<button type=\"button\" class=\"secondary\" onclick=\"expandAll(false)\">Alles schließen</button>")
            .append(fieldsWithDimensions == 0
                ? "<span class=\"muted\">Keine Dimensionen im Mapping vorhanden; der Dimensionsfilter ist ausgeblendet.</span>"
                : "<label class=\"filter\"><input id=\"filterDimensional\" type=\"checkbox\" onchange=\"applyFilters()\"> nur Dimensionen</label>")
            .append("</div>")
            .append("<div class=\"tabs\">")
            .append("<button type=\"button\" class=\"tab-btn active\" data-view=\"tree\" onclick=\"setView('tree')\">1. Tree</button>")
            .append("<button type=\"button\" class=\"tab-btn\" data-view=\"graph\" onclick=\"setView('graph')\">2. Graph</button>")
            .append("<button type=\"button\" class=\"tab-btn\" data-view=\"layer\" onclick=\"setView('layer')\">3. Layer</button>")
            .append("<button type=\"button\" class=\"tab-btn\" data-view=\"matrix\" onclick=\"setView('matrix')\">4. Matrix</button>")
            .append("<button type=\"button\" class=\"tab-btn\" data-view=\"flow\" onclick=\"setView('flow')\">5. Flow</button>")
            .append("</div>")
            .append("<section class=\"viz-panel active\" data-view=\"tree\"><h2>Präsentationshierarchie</h2><div class=\"role-list\" id=\"hierarchyRoot\">");

        for (PresentationRoleGraph role : forest.roles()) {
            html.append("<details class=\"role taxonomy-node\" open data-search=\"")
                .append(escapeHtml(normalizeSearch(role.searchText())))
                .append("\" data-has-mapping=\"")
                .append(role.hasMappedConcepts())
                .append("\" data-has-dimensions=\"")
                .append(role.hasDimensionalMappings(mappingsByConcept, taxonomyDimensionsByConcept))
                .append("\" data-has-enumeration=\"")
                .append(role.hasEnumerationMappings(mappingsByConcept, metadata))
                .append("\">")
                .append("<summary><span>")
                .append(escapeHtml(role.displayLabel()))
                .append("</span><span class=\"role-meta\">")
                .append(role.rootLabels().size()).append(" Wurzelknoten, ")
                .append(role.nodeCount()).append(" Knoten</span></summary>")
                .append("<div class=\"node-children\">");

            for (String rootLabel : role.rootLabels()) {
                renderNode(html, role, rootLabel, metadata, mappingsByConcept, placeholdersByField, taxonomyDimensionsByConcept, new LinkedHashSet<>(), 0);
            }

            html.append("</div></details>");
        }

        html.append("</div></section>")
            .append("<section class=\"viz-panel\" data-view=\"matrix\"><h2>Konzeptindex</h2><div class=\"concept-list\" id=\"conceptIndex\">");

        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            List<MappingEntry> conceptFields = conceptEntry.getValue();
            String conceptDisplay = conceptFields.isEmpty() ? conceptEntry.getKey() : conceptFields.get(0).concept();
            html.append("<article class=\"concept-item search-card\" data-search=\"")
                .append(escapeHtml(normalizeSearch(conceptSearchText(conceptDisplay, conceptFields, placeholdersByField))))
                .append("\" data-has-mapping=\"true\" data-has-dimensions=\"")
                .append(conceptFields.stream().anyMatch(TaxonomyVisualizationExporter::hasDimensions))
                .append("\" data-has-enumeration=\"")
                .append(conceptFields.stream().anyMatch(TaxonomyVisualizationExporter::hasEnumeration))
                .append("\">")
                .append("<h3><code>").append(escapeHtml(conceptDisplay)).append("</code></h3>")
                .append("<div class=\"muted\">")
                .append(conceptFields.size()).append(" Feldzuordnung(en)</div>")
                .append("<div>");

            for (MappingEntry mappingEntry : conceptFields) {
                html.append("<div><a href=\"#").append(fieldId(mappingEntry.field())).append("\">")
                    .append(escapeHtml(mappingEntry.field())).append("</a></div>");
            }

            html.append("</div></article>");
        }

        html.append("</div>")
            .append("<section><h2>Layout-Zuordnung</h2>")
            .append("<table class=\"layout-table\" id=\"layoutTable\"><thead><tr><th>Placeholder</th><th>Feld</th><th>Konzept</th></tr></thead><tbody>");

        for (Map.Entry<String, String> mapping : layoutSnapshot.placeholderMappings().entrySet()) {
            List<MappingEntry> mappedEntries = entriesForField(mappingsByConcept, mapping.getValue());
            String conceptLabel = mappedEntries.isEmpty() ? "-" : mappedEntries.get(0).concept();
            html.append("<tr class=\"layout-row search-row\" data-search=\"")
                .append(escapeHtml(normalizeSearch(mapping.getKey() + " " + mapping.getValue() + " " + conceptLabel)))
                .append("\" data-has-mapping=\"true\" data-has-dimensions=\"")
                .append(mappedEntries.stream().anyMatch(TaxonomyVisualizationExporter::hasDimensions))
                .append("\" data-has-enumeration=\"")
                .append(mappedEntries.stream().anyMatch(TaxonomyVisualizationExporter::hasEnumeration))
                .append("\">")
                .append("<td><code>").append(escapeHtml(mapping.getKey())).append("</code></td>")
                .append("<td><a href=\"#").append(fieldId(mapping.getValue())).append("\">")
                .append(escapeHtml(mapping.getValue())).append("</a></td>")
                .append("<td>").append(mappedEntries.isEmpty() ? "-" : "<code>" + escapeHtml(conceptLabel) + "</code>")
                .append("</td></tr>");
        }

        html.append("</tbody></table></section></section>")
            .append("<section class=\"viz-panel\" data-view=\"graph\"><h2>Graph-Ansicht (Knowledge Graph, Sample)</h2>")
            .append("<p class=\"lead\">Beziehungen aus Presentation/Calculation/Definition werden als Abhängigkeitsgraph visualisiert. Zur Performance ist dies ein repräsentatives Sample.</p>")
            .append("<div class=\"layer-toolbar\" id=\"layerToggles\">");

        for (String layer : metadata.allLayers()) {
            html.append("<label class=\"filter\"><input type=\"checkbox\" class=\"layer-toggle\" value=\"")
                .append(escapeHtml(layer))
                .append("\" checked onchange=\"drawDependencyGraph()\"> ")
                .append(escapeHtml(layer))
                .append("</label>");
        }

        html.append("</div>")
            .append("<div class=\"graph-wrap\"><svg id=\"dependencyGraph\" class=\"graph\" viewBox=\"0 0 1400 520\" role=\"img\" aria-label=\"ESRS Dependency Graph\"></svg></div>")
            .append("</section>")
            .append("<section class=\"viz-panel\" data-view=\"layer\"><h2>Linkbase-Layer View</h2>")
            .append("<p class=\"lead\">Dateien und Kanten nach Layer getrennt (Presentation, Calculation, Definition, Label, Reference, Dimensions).</p>")
            .append("<table class=\"layout-table\"><thead><tr><th>Layer</th><th>Dateien</th><th>Kanten</th></tr></thead><tbody>");

        for (String layer : metadata.allLayers()) {
            html.append("<tr><td><code>")
                .append(escapeHtml(layer))
                .append("</code></td><td>")
                .append(metadata.fileCountByLayer().getOrDefault(layer, 0L))
                .append("</td><td>")
                .append(metadata.edgeCountByLayer().getOrDefault(layer, 0L))
                .append("</td></tr>");
        }

        html.append("</tbody></table>")
            .append("<details class=\"role\"><summary>Externe/Referenz-HREFs (Sample)</summary><div class=\"node-children\">\n");

        for (String href : metadata.hrefTargets()) {
            html.append("<div class=\"layout-row\"><code>")
                .append(escapeHtml(href))
                .append("</code></div>");
        }

        html.append("</div></details></section>")
            .append("<section class=\"viz-panel\" data-view=\"flow\"><h2>Reporting-Flow View</h2>")
            .append("<p class=\"lead\">Taxonomie-Elemente entlang des Reporting-Prozesses: Datensammlung -> Mapping -> Hierarchie -> Berechnung/Semantik -> Disclosure.</p>")
            .append("<div class=\"flow-grid\">")
            .append(flowStep("1", "Datensammlung", "Input-Felder", entriesCount(mappingsByConcept)))
            .append(flowStep("2", "Mapping", "Gemappte Konzepte", mappingsByConcept.size()))
            .append(flowStep("3", "Navigation", "Präsentationsrollen", forest.roleCount()))
            .append(flowStep("4", "Semantik", "Calc/Def-Kanten", metadata.edgeCountByLayer().getOrDefault("calculation", 0L) + metadata.edgeCountByLayer().getOrDefault("definition", 0L)))
            .append(flowStep("5", "Disclosure", "Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append("</div></section>")
            .append("<script>")
            .append("function normalize(text){return (text||'').toLowerCase();}")
            .append("function matchesNode(node, query, mappedOnly, dimensionalOnly, enumerationOnly){")
            .append("  const search = node.dataset.search || '';")
            .append("  if (query && !search.includes(query)) { return false; }")
            .append("  if (mappedOnly && node.dataset.hasMapping !== 'true') { return false; }")
            .append("  if (dimensionalOnly && node.dataset.hasDimensions !== 'true') { return false; }")
            .append("  if (enumerationOnly && node.dataset.hasEnumeration !== 'true') { return false; }")
            .append("  return true;")
            .append("}")
            .append("function updateNode(node, query, mappedOnly, dimensionalOnly, enumerationOnly){")
            .append("  const childrenContainer = Array.from(node.children).find(function(child){ return child.classList && child.classList.contains('node-children'); });")
            .append("  let childVisible = false;")
            .append("  if (childrenContainer) {")
            .append("    Array.from(childrenContainer.children).forEach(function(child){")
            .append("      if (child.classList && child.classList.contains('taxonomy-node')) {")
            .append("        if (updateNode(child, query, mappedOnly, dimensionalOnly, enumerationOnly)) { childVisible = true; }")
            .append("      }")
            .append("    });")
            .append("  }")
            .append("  const selfMatches = matchesNode(node, query, mappedOnly, dimensionalOnly, enumerationOnly);")
            .append("  const visible = selfMatches || childVisible;")
            .append("  node.hidden = !visible;")
            .append("  if (!visible) { node.open = false; }")
            .append("  else if (query || mappedOnly || dimensionalOnly || enumerationOnly) { node.open = true; }")
            .append("  return visible;")
            .append("}")
            .append("function applyFilters(){")
            .append("  const query = normalize(document.getElementById('taxonomySearch').value.trim());")
            .append("  const mappedOnly = document.getElementById('filterMapped').checked;")
            .append("  const dimensionalCheckbox = document.getElementById('filterDimensional');")
            .append("  const dimensionalOnly = dimensionalCheckbox ? dimensionalCheckbox.checked : false;")
            .append("  const enumerationOnly = document.getElementById('filterEnumeration').checked;")
            .append("  document.querySelectorAll('details.role').forEach(function(role){ updateNode(role, query, mappedOnly, dimensionalOnly, enumerationOnly); });")
            .append("  document.querySelectorAll('.search-card').forEach(function(card){ card.hidden = !matchesNode(card, query, mappedOnly, dimensionalOnly, enumerationOnly); });")
            .append("  document.querySelectorAll('.search-row').forEach(function(row){ row.hidden = !matchesNode(row, query, mappedOnly, dimensionalOnly, enumerationOnly); });")
            .append("}")
            .append("function setView(view){")
            .append("  document.querySelectorAll('.viz-panel').forEach(function(panel){ panel.classList.toggle('active', panel.dataset.view===view); });")
            .append("  document.querySelectorAll('.tab-btn').forEach(function(btn){ btn.classList.toggle('active', btn.dataset.view===view); });")
            .append("  if(view==='graph'){ drawDependencyGraph(); }")
            .append("}")
            .append("function expandAll(open){ document.querySelectorAll('details').forEach(function(node){ node.open = open; }); }")
            .append("const graphEdges = [");

        for (int i = 0; i < metadata.sampleEdges().size(); i++) {
            LinkEdge edge = metadata.sampleEdges().get(i);
            if (i > 0) {
                html.append(',');
            }
            html.append("{s:\"").append(escapeJs(edge.source())).append("\",t:\"").append(escapeJs(edge.target())).append("\",layer:\"").append(escapeJs(edge.layer())).append("\"}");
        }

        html.append("];\n")
            .append("function drawDependencyGraph(){")
            .append("  const svg = document.getElementById('dependencyGraph');")
            .append("  if (!svg || !graphEdges.length) return;")
            .append("  svg.innerHTML = '';")
            .append("  const selected = new Set(Array.from(document.querySelectorAll('.layer-toggle:checked')).map(function(el){ return el.value; }));")
            .append("  const filteredEdges = graphEdges.filter(function(e){ return selected.size===0 || selected.has(e.layer); });")
            .append("  if(!filteredEdges.length) return;")
            .append("  const nodeSet = new Map();")
            .append("  filteredEdges.forEach(function(e){ if(!nodeSet.has(e.s)) nodeSet.set(e.s,nodeSet.size); if(!nodeSet.has(e.t)) nodeSet.set(e.t,nodeSet.size); });")
            .append("  const nodes = Array.from(nodeSet.keys()).slice(0, 180);")
            .append("  const byName = new Map(nodes.map(function(n,i){ return [n,i]; }));")
            .append("  const cx = 700, cy = 260, rx = 610, ry = 210;")
            .append("  const pos = nodes.map(function(_,i){ const a = (2*Math.PI*i)/nodes.length; return {x:cx+Math.cos(a)*rx,y:cy+Math.sin(a)*ry}; });")
            .append("  const ns='http://www.w3.org/2000/svg';")
            .append("  function line(x1,y1,x2,y2,c,w){ const el=document.createElementNS(ns,'line'); el.setAttribute('x1',x1); el.setAttribute('y1',y1); el.setAttribute('x2',x2); el.setAttribute('y2',y2); el.setAttribute('stroke',c); el.setAttribute('stroke-width',w); el.setAttribute('stroke-opacity','0.35'); return el; }")
            .append("  function circle(x,y,r,c){ const el=document.createElementNS(ns,'circle'); el.setAttribute('cx',x); el.setAttribute('cy',y); el.setAttribute('r',r); el.setAttribute('fill',c); return el; }")
            .append("  const colors={presentation:'#245b8f',calculation:'#be5d00',definition:'#3a7f2a',label:'#6a4c93',reference:'#00798c',dimension:'#7a6a00',other:'#6b7280'};")
            .append("  filteredEdges.forEach(function(e){ const si=byName.get(e.s); const ti=byName.get(e.t); if(si===undefined||ti===undefined) return; const p1=pos[si], p2=pos[ti]; svg.appendChild(line(p1.x,p1.y,p2.x,p2.y,colors[e.layer]||colors.other,'1.4')); });")
            .append("  pos.forEach(function(p,i){ svg.appendChild(circle(p.x,p.y,3.2,'#17324d')); if(i%12===0){ const text=document.createElementNS(ns,'text'); text.setAttribute('x',p.x+6); text.setAttribute('y',p.y-4); text.setAttribute('font-size','10'); text.setAttribute('fill','#17324d'); text.textContent=nodes[i]; svg.appendChild(text);} });")
            .append("}")
            .append("window.addEventListener('DOMContentLoaded', function(){ applyFilters(); setView('tree'); });")
            .append("</script>")
            .append("</main></body></html>");

        return html.toString();
    }

    private List<MappingEntry> entriesForField(Map<String, List<MappingEntry>> mappingsByConcept, String field) {
        List<MappingEntry> result = new ArrayList<>();
        for (List<MappingEntry> entries : mappingsByConcept.values()) {
            for (MappingEntry entry : entries) {
                if (field.equals(entry.field())) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private int entriesCount(Map<String, List<MappingEntry>> mappingsByConcept) {
        int count = 0;
        for (List<MappingEntry> entries : mappingsByConcept.values()) {
            count += entries.size();
        }
        return count;
    }

    private String conceptSearchText(String concept,
                                    List<MappingEntry> entries,
                                    Map<String, List<String>> placeholdersByField) {
        StringBuilder builder = new StringBuilder(concept);
        for (MappingEntry entry : entries) {
            builder.append(' ').append(entry.field());
            if (entry.enumerationDomain() != null) {
                builder.append(' ').append(entry.enumerationDomain());
            }
            if (entry.allowedValues() != null) {
                for (String value : entry.allowedValues()) {
                    builder.append(' ').append(value);
                }
            }
            List<String> placeholders = placeholdersByField.get(entry.field());
            if (placeholders != null) {
                for (String placeholder : placeholders) {
                    builder.append(' ').append(placeholder);
                }
            }
        }
        return builder.toString();
    }

    private void renderNode(StringBuilder html,
                            PresentationRoleGraph role,
                            String label,
                            TaxonomyMetadata metadata,
                            Map<String, List<MappingEntry>> mappingsByConcept,
                            Map<String, List<String>> placeholdersByField,
                            Map<String, Set<String>> taxonomyDimensionsByConcept,
                            Set<String> path,
                            int depth) {
        if (!path.add(label)) {
            return;
        }

        String qname = role.qname(label);
        String display = humanize(qname);
    TaxonomyEnumeration taxonomyEnumeration = taxonomyEnumerationForConcept(metadata, qname);
        List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(normalizeConceptKey(qname), List.of());
        List<String> placeholders = new ArrayList<>();
        for (MappingEntry entry : mappedEntries) {
            List<String> fieldPlaceholders = placeholdersByField.get(entry.field());
            if (fieldPlaceholders != null) {
                placeholders.addAll(fieldPlaceholders);
            }
        }

        Set<String> taxonomyDimensions = taxonomyDimensionsByConcept.getOrDefault(normalizeConceptKey(qname), Set.of());
        boolean hasDimensions = !taxonomyDimensions.isEmpty()
            || mappedEntries.stream().anyMatch(entry -> entry.dimensions() != null && !entry.dimensions().isEmpty());
        boolean hasEnumeration = taxonomyEnumeration != null
            || mappedEntries.stream().anyMatch(entry -> entry.enumerationDomain() != null && !entry.enumerationDomain().isBlank());
        String searchText = normalizeSearch(role.searchText() + " " + qname + " " + display + " " + mappedEntries.stream().map(MappingEntry::field).collect(Collectors.joining(" ")) + " " + String.join(" ", placeholders));
        String nodeId = fieldId(role.roleLabel() + "-" + qname + "-" + depth + "-" + path.hashCode());

        String conceptValue = mappedEntries.stream().map(MappingEntry::concept).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String typeValue = mappedEntries.stream().map(MappingEntry::type).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String periodValue = mappedEntries.stream().map(MappingEntry::period).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String unitValue = mappedEntries.stream().map(MappingEntry::unit).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String placeholderValue = placeholders.isEmpty() ? "" : String.join(", ", new TreeSet<>(placeholders));
        List<String> dimensions = collectDimensionEntries(mappedEntries, taxonomyDimensions);
        String dimensionsValue = renderDimensionSummary(dimensions);
        String enumerationValue = renderEnumerationSummary(mappedEntries, taxonomyEnumeration);

        html.append("<details class=\"taxonomy-node\" open data-search=\"")
            .append(escapeHtml(searchText))
            .append("\" data-has-mapping=\"")
            .append(!mappedEntries.isEmpty())
            .append("\" data-has-dimensions=\"")
            .append(hasDimensions)
            .append("\" data-has-enumeration=\"")
            .append(hasEnumeration)
            .append("\" id=\"")
            .append(nodeId)
            .append("\">")
            .append("<summary><span>")
            .append("<span class=\"node-title\">")
            .append(escapeHtml(display))
            .append("</span> <span class=\"node-code\"><code>")
            .append(escapeHtml(qname))
            .append("</code></span>")
            .append(mappedEntries.isEmpty() ? "" : "<span class=\"node-badge\">" + mappedEntries.size() + " Feldzuordnung(en)</span>")
            .append("</span></summary>")
            .append("<div class=\"node-meta\">")
            .append(metaCell("Role", role.displayLabel()))
            .append(metaCell("QName", qname))
            .append(metaCell("Kind", mappedEntries.isEmpty() ? "Taxonomie-Knoten" : "Gemappter Knoten"))
            .append(metaCellIfPresent("Placeholder", placeholderValue))
            .append(metaCellIfPresent("Konzept", conceptValue))
            .append(metaCellIfPresent("Typ", typeValue))
            .append(metaCellIfPresent("Periode", periodValue))
            .append(metaCellIfPresent("Einheit", unitValue))
            .append(metaCellIfPresentHtml("Dimensions", dimensionsValue))
            .append(metaCellIfPresent("Enumeration", enumerationValue))
            .append("</div>")
            .append("<div class=\"node-children\">");

        for (PresentationArc childArc : role.children(label)) {
            renderNode(html, role, childArc.to(), metadata, mappingsByConcept, placeholdersByField, taxonomyDimensionsByConcept, new LinkedHashSet<>(path), depth + 1);
        }

        html.append("</div></details>");
    }

    private List<String> collectDimensionEntries(List<MappingEntry> mappedEntries,
                                                 Set<String> taxonomyDimensions) {
        List<String> dimensions = new ArrayList<>();
        if (mappedEntries != null) {
            for (MappingEntry entry : mappedEntries) {
                if (entry.dimensions() != null) {
                    for (DimensionSelection dimension : entry.dimensions()) {
                        dimensions.add(dimension.axisQname() + " → " + dimension.memberQname());
                    }
                }
            }
        }
        if (taxonomyDimensions != null) {
            for (String dimension : taxonomyDimensions) {
                dimensions.add(toDisplayQName(dimension));
            }
        }
        return dimensions.stream().distinct().toList();
    }

    private String renderDimensionSummary(List<String> dimensionEntries) {
        if (dimensionEntries == null || dimensionEntries.isEmpty()) {
            return "";
        }

        final int collapsedLimit = 15;
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"meta-list\">");

        int visibleCount = Math.min(collapsedLimit, dimensionEntries.size());
        for (int i = 0; i < visibleCount; i++) {
            html.append("<div class=\"meta-list-line\"><code>")
                .append(escapeHtml(dimensionEntries.get(i)))
                .append("</code></div>");
        }

        if (dimensionEntries.size() > collapsedLimit) {
            html.append("<details class=\"meta-list-toggle\"><summary>")
                .append(dimensionEntries.size() - collapsedLimit)
                .append(" weitere anzeigen</summary>");
            for (int i = collapsedLimit; i < dimensionEntries.size(); i++) {
                html.append("<div class=\"meta-list-line\"><code>")
                    .append(escapeHtml(dimensionEntries.get(i)))
                    .append("</code></div>");
            }
            html.append("</details>");
        }

        html.append("</div>");
        return html.toString();
    }

    private String metaCellIfPresentHtml(String label, String htmlValue) {
        if (htmlValue == null || htmlValue.isBlank()) {
            return "";
        }
        return "<div><strong>" + escapeHtml(label) + "</strong>" + htmlValue + "</div>";
    }

    private String renderDimensionSummary(List<MappingEntry> mappedEntries,
                                          Set<String> taxonomyDimensions) {
        List<String> dimensions = collectDimensionEntries(mappedEntries, taxonomyDimensions);
        if (dimensions.isEmpty()) {
            return "";
        }
        return renderDimensionSummary(dimensions);
    }

    private Map<String, Set<String>> buildTaxonomyDimensionsByConcept(TaxonomyMetadata metadata,
                                                                       PresentationForest forest) {
        if (metadata == null || metadata.hypercubeMetadata() == null || metadata.hypercubeMetadata().cubes().isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> dimensionsByConcept = new TreeMap<>();
        for (HypercubeCube cube : metadata.hypercubeMetadata().cubes()) {
            if (cube == null || cube.dimensions() == null || cube.dimensions().isEmpty()) {
                continue;
            }

            Set<String> primaries = new LinkedHashSet<>();
            if (cube.primaryItemsAll() != null) {
                primaries.addAll(cube.primaryItemsAll());
            }
            if (cube.primaryItemsNotAll() != null) {
                primaries.addAll(cube.primaryItemsNotAll());
            }

            for (String primary : primaries) {
                if (primary == null || primary.isBlank()) {
                    continue;
                }
                Set<String> conceptDimensions = dimensionsByConcept.computeIfAbsent(normalizeConceptKey(primary), key -> new TreeSet<>());
                conceptDimensions.addAll(cube.dimensions());
            }
        }

        if (forest != null && !forest.roles().isEmpty()) {
            for (PresentationRoleGraph role : forest.roles()) {
                for (String rootLabel : role.rootLabels()) {
                    propagateTaxonomyDimensionsToDescendants(role, rootLabel, Set.of(), dimensionsByConcept, new LinkedHashSet<>());
                }
            }
        }

        return dimensionsByConcept;
    }

    private void propagateTaxonomyDimensionsToDescendants(PresentationRoleGraph role,
                                                          String label,
                                                          Set<String> inheritedDimensions,
                                                          Map<String, Set<String>> dimensionsByConcept,
                                                          Set<String> path) {
        if (!path.add(label)) {
            return;
        }

        String conceptKey = normalizeConceptKey(role.qname(label));
        Set<String> ownDimensions = dimensionsByConcept.getOrDefault(conceptKey, Set.of());
        Set<String> effectiveDimensions = new LinkedHashSet<>(inheritedDimensions);
        effectiveDimensions.addAll(ownDimensions);

        if (!effectiveDimensions.isEmpty()) {
            dimensionsByConcept.computeIfAbsent(conceptKey, key -> new TreeSet<>()).addAll(effectiveDimensions);
        }

        for (PresentationArc childArc : role.children(label)) {
            propagateTaxonomyDimensionsToDescendants(
                role,
                childArc.to(),
                effectiveDimensions,
                dimensionsByConcept,
                new LinkedHashSet<>(path)
            );
        }
    }

    private long countFieldsWithDimensions(Map<String, List<MappingEntry>> mappingsByConcept,
                                           Map<String, Set<String>> taxonomyDimensionsByConcept) {
        if (mappingsByConcept == null || mappingsByConcept.isEmpty()) {
            return 0;
        }

        long count = 0;
        for (Map.Entry<String, List<MappingEntry>> conceptEntry : mappingsByConcept.entrySet()) {
            boolean conceptHasTaxonomyDimensions = taxonomyDimensionsByConcept != null
                && taxonomyDimensionsByConcept.containsKey(conceptEntry.getKey())
                && !taxonomyDimensionsByConcept.get(conceptEntry.getKey()).isEmpty();
            for (MappingEntry entry : conceptEntry.getValue()) {
                if (hasDimensions(entry) || conceptHasTaxonomyDimensions) {
                    count++;
                }
            }
        }
        return count;
    }

    private String renderEnumerationSummary(List<MappingEntry> mappedEntries,
                                            TaxonomyEnumeration taxonomyEnumeration) {
        List<String> enumeration = new ArrayList<>();
        if (mappedEntries != null) {
            for (MappingEntry entry : mappedEntries) {
                if (entry.enumerationDomain() != null && !entry.enumerationDomain().isBlank()) {
                    enumeration.add(entry.enumerationDomain());
                }
                if (entry.allowedValues() != null) {
                    enumeration.addAll(entry.allowedValues());
                }
            }
        }

        if (taxonomyEnumeration != null) {
            StringBuilder taxonomyInfo = new StringBuilder(
                taxonomyEnumeration.multiValued()
                    ? "Taxonomie: Mehrfachauswahl (enum2:set)"
                    : "Taxonomie: Einzelauswahl (enum2:item)"
            );
            if (taxonomyEnumeration.domain() != null && !taxonomyEnumeration.domain().isBlank()) {
                taxonomyInfo.append(" domain=").append(taxonomyEnumeration.domain());
            }
            if (taxonomyEnumeration.linkrole() != null && !taxonomyEnumeration.linkrole().isBlank()) {
                taxonomyInfo.append(" linkrole=").append(taxonomyEnumeration.linkrole());
            }
            enumeration.add(taxonomyInfo.toString());
        }

        if (enumeration.isEmpty()) {
            return "";
        }
        return enumeration.stream().distinct().collect(Collectors.joining(", "));
    }

    private String roleLabel(Path file, String roleUri) {
        String fileName = file.getFileName().toString().replaceFirst("\\.xml$", "");
        if (roleUri == null || roleUri.isBlank()) {
            return humanize(fileName);
        }
        return humanize(fileName) + " | " + roleUri.substring(roleUri.lastIndexOf('/') + 1);
    }

    private String summaryCard(String label, long value) {
        return "<div class=\"card\"><span class=\"value\">" + value + "</span><span class=\"muted\">" + escapeHtml(label) + "</span></div>";
    }

    private String statusPill(boolean ok, String label) {
        String cssClass = ok ? "binding-all" : "binding-not-all";
        return "<span class=\"legend-pill " + cssClass + "\">" + escapeHtml(label) + "</span>";
    }

    private String limitJoined(Set<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        List<String> sorted = new ArrayList<>(values);
        int limit = Math.min(maxItems, sorted.size());
        String joined = String.join(", ", sorted.subList(0, limit));
        if (sorted.size() > limit) {
            return joined + " +" + (sorted.size() - limit) + " weitere";
        }
        return joined;
    }

    private String taxonomyText(TaxonomyEnumeration taxonomyEnumeration) {
        if (taxonomyEnumeration == null) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        text.append(taxonomyEnumeration.multiValued() ? "enum2:set" : "enum2:item");
        if (taxonomyEnumeration.domain() != null && !taxonomyEnumeration.domain().isBlank()) {
            text.append(" | domain=").append(taxonomyEnumeration.domain());
        }
        if (taxonomyEnumeration.linkrole() != null && !taxonomyEnumeration.linkrole().isBlank()) {
            text.append(" | linkrole=").append(taxonomyEnumeration.linkrole());
        }
        return text.toString();
    }

    private String metaCell(String label, String value) {
        String display = value == null || value.isBlank() ? "-" : escapeHtml(value);
        return "<div><strong>" + escapeHtml(label) + "</strong>" + display + "</div>";
    }

    private String metaCellIfPresent(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<div><strong>" + escapeHtml(label) + "</strong>" + escapeHtml(value) + "</div>";
    }

    private String flowStep(String step, String title, String subtitle, long count) {
        return "<article class=\"flow-step\"><div class=\"step\">Schritt " + escapeHtml(step)
            + "</div><div class=\"title\">" + escapeHtml(title)
            + "</div><div class=\"muted\">" + escapeHtml(subtitle)
            + "</div><div class=\"count\">" + count + "</div></article>";
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace('_', ' ');
        return cleaned.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").trim();
    }

    private String slug(String value) {
        return value == null ? "node" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private String fieldId(String field) {
        return "field-" + slug(field);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }

    private String urlFragment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replace(':', '-')
            .replaceAll("[^a-z0-9._-]", "-");
    }

    private static boolean hasDimensions(MappingEntry entry) {
        return entry.dimensions() != null && !entry.dimensions().isEmpty();
    }

    private static boolean hasEnumeration(MappingEntry entry) {
        return (entry.enumerationDomain() != null && !entry.enumerationDomain().isBlank())
            || (entry.allowedValues() != null && !entry.allowedValues().isEmpty());
    }

    private static String normalizeConceptKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(':', '_');
    }

    private static String toDisplayQName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.contains(":")) {
            return value;
        }
        int underscore = value.indexOf('_');
        if (underscore <= 0 || underscore >= value.length() - 1) {
            return value;
        }
        return value.substring(0, underscore) + ":" + value.substring(underscore + 1);
    }

    private record LayoutSnapshot(Map<String, String> placeholderMappings) {
    }

    private record LinkEdge(String source, String target, String layer) {
    }

    private record GraphNodeMeta(String theme,
                                 String search,
                                 String domains) {
    }

    private record CoverageRow(String concept,
                               Set<String> fields,
                               Set<String> placeholders,
                               boolean hasLayout,
                               boolean hasDimensions,
                               boolean hasEnumeration) {
    }

    private record EnumerationRow(String concept,
                                  TaxonomyEnumeration taxonomyEnumeration,
                                  Set<String> mappingDomains,
                                  Set<String> allowedValues,
                                  Set<String> fields,
                                  Set<String> placeholders) {
    }

    private record ReferenceRow(String concept,
                                List<String> references,
                                Set<String> fields,
                                Set<String> placeholders) {
    }

    private record CalculationImpactRow(String concept,
                                        int calcDegree,
                                        int formulaMentions,
                                        Set<String> fields) {
    }

    private record IntersectionRow(String cube,
                                   String dimensionA,
                                   String dimensionB,
                                   int membersA,
                                   int membersB,
                                   long combinationCount) {
    }

    private record FormulaRuleRow(String formulaFile,
                                  List<String> concepts,
                                  Set<String> fields) {
    }

    private record ValidationConceptRow(String concept,
                                        int mentions,
                                        Set<String> fields) {
    }

    private record AllocationRow(String section,
                                 String placeholder,
                                 String field,
                                 String concept) {
    }

    private record NodeStatsRow(String node,
                                int outDegree,
                                int inDegree,
                                int degree) {
    }

    private record ComplexityRow(String concept,
                                 int score,
                                 int dimensionCount,
                                 int enumerationSignals,
                                 int calcDegree,
                                 int formulaMentions,
                                 Set<String> fields) {
    }

    private record ImpactHeatmapRow(String concept,
                                    String section,
                                    int mappingCount,
                                    int dimensionSignals,
                                    int enumerationSignals,
                                    int placeholderCount,
                                    int score,
                                    Set<String> fields,
                                    Set<String> placeholders) {
    }

    private record HypercubeDimensionInventoryRow(String cube,
                                                  String dimension,
                                                  int domains,
                                                  int members,
                                                  int defaults,
                                                  int primaryBindings,
                                                  boolean maybeTypedAxis) {
    }

    private record MappingFlowRow(String section,
                                  String field,
                                  String concept,
                                  String period,
                                  String unit,
                                  Set<String> dimensions,
                                  Set<String> cubes,
                                  boolean enumeration) {
    }

    private record ConceptBacklogRow(String concept,
                                     int mappingCount,
                                     boolean hasLayout,
                                     int dimensionSignals,
                                     int enumerationSignals,
                                     int formulaMentions,
                                     int riskScore,
                                     Set<String> fields,
                                     Set<String> placeholders) {
    }

    private record ScopePeriodRow(String section,
                                  String field,
                                  String concept,
                                  String period,
                                  String unit,
                                  boolean hasDimensions,
                                  boolean hasEnumeration) {
    }

    private record RuleCoverageRow(String formulaFile,
                                   String concept,
                                   boolean mapped,
                                   Set<String> fields) {
    }

    private record IntersectionRiskRow(String cube,
                                       String dimensionA,
                                       String dimensionB,
                                       long combinations,
                                       long riskScore) {
    }

    private record TraceabilityMatrixRow(String concept,
                                         List<String> references,
                                         Set<String> fields,
                                         Set<String> placeholders,
                                         boolean taxonomyEnumeration) {
    }

    private record DimensionCooccurrenceRow(String dimensionA,
                                            String dimensionB,
                                            int count) {
    }

    private record DefaultMemberQualityRow(String cube,
                                           String dimension,
                                           int domains,
                                           int defaults,
                                           String status) {
    }

    private record EnumDomainValidityRow(String domain,
                                         Set<String> concepts,
                                         Set<String> fields,
                                         Set<String> allowedValues) {
    }

    private record TaxonomyMetadata(long xsdElementCount,
                                    long xsdImportCount,
                                    Map<String, Long> fileCountByLayer,
                                    Map<String, Long> edgeCountByLayer,
                                    List<LinkEdge> sampleEdges,
                                    List<String> hrefTargets,
                                    Map<String, TaxonomyEnumeration> taxonomyEnumerationsByConcept,
                                    Map<String, Integer> formulaMentionsByConcept,
                                    Map<String, List<String>> formulaConceptsByFile,
                                    HypercubeMetadata hypercubeMetadata,
                                    Map<String, List<String>> domainMembersByDomain,
                                    List<ExternalSchemaReference> externalSchemaReferences,
                                    List<ExternalSchemaType> externalSchemaTypes,
                                    List<ExternalSchemaEdge> externalSchemaEdges,
                                    List<ExternalSchemaSubstitution> externalSchemaSubstitutions) {
        private List<String> allLayers() {
            Set<String> layers = new TreeSet<>();
            layers.addAll(fileCountByLayer.keySet());
            layers.addAll(edgeCountByLayer.keySet());
            return new ArrayList<>(layers);
        }
    }

    private record TaxonomyEnumeration(boolean multiValued,
                                       String domain,
                                       String linkrole) {
    }

    private record DimensionalArc(String source,
                                  String target,
                                  String arcrole) {
    }

    private record HypercubeCube(String cube,
                                 List<String> primaryItemsAll,
                                 List<String> primaryItemsNotAll,
                                 List<String> dimensions,
                                 Map<String, List<String>> domainsPerDimension,
                                 Map<String, List<String>> defaultsPerDimension,
                                 Map<String, List<String>> membersPerDomain) {
    }

    private record HypercubeMetadata(List<HypercubeCube> cubes,
                                     int relationCount) {
    }

    private record ExternalSchemaReference(String namespace,
                                           String schemaLocation,
                                           String kind,
                                           String source) {
    }

    private record ExternalSchemaType(String namespace,
                                      String name,
                                      String category,
                                      String base,
                                      String source,
                                      String facets,
                                      String status) {
    }

    private record ExternalSchemaSubstitution(String namespace,
                                              String element,
                                              String type,
                                              String substitutionGroup,
                                              boolean abstractElement,
                                              String source) {
    }

    private record ExternalSchemaEdge(String source,
                                      String target,
                                      String relation,
                                      String location) {
    }

    private record ExternalSchemaAnalysis(List<ExternalSchemaType> types,
                                          List<ExternalSchemaEdge> edges,
                                          List<ExternalSchemaSubstitution> substitutions) {
    }

    public record VisualizationResult(Path outputPath,
                                      int presentationRoleCount,
                                      int presentationNodeCount,
                                      int fieldCount,
                                      int conceptCount,
                                      int placeholderCount) {
    }

    private record PresentationArc(String from, String to, double order) {
    }

    private static final class PresentationRoleGraph {
        private final String roleUri;
        private final String displayLabel;
        private final Map<String, String> labelToQname;
        private final Map<String, List<PresentationArc>> outgoingArcs;
        private final List<String> rootLabels;

        private PresentationRoleGraph(String roleUri,
                                      String displayLabel,
                                      Map<String, String> labelToQname,
                                      Map<String, List<PresentationArc>> outgoingArcs,
                                      List<String> rootLabels) {
            this.roleUri = roleUri;
            this.displayLabel = displayLabel;
            this.labelToQname = labelToQname;
            this.outgoingArcs = outgoingArcs;
            this.rootLabels = rootLabels;
        }

        private String displayLabel() {
            return displayLabel;
        }

        private List<String> rootLabels() {
            return rootLabels;
        }

        private List<PresentationArc> children(String label) {
            return outgoingArcs.getOrDefault(label, List.of());
        }

        private String qname(String label) {
            return labelToQname.getOrDefault(label, label);
        }

        private boolean hasMappedConcepts() {
            return !labelToQname.isEmpty();
        }

        private boolean hasDimensionalMappings(Map<String, List<MappingEntry>> mappingsByConcept,
                                               Map<String, Set<String>> taxonomyDimensionsByConcept) {
            return hasAnyDimensional(mappingsByConcept, taxonomyDimensionsByConcept, new HashSet<>(), rootLabels);
        }

        private boolean hasEnumerationMappings(Map<String, List<MappingEntry>> mappingsByConcept,
                                               TaxonomyMetadata metadata) {
            return hasAnyEnumeration(mappingsByConcept, metadata, new HashSet<>(), rootLabels);
        }

        private int nodeCount() {
            return labelToQname.size();
        }

        private String searchText() {
            return roleUri + " " + displayLabel + " " + String.join(" ", labelToQname.values());
        }

        private String roleLabel() {
            return displayLabel;
        }

        private boolean hasAnyMatchingMapping(Map<String, List<MappingEntry>> mappingsByConcept,
                                              java.util.function.Predicate<MappingEntry> predicate,
                                              Set<String> visited,
                                              List<String> labels) {
            for (String label : labels) {
                if (!visited.add(label)) {
                    continue;
                }

                String qname = qname(label);
                List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(normalizeConceptKey(qname), List.of());
                if (mappedEntries.stream().anyMatch(predicate)) {
                    return true;
                }
                if (hasAnyMatchingMapping(mappingsByConcept, predicate, visited, childLabels(label))) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasAnyEnumeration(Map<String, List<MappingEntry>> mappingsByConcept,
                                          TaxonomyMetadata metadata,
                                          Set<String> visited,
                                          List<String> labels) {
            for (String label : labels) {
                if (!visited.add(label)) {
                    continue;
                }

                String qname = qname(label);
                List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(normalizeConceptKey(qname), List.of());
                if (mappedEntries.stream().anyMatch(TaxonomyVisualizationExporter::hasEnumeration)
                    || metadata.taxonomyEnumerationsByConcept().containsKey(normalizeConceptKey(qname))) {
                    return true;
                }
                if (hasAnyEnumeration(mappingsByConcept, metadata, visited, childLabels(label))) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasAnyDimensional(Map<String, List<MappingEntry>> mappingsByConcept,
                                          Map<String, Set<String>> taxonomyDimensionsByConcept,
                                          Set<String> visited,
                                          List<String> labels) {
            for (String label : labels) {
                if (!visited.add(label)) {
                    continue;
                }

                String qname = qname(label);
                String conceptKey = normalizeConceptKey(qname);
                List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(conceptKey, List.of());
                boolean hasTaxonomyDimensions = taxonomyDimensionsByConcept != null
                    && taxonomyDimensionsByConcept.containsKey(conceptKey)
                    && !taxonomyDimensionsByConcept.get(conceptKey).isEmpty();
                if (hasTaxonomyDimensions || mappedEntries.stream().anyMatch(TaxonomyVisualizationExporter::hasDimensions)) {
                    return true;
                }
                if (hasAnyDimensional(mappingsByConcept, taxonomyDimensionsByConcept, visited, childLabels(label))) {
                    return true;
                }
            }
            return false;
        }

        private List<String> childLabels(String label) {
            return outgoingArcs.getOrDefault(label, List.of()).stream().map(PresentationArc::to).toList();
        }
    }

    private static final class PresentationForest {
        private final List<PresentationRoleGraph> roles;
        private final int nodeCount;

        private PresentationForest(List<PresentationRoleGraph> roles, int nodeCount) {
            this.roles = roles;
            this.nodeCount = nodeCount;
        }

        private List<PresentationRoleGraph> roles() {
            return roles;
        }

        private int roleCount() {
            return roles.size();
        }

        private int nodeCount() {
            return nodeCount;
        }
    }
}
