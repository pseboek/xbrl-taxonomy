package org.esrs.pipeline.mapping;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingRegistryTest {
    @Test
    void shouldLoadMappingsFromJson() throws Exception {
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        MappingEntry entry = registry.getRequired("company.totalEnergyConsumption");
        assertEquals("esrs:TotalEnergyConsumption", entry.concept());
        assertEquals("numeric", entry.type());
    }
}
