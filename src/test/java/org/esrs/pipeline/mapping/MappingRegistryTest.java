package org.esrs.pipeline.mapping;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class MappingRegistryTest {
    @Test
    void shouldLoadMappingsFromJson() throws Exception {
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        MappingEntry entry = registry.getRequired("company.totalEnergyConsumption");
        assertEquals("esrs:EnergyConsumptionRelatedToOwnOperations", entry.concept());
        assertEquals("numeric", entry.type());

        MappingEntry textEntry = registry.getRequired("governance.whistleblowerChannelDescription");
        assertEquals("text", textEntry.type());
        assertNotNull(textEntry.concept());
    }
}
