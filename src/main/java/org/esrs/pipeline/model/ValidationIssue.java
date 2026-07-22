package org.esrs.pipeline.model;

public record ValidationIssue(String severity, String code, String message) {
}
