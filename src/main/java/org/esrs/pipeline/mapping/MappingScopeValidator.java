package org.esrs.pipeline.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MappingScopeValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validate(MappingRegistry mappingRegistry, Path layoutMapPath, Path scopeFilePath) throws IOException {
        if (scopeFilePath == null || !Files.exists(scopeFilePath)) {
            throw new IOException("Mapping scope file not found: " + scopeFilePath);
        }

        JsonNode scopeRoot = objectMapper.readTree(Files.readString(scopeFilePath, StandardCharsets.UTF_8));
        Set<String> mappedFields = new HashSet<>(mappingRegistry.all().keySet());
        Set<String> layoutFields = loadLayoutFields(layoutMapPath);

        List<String> errors = new ArrayList<>();

        int minimumMappedFields = scopeRoot.path("minimumMappedFields").asInt(0);
        if (mappedFields.size() < minimumMappedFields) {
            errors.add("mapped fields below minimum: " + mappedFields.size() + " < " + minimumMappedFields);
        }

        JsonNode requiredFields = scopeRoot.path("requiredFields");
        if (requiredFields.isArray()) {
            for (JsonNode fieldNode : requiredFields) {
                String field = fieldNode.asText();
                if (!mappedFields.contains(field)) {
                    errors.add("required mapping field missing: " + field);
                }
            }
        }

        JsonNode requiredTemplateFields = scopeRoot.path("requiredTemplateFields");
        if (requiredTemplateFields.isArray()) {
            for (JsonNode fieldNode : requiredTemplateFields) {
                String field = fieldNode.asText();
                if (!layoutFields.contains(field)) {
                    errors.add("required template field missing in layout map: " + field);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Mapping scope validation failed: " + String.join(" | ", errors));
        }
    }

    private Set<String> loadLayoutFields(Path layoutMapPath) throws IOException {
        JsonNode layoutRoot = objectMapper.readTree(Files.readString(layoutMapPath, StandardCharsets.UTF_8));
        Set<String> layoutFields = new HashSet<>();
        Iterator<String> placeholders = layoutRoot.path("placeholders").fieldNames();
        while (placeholders.hasNext()) {
            String placeholder = placeholders.next();
            layoutFields.add(layoutRoot.path("placeholders").path(placeholder).asText());
        }
        return layoutFields;
    }
}
