package org.esrs.pipeline.xbrl.context;

import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ReportingEntity;
import org.esrs.pipeline.model.ReportingPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextBuilderTest {
    @Test
    void shouldDeduplicateSameContext() {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(
                new DisclosureFact("a", "1", List.of(), null, null),
                new DisclosureFact("b", "2", List.of(), null, null)
            )
        );

        ContextBuilder builder = new ContextBuilder();
        ContextBuilder.ContextBuildResult result = builder.build(envelope);
        assertEquals(1, result.contexts().size());
    }
}
