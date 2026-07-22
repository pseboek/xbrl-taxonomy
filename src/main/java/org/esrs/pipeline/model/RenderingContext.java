package org.esrs.pipeline.model;

import java.util.List;
import java.util.Map;

public record RenderingContext(ReportEnvelope envelope,
                               Map<String, String> contextByField,
                               Map<String, String> unitByField,
                               List<InlinePlacement> placements) {
}
