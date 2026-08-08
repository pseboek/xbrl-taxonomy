package org.esrs.pipeline.xbrl.fact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.esrs.pipeline.mapping.MappingEntry;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DimensionSelection;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.xbrl.unit.UnitCatalog;

public class FactBuilder {
    public static final String NIL_SENTINEL = "__NIL__";
    private static final Set<String> YES_NO_ENUM = Set.of("esrs:YesMember", "esrs:NoMember");

    public FactBuildResult build(ReportEnvelope envelope,
                                 MappingRegistry mappingRegistry,
                                 Map<String, String> fieldOccurrenceContextRefs) {
        List<XbrlFact> facts = new ArrayList<>();
        Map<String, String> unitByField = new HashMap<>();

        int seq = 1;
        for (DisclosureFact sourceFact : envelope.facts()) {
            MappingEntry entry = mappingRegistry.getRequired(sourceFact.field());
            validatePeriod(entry, sourceFact.field());
            validateDimensions(entry, sourceFact);
            String normalized = normalizeValue(entry, sourceFact.value());
            String occurrence = sourceFact.field() + "#" + seq;
            String contextRef = fieldOccurrenceContextRefs.get(occurrence);
            String unitRef = resolveUnit(entry, sourceFact);
            String decimals = resolveDecimals(entry, sourceFact);
            boolean numeric = "numeric".equalsIgnoreCase(entry.type());
            boolean enumeration = "enumeration".equalsIgnoreCase(entry.type());
            boolean nilFact = NIL_SENTINEL.equals(normalized);

            if (contextRef == null || contextRef.isBlank()) {
                throw new IllegalArgumentException("Missing context reference for field occurrence: " + occurrence);
            }
            if (numeric && !nilFact && (unitRef == null || unitRef.isBlank())) {
                throw new IllegalArgumentException("Missing numeric unit for field: " + sourceFact.field());
            }

            facts.add(new XbrlFact(
                sourceFact.field(),
                occurrence,
                entry.concept(),
                contextRef,
                unitRef,
                normalized,
                decimals,
                numeric,
                enumeration
            ));

            if (unitRef != null) {
                unitByField.put(sourceFact.field(), unitRef);
            }
            seq++;
        }

        return new FactBuildResult(facts, unitByField);
    }

    private String resolveDecimals(MappingEntry entry, DisclosureFact sourceFact) {
        Integer decimals = sourceFact.decimalsOverride() != null ? sourceFact.decimalsOverride() : entry.decimals();
        return decimals == null ? null : Integer.toString(decimals);
    }

    private String resolveUnit(MappingEntry entry, DisclosureFact sourceFact) {
        String unit = sourceFact.unitOverride() != null ? sourceFact.unitOverride() : entry.unit();
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return UnitCatalog.sanitizeUnitRef(unit);
    }

    private void validatePeriod(MappingEntry entry, String field) {
        if (entry.period() == null || entry.period().isBlank()) {
            return;
        }

        boolean mappingInstant = "instant".equalsIgnoreCase(entry.period());
        boolean mappingDuration = "duration".equalsIgnoreCase(entry.period());
        if (!mappingInstant && !mappingDuration) {
            throw new IllegalArgumentException("Unsupported period type in mapping for field " + field + ": " + entry.period());
        }
    }

    private void validateDimensions(MappingEntry entry, DisclosureFact sourceFact) {
        List<DimensionSelection> expected = entry.dimensions() == null ? List.of() : entry.dimensions();
        List<DimensionSelection> actual = sourceFact.dimensions() == null ? List.of() : sourceFact.dimensions();
        if (expected.isEmpty() && actual.isEmpty()) {
            return;
        }

        Set<String> expectedPairs = new HashSet<>();
        for (DimensionSelection d : expected) {
            expectedPairs.add(d.axisQname() + "=" + d.memberQname());
        }
        Set<String> actualPairs = new HashSet<>();
        for (DimensionSelection d : actual) {
            actualPairs.add(d.axisQname() + "=" + d.memberQname());
        }
        if (!expectedPairs.equals(actualPairs)) {
            throw new IllegalArgumentException("Invalid dimension set for field: " + sourceFact.field());
        }
    }

    private String normalizeValue(MappingEntry entry, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Fact value is null for field: " + entry.field());
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Fact value is empty for field: " + entry.field());
        }

        if (NIL_SENTINEL.equals(trimmed)) {
            return NIL_SENTINEL;
        }

        if ("numeric".equalsIgnoreCase(entry.type())) {
            try {
                new BigDecimal(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for field " + entry.field() + ": " + value, ex);
            }
            return trimmed;
        }

        if ("enumeration".equalsIgnoreCase(entry.type())) {
            if ("esrs:YesNoDomain".equalsIgnoreCase(entry.enumerationDomain())) {
                if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                    return trimmed.toLowerCase();
                }
                if (!YES_NO_ENUM.contains(trimmed)) {
                    throw new IllegalArgumentException("Invalid enumeration value for field " + entry.field() + ": " + value);
                }
                return "esrs:YesMember".equals(trimmed) ? "true" : "false";
            }

            if (entry.allowedValues() != null && !entry.allowedValues().isEmpty()) {
                List<String> setValues = parseEnumerationSetValues(trimmed);
                if (setValues.size() > 1) {
                    for (String setValue : setValues) {
                        if (!entry.allowedValues().contains(setValue)) {
                            throw new IllegalArgumentException("Invalid enumeration value for field " + entry.field() + ": " + value);
                        }
                    }
                    // XBRL enum2 set lexical form uses a whitespace-separated QName list.
                    return String.join(" ", new LinkedHashSet<>(setValues));
                }

                if (!entry.allowedValues().contains(trimmed)) {
                    throw new IllegalArgumentException("Invalid enumeration value for field " + entry.field() + ": " + value);
                }
                return trimmed;
            }
            return trimmed;
        }

        if ("text".equalsIgnoreCase(entry.type())) {
            return trimmed;
        }

        throw new IllegalArgumentException("Unsupported mapping type for field " + entry.field() + ": " + entry.type());
    }

    private List<String> parseEnumerationSetValues(String value) {
        String[] commaOrSemicolonSplit = value.split("\\s*[;,]\\s*");
        if (commaOrSemicolonSplit.length > 1) {
            List<String> values = new ArrayList<>();
            for (String token : commaOrSemicolonSplit) {
                String trimmed = token == null ? "" : token.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }

        String[] whitespaceSplit = value.trim().split("\\s+");
        if (whitespaceSplit.length > 1) {
            List<String> values = new ArrayList<>();
            for (String token : whitespaceSplit) {
                if (!token.isBlank()) {
                    values.add(token.trim());
                }
            }
            return values;
        }

        return List.of(value.trim());
    }

    public record FactBuildResult(List<XbrlFact> facts, Map<String, String> unitByField) {
    }
}
