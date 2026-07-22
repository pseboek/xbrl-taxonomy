package org.esrs.pipeline.ixbrl.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.esrs.pipeline.model.ReportEnvelope;

public class IxbrlTemplateRenderer {
    public String render(Path templatePath, ReportEnvelope envelope) throws IOException {
        String html = Files.readString(templatePath, StandardCharsets.UTF_8);
        html = html.replace("{{entityName}}", safe(envelope.entity().name()));
        html = html.replace("{{entityIdentifier}}", safe(envelope.entity().identifier()));
        html = html.replace("{{periodStart}}", envelope.period().startDate().toString());
        html = html.replace("{{periodEnd}}", envelope.period().endDate().toString());
        return html;
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }
}
