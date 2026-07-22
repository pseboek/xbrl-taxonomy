package org.esrs.pipeline.mapping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MappingScopeValidatorTest {
    @Test
    void shouldValidateConfiguredScopeSuccessfully() throws Exception {
        MappingRegistry registry = MappingRegistry.fromPath(Path.of("mapping/map-esrs-2023-12-22.json"));
        MappingScopeValidator validator = new MappingScopeValidator();

        assertDoesNotThrow(() -> validator.validate(
            registry,
            Path.of("mapping/report-layout-map.json"),
            Path.of("mapping/scopes/esrs-full-scope.json")
        ));
    }
}
