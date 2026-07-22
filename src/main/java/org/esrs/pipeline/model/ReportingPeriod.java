package org.esrs.pipeline.model;

import java.time.LocalDate;

public record ReportingPeriod(LocalDate startDate, LocalDate endDate, boolean instant) {
}
