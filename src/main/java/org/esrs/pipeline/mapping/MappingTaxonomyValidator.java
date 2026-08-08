package org.esrs.pipeline.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.esrs.pipeline.model.DimensionSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingTaxonomyValidator {
    private static final Logger LOG = LoggerFactory.getLogger(MappingTaxonomyValidator.class);
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<xsd:element[^>]*>");
    private static final Pattern ELEMENT_NAME_PATTERN = Pattern.compile("\\bname=\"([^\"]+)\"");
    private static final Pattern PERIOD_TYPE_PATTERN = Pattern.compile("\\bxbrli:periodType=\"([^\"]+)\"");

    public void validate(MappingRegistry mappingRegistry, Path taxonomyRoot) throws IOException {
        Path coreSchema = taxonomyRoot
            .resolve("xbrl.efrag.org")
            .resolve("taxonomy")
            .resolve("esrs")
            .resolve("2023-12-22")
            .resolve("common")
            .resolve("esrs_cor.xsd");

        if (!Files.exists(coreSchema)) {
            LOG.warn("Skipping mapping taxonomy validation because ESRS core schema is unavailable: {}", coreSchema);
            return;
        }

        Map<String, ConceptMetadata> metadata = loadEsrsConceptMetadata(coreSchema);
        Set<String> availableConcepts = new HashSet<>(metadata.keySet());
        List<String> missing = new ArrayList<>();

        for (MappingEntry entry : mappingRegistry.all().values()) {
            validateQName("concept", entry.field(), entry.concept(), availableConcepts, missing);
            validateConceptSemantics(entry, metadata, missing);
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

    private Map<String, ConceptMetadata> loadEsrsConceptMetadata(Path schemaPath) throws IOException {
        String content = Files.readString(schemaPath, StandardCharsets.UTF_8);
        Matcher matcher = ELEMENT_PATTERN.matcher(content);
        Map<String, ConceptMetadata> metadataByQname = new HashMap<>();
        while (matcher.find()) {
            String elementTag = matcher.group();
            Matcher nameMatcher = ELEMENT_NAME_PATTERN.matcher(elementTag);
            if (!nameMatcher.find()) {
                continue;
            }

            String localName = nameMatcher.group(1);
            String qname = "esrs:" + localName;
            boolean isAbstract = !elementTag.contains("abstract=\"false\"");

            String periodType = null;
            Matcher periodMatcher = PERIOD_TYPE_PATTERN.matcher(elementTag);
            if (periodMatcher.find()) {
                periodType = periodMatcher.group(1);
            }

            metadataByQname.put(qname, new ConceptMetadata(isAbstract, periodType));
        }
        return metadataByQname;
    }

    private void validateConceptSemantics(MappingEntry entry,
                                          Map<String, ConceptMetadata> metadata,
                                          List<String> missing) {
        if (entry.concept() == null || !entry.concept().startsWith("esrs:")) {
            return;
        }

        ConceptMetadata conceptMetadata = metadata.get(entry.concept());
        if (conceptMetadata == null) {
            return;
        }

        if (conceptMetadata.abstractElement()) {
            missing.add("field=" + entry.field() + ", concept is abstract=" + entry.concept());
        }

        if (entry.period() != null && !entry.period().isBlank()
            && conceptMetadata.periodType() != null
            && !entry.period().equalsIgnoreCase(conceptMetadata.periodType())) {
            missing.add("field=" + entry.field() + ", period mismatch: mapping=" + entry.period()
                + ", taxonomy=" + conceptMetadata.periodType());
        }
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

    private record ConceptMetadata(boolean abstractElement, String periodType) {
    }
}
