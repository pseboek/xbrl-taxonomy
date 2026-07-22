package org.esrs.pipeline.xbrl.fact;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ReportingEntity;
import org.esrs.pipeline.model.ReportingPeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FactBuilderTest {
    @Test
    void shouldNormalizeYesNoEnumerationToBooleanLexicalValues() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(new DisclosureFact("governance.corruptionPolicyExists", "esrs:YesMember", List.of(), null, null))
        );
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        FactBuilder builder = new FactBuilder();

        FactBuilder.FactBuildResult result = builder.build(
            envelope,
            registry,
            Map.of("governance.corruptionPolicyExists#1", "c1")
        );

        assertEquals("true", result.facts().getFirst().value());
    }

    @Test
    void shouldRejectInvalidYesNoEnumeration() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(new DisclosureFact("governance.corruptionPolicyExists", "INVALID", List.of(), null, null))
        );
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        FactBuilder builder = new FactBuilder();

        assertThrows(IllegalArgumentException.class,
            () -> builder.build(envelope, registry, Map.of("governance.corruptionPolicyExists#1", "c1")));
    }

    @Test
    void shouldRejectInvalidNumericValue() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(new DisclosureFact("company.totalEnergyConsumption", "NaNvalue", List.of(), null, 2))
        );
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        FactBuilder builder = new FactBuilder();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> builder.build(envelope, registry, Map.of("company.totalEnergyConsumption#1", "c1")));
        assertTrue(ex.getMessage().contains("Invalid numeric value"));
    }

    @Test
    void shouldRejectPeriodMismatch() throws Exception {
        ReportEnvelope instantEnvelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-12-31"), LocalDate.parse("2025-12-31"), true),
            List.of(new DisclosureFact("company.totalEnergyConsumption", "100", List.of(), null, 0))
        );
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        FactBuilder builder = new FactBuilder();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> builder.build(instantEnvelope, registry, Map.of("company.totalEnergyConsumption#1", "c1")));
        assertTrue(ex.getMessage().contains("Period mismatch"));
    }
}
