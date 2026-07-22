package org.esrs.pipeline.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.esrs.pipeline.model.ReportEnvelope;

import java.io.IOException;
import java.nio.file.Path;

public class ApiIngestionService {
    private final ObjectMapper objectMapper;

    public ApiIngestionService() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ReportEnvelope loadFromJson(Path inputPath) throws IOException {
        return objectMapper.readValue(inputPath.toFile(), ReportEnvelope.class);
    }
}
