package org.esrs.pipeline.model;

import java.util.List;

public record ReportEnvelope(ReportingEntity entity, ReportingPeriod period, List<DisclosureFact> facts) {
}
