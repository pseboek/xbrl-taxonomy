package org.esrs.pipeline.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.esrs.pipeline.model.DimensionSelection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MappingRegistry {
    private final Map<String, MappingEntry> mappingByField;

    private MappingRegistry(Map<String, MappingEntry> mappingByField) {
        this.mappingByField = mappingByField;
    }

    public static MappingRegistry fromPath(Path mappingPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(mappingPath)) {
            return fromStream(inputStream);
        }
    }

    public static MappingRegistry fromResource(String resourcePath) throws IOException {
        try (InputStream inputStream = MappingRegistry.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Mapping resource not found: " + resourcePath);
            }
            return fromStream(inputStream);
        }
    }

    private static MappingRegistry fromStream(InputStream inputStream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(inputStream);
        JsonNode fieldMappings = root.path("fieldMappings");
        if (!fieldMappings.isObject()) {
            throw new IOException("fieldMappings must be an object");
        }

        Map<String, MappingEntry> entries = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = fieldMappings.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String field = e.getKey();
            JsonNode cfg = e.getValue();
            entries.put(field, parseEntry(field, cfg));
        }
        return new MappingRegistry(Collections.unmodifiableMap(entries));
    }

    private static MappingEntry parseEntry(String field, JsonNode cfg) {
        List<DimensionSelection> dims = new ArrayList<>();
        JsonNode dimensions = cfg.path("dimensions");
        if (dimensions.isArray()) {
            for (JsonNode d : dimensions) {
                dims.add(new DimensionSelection(d.path("axis").asText(), d.path("member").asText()));
            }
        }

        Integer decimals = cfg.has("decimals") ? cfg.path("decimals").asInt() : null;
        return new MappingEntry(
            field,
            cfg.path("concept").asText(),
            cfg.path("type").asText(),
            cfg.path("unit").asText(null),
            cfg.path("period").asText(null),
            cfg.path("enumerationDomain").asText(null),
            decimals,
            dims
        );
    }

    public MappingEntry getRequired(String field) {
        MappingEntry entry = mappingByField.get(field);
        if (entry == null) {
            throw new IllegalArgumentException("Missing mapping for field: " + field);
        }
        return entry;
    }

    public Map<String, MappingEntry> all() {
        return mappingByField;
    }
}
