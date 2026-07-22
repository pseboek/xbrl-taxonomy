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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MappingRegistry {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, MappingEntry> mappingByField;

    private MappingRegistry(Map<String, MappingEntry> mappingByField) {
        this.mappingByField = mappingByField;
    }

    public static MappingRegistry fromPath(Path mappingPath) throws IOException {
        Map<String, MappingEntry> entries = loadFromPath(mappingPath.toAbsolutePath().normalize(), new HashSet<>());
        return new MappingRegistry(Collections.unmodifiableMap(entries));
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
        JsonNode root = OBJECT_MAPPER.readTree(inputStream);
        Map<String, MappingEntry> entries = parseFieldMappings(root);
        return new MappingRegistry(Collections.unmodifiableMap(entries));
    }

    private static Map<String, MappingEntry> loadFromPath(Path mappingPath, Set<Path> visited) throws IOException {
        if (!Files.exists(mappingPath)) {
            throw new IOException("Mapping file not found: " + mappingPath);
        }
        if (!visited.add(mappingPath)) {
            throw new IOException("Cyclic mapping import detected: " + mappingPath);
        }

        JsonNode root;
        try (InputStream inputStream = Files.newInputStream(mappingPath)) {
            root = OBJECT_MAPPER.readTree(inputStream);
        }

        Map<String, MappingEntry> entries = new HashMap<>();
        JsonNode imports = root.path("imports");
        if (imports.isArray()) {
            for (JsonNode importedPathNode : imports) {
                Path importedPath = mappingPath.getParent().resolve(importedPathNode.asText()).normalize();
                entries.putAll(loadFromPath(importedPath, visited));
            }
        }

        // Local field mappings override imported mappings with the same key.
        entries.putAll(parseFieldMappings(root));
        visited.remove(mappingPath);
        return entries;
    }

    private static Map<String, MappingEntry> parseFieldMappings(JsonNode root) throws IOException {
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
        return entries;
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
