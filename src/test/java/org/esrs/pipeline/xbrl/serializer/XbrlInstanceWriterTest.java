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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(xml.contains("uom:kWh"));
    }

    @Test
    void shouldWriteInstantContextWhenReportPeriodIsInstant() throws Exception {
        ReportEnvelope envelope = new ReportEnvelope(
            new ReportingEntity("scheme", "id", "entity"),
            new ReportingPeriod(LocalDate.parse("2025-12-31"), LocalDate.parse("2025-12-31"), true),
            List.of(new DisclosureFact("workforce.totalEmployees", "10", List.of(), null, 0))
        );
        ContextBuilder contextBuilder = new ContextBuilder();
        var ctx = contextBuilder.build(envelope);

        List<XbrlFact> facts = List.of(
            new XbrlFact("workforce.totalEmployees", "workforce.totalEmployees#1", "esrs:NumberOfEmployeesHeadCountDuringPeriod", "c1", "u_count", "10", "0", true, false)
        );

        Path temp = Files.createTempDirectory("xbrl-test-instant").resolve("instance.xml");
        XbrlInstanceWriter writer = new XbrlInstanceWriter();
        writer.write(temp, envelope, ctx.contexts(), facts, "esrs_all.xsd");

        String xml = Files.readString(temp);
        assertTrue(xml.contains("<xbrli:instant>2025-12-31</xbrli:instant>"));
        assertFalse(xml.contains("<xbrli:startDate>"));
    }
}
