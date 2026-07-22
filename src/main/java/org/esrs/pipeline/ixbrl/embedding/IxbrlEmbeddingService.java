package org.esrs.pipeline.ixbrl.embedding;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.esrs.pipeline.xbrl.context.ContextKey;
import org.esrs.pipeline.xbrl.fact.XbrlFact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IxbrlEmbeddingService {
    public String embed(String renderedTemplate,
                        List<XbrlFact> facts,
                        Path layoutMappingPath,
                        Map<ContextKey, String> contexts,
                        String schemaRefHref) throws IOException {
        Map<String, String> placeholderToField = loadLayout(layoutMappingPath);
        Map<String, XbrlFact> byField = new HashMap<>();
        for (XbrlFact fact : facts) {
            byField.put(fact.field(), fact);
        }

        String result = renderedTemplate;
        for (Map.Entry<String, String> e : placeholderToField.entrySet()) {
            String placeholder = "{{fact:" + e.getKey() + "}}";
            XbrlFact fact = byField.get(e.getValue());
            if (fact == null) {
                result = result.replace(placeholder, "N/A");
                continue;
            }
            result = result.replace(placeholder, asInlineFact(fact));
        }
        return injectInlineHeader(result, contexts, collectUnits(facts), schemaRefHref);
    }

    public void writeOutput(Path outputPath, String content) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    private String asInlineFact(XbrlFact fact) {
        String name = fact.conceptQname();
        if (fact.numeric()) {
            String decimals = fact.decimals() == null ? "INF" : fact.decimals();
            return "<ix:nonFraction name=\"" + name + "\" contextRef=\"" + fact.contextRef()
                + "\" unitRef=\"" + fact.unitRef() + "\" decimals=\"" + decimals + "\">"
                + fact.value() + "</ix:nonFraction>";
        }
        return "<ix:nonNumeric name=\"" + name + "\" contextRef=\"" + fact.contextRef() + "\">"
            + fact.value() + "</ix:nonNumeric>";
    }

    private String injectInlineHeader(String xhtml,
                                      Map<ContextKey, String> contexts,
                                      Map<String, String> units,
                                      String schemaRefHref) {
        String bodyStart = "<body>";
        int bodyIndex = xhtml.indexOf(bodyStart);
        if (bodyIndex < 0) {
            return xhtml;
        }

        String header = buildHeader(contexts, units, schemaRefHref);
        int insertPos = bodyIndex + bodyStart.length();
        return xhtml.substring(0, insertPos) + "\n" + header + xhtml.substring(insertPos);
    }

    private String buildHeader(Map<ContextKey, String> contexts, Map<String, String> units, String schemaRefHref) {
        StringWriter out = new StringWriter();
        out.append("<ix:header>\n");
        out.append("    <ix:references>\n");
        out.append("        <link:schemaRef xlink:type=\"simple\" xlink:href=\"")
            .append(escapeXml(schemaRefHref))
            .append("\"/>\n");
        out.append("    </ix:references>\n");
        out.append("    <ix:resources>\n");

        for (Map.Entry<ContextKey, String> entry : contexts.entrySet()) {
            ContextKey key = entry.getKey();
            String id = entry.getValue();

            out.append("        <xbrli:context id=\"").append(escapeXml(id)).append("\">\n");
            out.append("            <xbrli:entity>\n");
            out.append("                <xbrli:identifier scheme=\"")
                .append(escapeXml(key.entityScheme()))
                .append("\">")
                .append(escapeXml(key.entityIdentifier()))
                .append("</xbrli:identifier>\n");
            out.append("            </xbrli:entity>\n");
            out.append("            <xbrli:period>\n");
            out.append("                <xbrli:startDate>")
                .append(key.startDate().toString())
                .append("</xbrli:startDate>\n");
            out.append("                <xbrli:endDate>")
                .append(key.endDate().toString())
                .append("</xbrli:endDate>\n");
            out.append("            </xbrli:period>\n");

            if (!key.dimensions().isEmpty()) {
                out.append("            <xbrli:scenario>\n");
                for (Map.Entry<String, String> dim : key.dimensions().entrySet()) {
                    out.append("                <xbrldi:explicitMember dimension=\"")
                        .append(escapeXml(dim.getKey()))
                        .append("\">")
                        .append(escapeXml(dim.getValue()))
                        .append("</xbrldi:explicitMember>\n");
                }
                out.append("            </xbrli:scenario>\n");
            }

            out.append("        </xbrli:context>\n");
        }

        for (Map.Entry<String, String> unit : units.entrySet()) {
            out.append("        <xbrli:unit id=\"")
                .append(escapeXml(unit.getKey()))
                .append("\">\n");
            out.append("            <xbrli:measure>")
                .append(escapeXml(unit.getValue()))
                .append("</xbrli:measure>\n");
            out.append("        </xbrli:unit>\n");
        }

        out.append("    </ix:resources>\n");
        out.append("</ix:header>\n");
        return out.toString();
    }

    private Map<String, String> collectUnits(List<XbrlFact> facts) {
        Map<String, String> unitMap = new LinkedHashMap<>();
        for (XbrlFact fact : facts) {
            if (fact.unitRef() == null) {
                continue;
            }
            if (fact.unitRef().contains("EUR")) {
                unitMap.putIfAbsent(fact.unitRef(), "iso4217:EUR");
            } else {
                unitMap.putIfAbsent(fact.unitRef(), "xbrli:pure");
            }
        }
        return unitMap;
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private Map<String, String> loadLayout(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        JsonNode placeholders = root.path("placeholders");
        if (!placeholders.isObject()) {
            throw new IOException("Layout mapping must define object 'placeholders'");
        }
        Map<String, String> result = new HashMap<>();
        placeholders.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }
}
