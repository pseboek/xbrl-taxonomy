package org.esrs.pipeline.ixbrl.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateDataConsistencyTest {
    private static final Pattern FACT_PLACEHOLDER = Pattern.compile("\\{\\{fact:([a-zA-Z0-9_]+)\\}\\}");

    @Test
    void templatePlaceholdersShouldMatchLayoutMap() throws Exception {
        Path templatePath = Path.of("templates/report-base.xhtml");
        Path layoutPath = Path.of("mapping/report-layout-map.json");

        String template = Files.readString(templatePath, StandardCharsets.UTF_8);
        Set<String> placeholdersInTemplate = extractPlaceholders(template);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode layoutRoot = mapper.readTree(Files.readString(layoutPath, StandardCharsets.UTF_8));
        Set<String> placeholdersInLayout = new HashSet<>();
        layoutRoot.path("placeholders").fieldNames().forEachRemaining(placeholdersInLayout::add);

        assertEquals(placeholdersInTemplate, placeholdersInLayout,
            "Template fact placeholders and layout mapping keys must be identical.");
    }

    @Test
    void layoutFieldsShouldExistInMappingAndFictiveData() throws Exception {
        Path layoutPath = Path.of("mapping/report-layout-map.json");
        Path mapPath = Path.of("mapping/map-esrs-2023-12-22.json");
        Path inputPath = Path.of("src/main/resources/testdata/fictive-esrs-input.json");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode layoutRoot = mapper.readTree(Files.readString(layoutPath, StandardCharsets.UTF_8));
        JsonNode inputRoot = mapper.readTree(Files.readString(inputPath, StandardCharsets.UTF_8));

        Set<String> mappedFields = new HashSet<>(MappingRegistry.fromPath(mapPath).all().keySet());

        Set<String> inputFields = new HashSet<>();
        for (JsonNode fact : inputRoot.path("facts")) {
            inputFields.add(fact.path("field").asText());
        }

        Iterator<String> placeholders = layoutRoot.path("placeholders").fieldNames();
        while (placeholders.hasNext()) {
            String key = placeholders.next();
            String field = layoutRoot.path("placeholders").path(key).asText();
            assertTrue(mappedFields.contains(field), "Layout field missing in mapping: " + field);
            assertTrue(inputFields.contains(field), "Layout field missing in fictive input: " + field);
        }
    }

    private Set<String> extractPlaceholders(String template) {
        Set<String> result = new HashSet<>();
        Matcher matcher = FACT_PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}
