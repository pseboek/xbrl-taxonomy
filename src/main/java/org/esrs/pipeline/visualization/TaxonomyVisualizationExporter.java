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
        Map<String, List<MappingEntry>> mappingsByConcept = groupByConcept(entries);
        Map<String, List<String>> placeholdersByField = reverseLayout(layoutSnapshot.placeholderMappings());

        Files.writeString(outputHtml, renderHtml(forest, mappingsByConcept, placeholdersByField, layoutSnapshot), StandardCharsets.UTF_8);

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

    private String renderHtml(PresentationForest forest,
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
            .append("<section><h2>Präsentationshierarchie</h2><div class=\"role-list\" id=\"hierarchyRoot\">");

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
            .append("<section><h2>Konzeptindex</h2><div class=\"concept-list\" id=\"conceptIndex\">");

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

        html.append("</div></section>")
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

        html.append("</tbody></table></section>")
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
            .append("function expandAll(open){ document.querySelectorAll('details').forEach(function(node){ node.open = open; }); }")
            .append("window.addEventListener('DOMContentLoaded', applyFilters);")
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
