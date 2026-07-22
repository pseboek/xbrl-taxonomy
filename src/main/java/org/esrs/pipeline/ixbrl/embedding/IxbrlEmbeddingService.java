package org.esrs.pipeline.ixbrl.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.esrs.pipeline.xbrl.fact.XbrlFact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IxbrlEmbeddingService {
    public String embed(String renderedTemplate, List<XbrlFact> facts, Path layoutMappingPath) throws IOException {
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
        return result;
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
