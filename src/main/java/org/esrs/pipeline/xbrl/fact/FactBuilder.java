package org.esrs.pipeline.xbrl.fact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.esrs.pipeline.mapping.MappingEntry;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DimensionSelection;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;

public class FactBuilder {
    private static final Set<String> YES_NO_ENUM = Set.of("esrs:YesMember", "esrs:NoMember");

    public FactBuildResult build(ReportEnvelope envelope,
                                 MappingRegistry mappingRegistry,
                                 Map<String, String> fieldOccurrenceContextRefs) {
        List<XbrlFact> facts = new ArrayList<>();
        Map<String, String> unitByField = new HashMap<>();

        int seq = 1;
        for (DisclosureFact sourceFact : envelope.facts()) {
            MappingEntry entry = mappingRegistry.getRequired(sourceFact.field());
            validatePeriod(entry, envelope.period().instant(), sourceFact.field());
            validateDimensions(entry, sourceFact);
            String normalized = normalizeValue(entry, sourceFact.value());
            String occurrence = sourceFact.field() + "#" + seq;
            String contextRef = fieldOccurrenceContextRefs.get(occurrence);
            String unitRef = resolveUnit(entry, sourceFact);
            String decimals = resolveDecimals(entry, sourceFact);
            boolean numeric = "numeric".equalsIgnoreCase(entry.type());
            boolean enumeration = "enumeration".equalsIgnoreCase(entry.type());

            if (contextRef == null || contextRef.isBlank()) {
                throw new IllegalArgumentException("Missing context reference for field occurrence: " + occurrence);
            }
            if (numeric && (unitRef == null || unitRef.isBlank())) {
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
        return "u_" + unit.replace(':', '_').replace('-', '_');
    }

    private void validatePeriod(MappingEntry entry, boolean reportInstant, String field) {
        if (entry.period() == null || entry.period().isBlank()) {
            return;
        }

        boolean mappingInstant = "instant".equalsIgnoreCase(entry.period());
        boolean mappingDuration = "duration".equalsIgnoreCase(entry.period());
        if (!mappingInstant && !mappingDuration) {
            throw new IllegalArgumentException("Unsupported period type in mapping for field " + field + ": " + entry.period());
        }
        if (mappingInstant != reportInstant) {
            throw new IllegalArgumentException("Period mismatch for field " + field + ": mapping=" + entry.period());
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

        if ("numeric".equalsIgnoreCase(entry.type())) {
            try {
                new BigDecimal(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric value for field " + entry.field() + ": " + value, ex);
            }
            return trimmed;
        }

        if ("enumeration".equalsIgnoreCase(entry.type())) {
            if ("esrs:YesNoDomain".equalsIgnoreCase(entry.enumerationDomain()) && !YES_NO_ENUM.contains(trimmed)) {
                throw new IllegalArgumentException("Invalid enumeration value for field " + entry.field() + ": " + value);
            }
            return trimmed;
        }

        if ("text".equalsIgnoreCase(entry.type())) {
            return trimmed;
        }

        throw new IllegalArgumentException("Unsupported mapping type for field " + entry.field() + ": " + entry.type());
    }

    public record FactBuildResult(List<XbrlFact> facts, Map<String, String> unitByField) {
    }
}
