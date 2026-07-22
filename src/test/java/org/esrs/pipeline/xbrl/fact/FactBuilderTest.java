package org.esrs.pipeline.xbrl.fact;

import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ReportingEntity;
import org.esrs.pipeline.model.ReportingPeriod;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FactBuilderTest {
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
}
