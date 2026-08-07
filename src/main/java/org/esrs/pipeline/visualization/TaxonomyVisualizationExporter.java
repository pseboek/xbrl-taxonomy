package org.esrs.pipeline.visualization;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String TAXONOMY_PATH = "xbrl.efrag.org/taxonomy/esrs/2023-12-22";
    private static final String LINK_NS = "http://www.xbrl.org/2003/linkbase";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String PARENT_CHILD_ARCROLE = "http://www.xbrl.org/2003/arcrole/parent-child";
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

        String stem = outputHtml.getFileName().toString().replaceFirst("\\.html$", "");
        Path treeHtml = outputHtml.resolveSibling(stem + "-tree.html");
        Path graphHtml = outputHtml.resolveSibling(stem + "-graph.html");
        Path layerHtml = outputHtml.resolveSibling(stem + "-layer.html");
        Path matrixHtml = outputHtml.resolveSibling(stem + "-matrix.html");
        Path flowHtml = outputHtml.resolveSibling(stem + "-flow.html");

        Files.writeString(treeHtml, renderTreeHtml(forest, metadata, mappingsByConcept, placeholdersByField, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(graphHtml, renderGraphHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(layerHtml, renderLayerHtml(metadata), StandardCharsets.UTF_8);
        Files.writeString(matrixHtml, renderMatrixHtml(mappingsByConcept, placeholdersByField, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(flowHtml, renderFlowHtml(forest, metadata, mappingsByConcept, layoutSnapshot), StandardCharsets.UTF_8);
        Files.writeString(outputHtml, renderOverviewHtml(forest, metadata, mappingsByConcept, layoutSnapshot, treeHtml, graphHtml, layerHtml, matrixHtml, flowHtml), StandardCharsets.UTF_8);

        return new VisualizationResult(
            outputHtml,
            forest.roleCount(),
            forest.nodeCount(),
            entries.size(),
            mappingsByConcept.size(),
            layoutSnapshot.placeholderMappings().size()
        );
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
            throw new IOException("Presentation linkbase directory not found: " + linkbaseDir);
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
            return new TaxonomyMetadata(0, 0, Map.of(), Map.of(), List.of(), List.of());
        }

        long xsdElementCount = 0;
        long xsdImportCount = 0;
        Map<String, Long> fileCountByLayer = new TreeMap<>();
        Map<String, Long> edgeCountByLayer = new TreeMap<>();
        List<LinkEdge> sampleEdges = new ArrayList<>();
        Set<String> hrefTargets = new TreeSet<>();

        try (Stream<Path> stream = Files.walk(taxonomyBase)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.endsWith(".xsd")) {
                    String xsdText = Files.readString(file, StandardCharsets.UTF_8);
                    xsdElementCount += countOccurrences(xsdText, "<xsd:element") + countOccurrences(xsdText, "<xs:element");
                    xsdImportCount += countOccurrences(xsdText, "<xsd:import") + countOccurrences(xsdText, "<xs:import");
                    xsdImportCount += countOccurrences(xsdText, "<xsd:include") + countOccurrences(xsdText, "<xs:include");
                    collectHrefTargetsFromText(xsdText, hrefTargets);
                    continue;
                }
                if (!fileName.endsWith(".xml")) {
                    continue;
                }

                String layer = detectLayer(fileName);
                fileCountByLayer.merge(layer, 1L, Long::sum);

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
                    if (sampleEdges.size() >= 220) {
                        continue;
                    }

                    String from = element.getAttributeNS(XLINK_NS, "from");
                    String to = element.getAttributeNS(XLINK_NS, "to");
                    String source = locators.getOrDefault(from, from);
                    String target = locators.getOrDefault(to, to);
                    if (source != null && !source.isBlank() && target != null && !target.isBlank()) {
                        sampleEdges.add(new LinkEdge(source, target, layer));
                    }
                }
            }
        }

        List<String> hrefSample = hrefTargets.stream().limit(80).toList();
        return new TaxonomyMetadata(
            xsdElementCount,
            xsdImportCount,
            fileCountByLayer,
            edgeCountByLayer,
            sampleEdges,
            hrefSample
        );
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
                                      Path flowHtml) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>ESRS Taxonomie-Visualisierungen</h1>")
            .append("<p class=\"lead\">Die Visualisierung wurde in getrennte Ansichten aufgeteilt, damit jede Seite kleiner, schneller und gezielter nutzbar ist.</p>")
            .append("<div class=\"summary\">")
            .append(summaryCard("Präsentationsrollen", forest.roleCount()))
            .append(summaryCard("Knoten", forest.nodeCount()))
            .append(summaryCard("Konzepte", mappingsByConcept.size()))
            .append(summaryCard("Layout-Placeholders", layoutSnapshot.placeholderMappings().size()))
            .append(summaryCard("XSD Elemente", metadata.xsdElementCount()))
            .append(summaryCard("Verlinkungen (href)", metadata.hrefTargets().size()))
            .append("</div>")
            .append("<section><h2>Ansichten</h2><div class=\"flow-grid\">")
            .append(viewCard("1. Tree", fileNameOnly(treeHtml), "Hierarchie + Drilldown + Mapping-Meta"))
            .append(viewCard("2. Graph", fileNameOnly(graphHtml), "Interaktiver Dependency-Graph mit Layer-Toggles"))
            .append(viewCard("3. Layer", fileNameOnly(layerHtml), "Layer-Übersicht mit aufklappbaren Unterelementen"))
            .append(viewCard("4. Matrix", fileNameOnly(matrixHtml), "Konzeptindex und Layout-Zuordnung"))
            .append(viewCard("5. Flow", fileNameOnly(flowHtml), "Reporting-Flow von Input bis Disclosure"))
            .append("</div></section>");
        return renderPage("ESRS Taxonomie-Visualisierungen", body.toString(), "");
    }

    private String renderTreeHtml(PresentationForest forest,
                                  TaxonomyMetadata metadata,
                                  Map<String, List<MappingEntry>> mappingsByConcept,
                                  Map<String, List<String>> placeholdersByField,
                                  LayoutSnapshot layoutSnapshot) {
        long fieldsWithDimensions = mappingsByConcept.values().stream().flatMap(List::stream)
            .filter(TaxonomyVisualizationExporter::hasDimensions).count();
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
                .append(role.hasDimensionalMappings(mappingsByConcept))
                .append("\" data-has-enumeration=\"")
                .append(role.hasEnumerationMappings(mappingsByConcept))
                .append("\"><summary><span>")
                .append(escapeHtml(role.displayLabel()))
                .append("</span><span class=\"role-meta\">")
                .append(role.rootLabels().size()).append(" Wurzelknoten, ")
                .append(role.nodeCount()).append(" Knoten</span></summary><div class=\"node-children\">");

            for (String rootLabel : role.rootLabels()) {
                renderNode(body, role, rootLabel, mappingsByConcept, placeholdersByField, new LinkedHashSet<>(), 0);
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
            body.append("<article class=\"concept-item search-card\" data-search=\"")
                .append(escapeHtml(normalizeSearch(conceptSearchText(conceptDisplay, conceptFields, placeholdersByField))))
                .append("\"><h3><code>").append(escapeHtml(conceptDisplay)).append("</code></h3><div class=\"muted\">")
                .append(conceptFields.size()).append(" Feldzuordnung(en)</div><div>");
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

    private String renderGraphHtml(TaxonomyMetadata metadata) {
        StringBuilder body = new StringBuilder();
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
        body.append("</div><div class=\"graph-wrap\"><svg id=\"dependencyGraph\" class=\"graph\" viewBox=\"0 0 1400 620\"></svg></div>")
            .append("<p class=\"muted\">Hinweis: Darstellung basiert auf einem Edge-Sample zur Performance-Stabilität.</p>");

        StringBuilder script = new StringBuilder();
        script.append("<script>const edges=[");
        for (int i = 0; i < metadata.sampleEdges().size(); i++) {
            LinkEdge edge = metadata.sampleEdges().get(i);
            if (i > 0) {
                script.append(',');
            }
            script.append("{s:\"").append(escapeJs(edge.source())).append("\",t:\"").append(escapeJs(edge.target())).append("\",layer:\"").append(escapeJs(edge.layer())).append("\"}");
        }
        script.append("];let selectedNode='';function drawGraph(){const svg=document.getElementById('dependencyGraph');if(!svg)return;svg.innerHTML='';"
            + "const selected=new Set(Array.from(document.querySelectorAll('.layer-toggle:checked')).map(e=>e.value));"
            + "const filtered=edges.filter(e=>selected.size===0||selected.has(e.layer));if(!filtered.length)return;"
            + "const names=new Map();filtered.forEach(e=>{if(!names.has(e.s))names.set(e.s,names.size);if(!names.has(e.t))names.set(e.t,names.size);});"
            + "const nodes=Array.from(names.keys()).slice(0,220);const idx=new Map(nodes.map((n,i)=>[n,i]));"
            + "const filtered2=filtered.filter(e=>idx.has(e.s)&&idx.has(e.t));const ns='http://www.w3.org/2000/svg';"
            + "const g=document.createElementNS(ns,'g');svg.appendChild(g);"
            + "const pos=nodes.map((_,i)=>{const a=(2*Math.PI*i)/nodes.length;return{x:700+Math.cos(a)*620,y:310+Math.sin(a)*250};});"
            + "const colors={presentation:'#245b8f',calculation:'#be5d00',definition:'#3a7f2a',label:'#6a4c93',reference:'#00798c',dimension:'#7a6a00',other:'#6b7280'};"
            + "const adj=new Map();nodes.forEach(n=>adj.set(n,new Set()));filtered2.forEach(e=>{adj.get(e.s).add(e.t);adj.get(e.t).add(e.s);});"
            + "filtered2.forEach(e=>{const si=idx.get(e.s),ti=idx.get(e.t);const p1=pos[si],p2=pos[ti];const l=document.createElementNS(ns,'line');l.setAttribute('x1',p1.x);l.setAttribute('y1',p1.y);l.setAttribute('x2',p2.x);l.setAttribute('y2',p2.y);l.setAttribute('stroke',colors[e.layer]||colors.other);l.setAttribute('stroke-width','1.4');l.setAttribute('stroke-opacity','0.32');l.dataset.s=e.s;l.dataset.t=e.t;g.appendChild(l);});"
            + "nodes.forEach((name,i)=>{const p=pos[i];const c=document.createElementNS(ns,'circle');c.setAttribute('cx',p.x);c.setAttribute('cy',p.y);c.setAttribute('r','3.4');c.setAttribute('fill','#17324d');c.dataset.n=name;c.style.cursor='pointer';c.addEventListener('click',()=>{selectedNode=(selectedNode===name)?'':name;highlight();});g.appendChild(c);if(i%14===0){const t=document.createElementNS(ns,'text');t.setAttribute('x',p.x+6);t.setAttribute('y',p.y-4);t.setAttribute('font-size','10');t.setAttribute('fill','#17324d');t.textContent=name;g.appendChild(t);}});"
            + "function highlight(){const neighbors=selectedNode?adj.get(selectedNode):null;g.querySelectorAll('line').forEach(l=>{if(!selectedNode){l.setAttribute('stroke-opacity','0.32');l.setAttribute('stroke-width','1.4');return;}const hit=l.dataset.s===selectedNode||l.dataset.t===selectedNode||(neighbors&&neighbors.has(l.dataset.s)&&neighbors.has(l.dataset.t));l.setAttribute('stroke-opacity',hit?'0.86':'0.08');l.setAttribute('stroke-width',hit?'2.2':'1');});"
            + "g.querySelectorAll('circle').forEach(c=>{if(!selectedNode){c.setAttribute('r','3.4');c.setAttribute('fill','#17324d');return;}const n=c.dataset.n;const hit=n===selectedNode||(neighbors&&neighbors.has(n));c.setAttribute('r',hit?'5.2':'2.5');c.setAttribute('fill',n===selectedNode?'#be5d00':(hit?'#245b8f':'#9fb2c4'));});}"
            + "let scale=1,tx=0,ty=0,drag=false,sx=0,sy=0;function apply(){g.setAttribute('transform',`translate(${tx} ${ty}) scale(${scale})`);}"
            + "svg.onwheel=(ev)=>{ev.preventDefault();scale=Math.max(0.35,Math.min(3.4,scale*(ev.deltaY<0?1.08:0.92)));apply();};"
            + "svg.onmousedown=(ev)=>{drag=true;sx=ev.clientX;sy=ev.clientY;};svg.onmouseup=()=>{drag=false;};svg.onmouseleave=()=>{drag=false;};"
            + "svg.onmousemove=(ev)=>{if(!drag)return;tx+=ev.clientX-sx;ty+=ev.clientY-sy;sx=ev.clientX;sy=ev.clientY;apply();};apply();highlight();}"
            + "window.addEventListener('DOMContentLoaded',drawGraph);</script>");
        return renderPage("Graph View", body.toString(), script.toString());
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

    private String viewCard(String title, String href, String description) {
        return "<article class=\"flow-step\"><div class=\"title\">" + escapeHtml(title)
            + "</div><div class=\"muted\">" + escapeHtml(description)
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
            .append("main{max-width:1480px;margin:0 auto;padding:32px 24px 48px;}")
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
            .append(".concept-list{display:grid;gap:10px;}.concept-item h3{margin:0 0 6px;font-size:1rem;}")
            .append(".layout-table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d9e3ee;border-radius:14px;overflow:hidden;}")
            .append(".layout-table th,.layout-table td{text-align:left;padding:10px 12px;border-bottom:1px solid #edf2f7;vertical-align:top;}")
            .append(".layout-table th{background:#f3f7fb;font-size:.8rem;text-transform:uppercase;letter-spacing:.04em;color:#5b7086;}")
            .append(".graph-wrap{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;overflow:auto;}svg.graph{width:100%;min-width:860px;height:620px;background:linear-gradient(180deg,#fbfdff,#f4f8fc);border-radius:10px;}")
            .append(".flow-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;}.flow-step{background:#fff;border:1px solid #d9e3ee;border-radius:14px;padding:12px;}")
            .append(".flow-step .title{font-weight:700;margin:4px 0;}.flow-step .count{font-size:1.3rem;font-weight:700;color:#17324d;}.flow-step .step{font-size:.75rem;color:#5b7086;text-transform:uppercase;letter-spacing:.05em;}")
            .append(".layer-toolbar{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 10px;}")
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
        long fieldsWithDimensions = mappingsByConcept.values().stream().flatMap(List::stream)
            .filter(TaxonomyVisualizationExporter::hasDimensions).count();
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
            .append("main{max-width:1480px;margin:0 auto;padding:32px 24px 48px;}")
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
            .append(".layout-table th,.layout-table td{text-align:left;padding:10px 12px;border-bottom:1px solid #edf2f7;vertical-align:top;}")
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
            .append("code{background:#eef4fa;padding:2px 6px;border-radius:6px;}")
            .append(".muted{color:#5b7086;}")
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
                .append(role.hasDimensionalMappings(mappingsByConcept))
                .append("\" data-has-enumeration=\"")
                .append(role.hasEnumerationMappings(mappingsByConcept))
                .append("\">")
                .append("<summary><span>")
                .append(escapeHtml(role.displayLabel()))
                .append("</span><span class=\"role-meta\">")
                .append(role.rootLabels().size()).append(" Wurzelknoten, ")
                .append(role.nodeCount()).append(" Knoten</span></summary>")
                .append("<div class=\"node-children\">");

            for (String rootLabel : role.rootLabels()) {
                renderNode(html, role, rootLabel, mappingsByConcept, placeholdersByField, new LinkedHashSet<>(), 0);
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
                            Map<String, List<MappingEntry>> mappingsByConcept,
                            Map<String, List<String>> placeholdersByField,
                            Set<String> path,
                            int depth) {
        if (!path.add(label)) {
            return;
        }

        String qname = role.qname(label);
        String display = humanize(qname);
        List<MappingEntry> mappedEntries = mappingsByConcept.getOrDefault(normalizeConceptKey(qname), List.of());
        List<String> placeholders = new ArrayList<>();
        for (MappingEntry entry : mappedEntries) {
            List<String> fieldPlaceholders = placeholdersByField.get(entry.field());
            if (fieldPlaceholders != null) {
                placeholders.addAll(fieldPlaceholders);
            }
        }

        boolean hasDimensions = mappedEntries.stream().anyMatch(entry -> entry.dimensions() != null && !entry.dimensions().isEmpty());
        boolean hasEnumeration = mappedEntries.stream().anyMatch(entry -> entry.enumerationDomain() != null && !entry.enumerationDomain().isBlank());
        String searchText = normalizeSearch(role.searchText() + " " + qname + " " + display + " " + mappedEntries.stream().map(MappingEntry::field).collect(Collectors.joining(" ")) + " " + String.join(" ", placeholders));
        String nodeId = fieldId(role.roleLabel() + "-" + qname + "-" + depth + "-" + path.hashCode());

        String conceptValue = mappedEntries.stream().map(MappingEntry::concept).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String typeValue = mappedEntries.stream().map(MappingEntry::type).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String periodValue = mappedEntries.stream().map(MappingEntry::period).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String unitValue = mappedEntries.stream().map(MappingEntry::unit).filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.joining(", "));
        String placeholderValue = placeholders.isEmpty() ? "" : String.join(", ", new TreeSet<>(placeholders));
        String dimensionsValue = renderDimensionSummary(mappedEntries);
        String enumerationValue = renderEnumerationSummary(mappedEntries);

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
            .append(metaCellIfPresent("Dimensions", dimensionsValue))
            .append(metaCellIfPresent("Enumeration", enumerationValue))
            .append("</div>")
            .append("<div class=\"node-children\">");

        for (PresentationArc childArc : role.children(label)) {
            renderNode(html, role, childArc.to(), mappingsByConcept, placeholdersByField, new LinkedHashSet<>(path), depth + 1);
        }

        html.append("</div></details>");
    }

    private String renderDimensionSummary(List<MappingEntry> mappedEntries) {
        if (mappedEntries.isEmpty()) {
            return "";
        }
        List<String> dimensions = new ArrayList<>();
        for (MappingEntry entry : mappedEntries) {
            if (entry.dimensions() != null) {
                for (DimensionSelection dimension : entry.dimensions()) {
                    dimensions.add(dimension.axisQname() + " → " + dimension.memberQname());
                }
            }
        }
        if (dimensions.isEmpty()) {
            return "";
        }
        return dimensions.stream().distinct().collect(Collectors.joining(", "));
    }

    private String renderEnumerationSummary(List<MappingEntry> mappedEntries) {
        if (mappedEntries.isEmpty()) {
            return "";
        }
        List<String> enumeration = new ArrayList<>();
        for (MappingEntry entry : mappedEntries) {
            if (entry.enumerationDomain() != null && !entry.enumerationDomain().isBlank()) {
                enumeration.add(entry.enumerationDomain());
            }
            if (entry.allowedValues() != null) {
                enumeration.addAll(entry.allowedValues());
            }
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

    private record LayoutSnapshot(Map<String, String> placeholderMappings) {
    }

    private record LinkEdge(String source, String target, String layer) {
    }

    private record TaxonomyMetadata(long xsdElementCount,
                                    long xsdImportCount,
                                    Map<String, Long> fileCountByLayer,
                                    Map<String, Long> edgeCountByLayer,
                                    List<LinkEdge> sampleEdges,
                                    List<String> hrefTargets) {
        private List<String> allLayers() {
            Set<String> layers = new TreeSet<>();
            layers.addAll(fileCountByLayer.keySet());
            layers.addAll(edgeCountByLayer.keySet());
            return new ArrayList<>(layers);
        }
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

        private boolean hasDimensionalMappings(Map<String, List<MappingEntry>> mappingsByConcept) {
            return hasAnyMatchingMapping(mappingsByConcept, TaxonomyVisualizationExporter::hasDimensions, new HashSet<>(), rootLabels);
        }

        private boolean hasEnumerationMappings(Map<String, List<MappingEntry>> mappingsByConcept) {
            return hasAnyMatchingMapping(mappingsByConcept, TaxonomyVisualizationExporter::hasEnumeration, new HashSet<>(), rootLabels);
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
