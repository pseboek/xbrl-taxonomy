package org.esrs.pipeline.xbrl.serializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ReportingEntity;
import org.esrs.pipeline.model.ReportingPeriod;
import org.esrs.pipeline.xbrl.context.ContextBuilder;
import org.esrs.pipeline.xbrl.fact.XbrlFact;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class XbrlInstanceWriterTest {
    @Test
    void shouldWriteBasicXbrlInstance() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), false),
            List.of(new DisclosureFact("company.totalEnergyConsumption", "100", List.of(), null, 0))
        );
        ContextBuilder contextBuilder = new ContextBuilder();
        var ctx = contextBuilder.build(envelope);

        List<XbrlFact> facts = List.of(
            new XbrlFact("company.totalEnergyConsumption", "company.totalEnergyConsumption#1", "esrs:EnergyConsumptionRelatedToOwnOperations", "c1", "u_kWh", "100", "0", true, false)
        );

        Path temp = Files.createTempDirectory("xbrl-test").resolve("instance.xml");
        XbrlInstanceWriter writer = new XbrlInstanceWriter();
        writer.write(temp, envelope, ctx.contexts(), facts, "esrs_all.xsd");

        String xml = Files.readString(temp);
        assertTrue(xml.contains("esrs:EnergyConsumptionRelatedToOwnOperations") || xml.contains("EnergyConsumptionRelatedToOwnOperations"));
        assertTrue(xml.contains("contextRef"));
    }
}
