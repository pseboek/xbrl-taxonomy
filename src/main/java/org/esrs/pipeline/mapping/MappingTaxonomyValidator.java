package org.esrs.pipeline.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.esrs.pipeline.model.DimensionSelection;

public class MappingTaxonomyValidator {
    private static final Pattern ELEMENT_NAME_PATTERN = Pattern.compile("<xsd:element[^>]*\\bname=\"([^\"]+)\"");

    public void validate(MappingRegistry mappingRegistry, Path taxonomyRoot) throws IOException {
        Path coreSchema = taxonomyRoot
            .resolve("xbrl.efrag.org")
            .resolve("taxonomy")
            .resolve("esrs")
            .resolve("2023-12-22")
            .resolve("common")
            .resolve("esrs_cor.xsd");

        if (!Files.exists(coreSchema)) {
            throw new IOException("ESRS core schema not found for mapping validation: " + coreSchema);
        }

        Set<String> availableConcepts = loadEsrsConcepts(coreSchema);
        List<String> missing = new ArrayList<>();

        for (MappingEntry entry : mappingRegistry.all().values()) {
            validateQName("concept", entry.field(), entry.concept(), availableConcepts, missing);
            if (entry.dimensions() != null) {
                for (DimensionSelection d : entry.dimensions()) {
                    validateQName("dimension axis", entry.field(), d.axisQname(), availableConcepts, missing);
                    validateQName("dimension member", entry.field(), d.memberQname(), availableConcepts, missing);
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Mapping QName validation failed against ESRS taxonomy: " + String.join(" | ", missing)
            );
        }
    }

    private Set<String> loadEsrsConcepts(Path schemaPath) throws IOException {
        String content = Files.readString(schemaPath, StandardCharsets.UTF_8);
        Matcher matcher = ELEMENT_NAME_PATTERN.matcher(content);
        Set<String> qnames = new HashSet<>();
        while (matcher.find()) {
            String localName = matcher.group(1);
            qnames.add("esrs:" + localName);
        }
        return qnames;
    }

    private void validateQName(String kind,
                               String field,
                               String qname,
                               Set<String> availableConcepts,
                               List<String> missing) {
        if (qname == null || qname.isBlank()) {
            return;
        }

        if (!qname.startsWith("esrs:")) {
            return;
        }

        if (!availableConcepts.contains(qname)) {
            missing.add("field=" + field + ", " + kind + "=" + qname);
        }
    }
}
