package org.esrs.pipeline.xbrl.context;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ReportingEntity;
import org.esrs.pipeline.model.ReportingPeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ContextBuilderTest {
    @Test
    void shouldDeduplicateSameContext() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(
                new DisclosureFact("company.totalEnergyConsumption", "1", List.of(), null, null),
                new DisclosureFact("climate.scope1GrossEmissions", "2", List.of(), null, null)
            )
        );

        MappingRegistry mappingRegistry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        ContextBuilder builder = new ContextBuilder();
        ContextBuilder.ContextBuildResult result = builder.build(envelope, mappingRegistry);
        assertEquals(1, result.contexts().size());
    }

    @Test
    void shouldPropagateInstantPeriodToContextKey() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-12-31"), LocalDate.parse("2025-12-31"), true),
            List.of(new DisclosureFact("strategy.targetTypeAbsoluteOrRelative", "Absolute", List.of(), null, null))
        );

        MappingRegistry mappingRegistry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        ContextBuilder builder = new ContextBuilder();
        ContextBuilder.ContextBuildResult result = builder.build(envelope, mappingRegistry);
        ContextKey key = result.contexts().keySet().iterator().next();
        assertTrue(!key.instant());
    }

    @Test
    void shouldCreateSeparateContextsForInstantAndDurationFacts() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(
                new DisclosureFact("company.totalEnergyConsumption", "100", List.of(), null, 0),
                new DisclosureFact("full.block0001.adjustingItemsToAssetsAtMaterialPhysicalRiskInReconciliationWithFinancialStatement", "100", List.of(), null, 0)
            )
        );

        MappingRegistry mappingRegistry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        ContextBuilder builder = new ContextBuilder();
        ContextBuilder.ContextBuildResult result = builder.build(envelope, mappingRegistry);

        assertEquals(2, result.contexts().size());
    }
}
