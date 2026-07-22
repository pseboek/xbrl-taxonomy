package org.esrs.pipeline.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.esrs.pipeline.api.ApiIngestionService;
import org.esrs.pipeline.ixbrl.embedding.IxbrlEmbeddingService;
import org.esrs.pipeline.ixbrl.template.IxbrlTemplateRenderer;
import org.esrs.pipeline.ixbrl.viewer.IxbrlViewerExporter;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.mapping.MappingTaxonomyValidator;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ValidationIssue;
import org.esrs.pipeline.validation.arelle.ArelleValidator;
import org.esrs.pipeline.xbrl.context.ContextBuilder;
import org.esrs.pipeline.xbrl.fact.FactBuilder;
import org.esrs.pipeline.xbrl.serializer.XbrlInstanceWriter;

public class ReportingPipelineOrchestrator {
    private final ApiIngestionService ingestionService;
    private final ContextBuilder contextBuilder;
    private final FactBuilder factBuilder;
    private final XbrlInstanceWriter xbrlInstanceWriter;
    private final IxbrlTemplateRenderer templateRenderer;
    private final IxbrlEmbeddingService embeddingService;
    private final ArelleValidator arelleValidator;
    private final IxbrlViewerExporter viewerExporter;
    private final MappingTaxonomyValidator mappingTaxonomyValidator;

    public ReportingPipelineOrchestrator(String arelleCommand) {
        this.ingestionService = new ApiIngestionService();
        this.contextBuilder = new ContextBuilder();
        this.factBuilder = new FactBuilder();
        this.xbrlInstanceWriter = new XbrlInstanceWriter();
        this.templateRenderer = new IxbrlTemplateRenderer();
        this.embeddingService = new IxbrlEmbeddingService();
        this.arelleValidator = new ArelleValidator(arelleCommand);
        this.viewerExporter = new IxbrlViewerExporter(arelleCommand);
        this.mappingTaxonomyValidator = new MappingTaxonomyValidator();
    }

    public PipelineResult run(Path inputJson,
                              Path mappingFile,
                              Path templateFile,
                              Path layoutMap,
                              Path outputDir,
                              Path taxonomyRoot,
                              boolean skipArelle) throws IOException, InterruptedException {
        return run(inputJson, mappingFile, templateFile, layoutMap, outputDir, taxonomyRoot, skipArelle, false, false);
    }

    public PipelineResult run(Path inputJson,
                              Path mappingFile,
                              Path templateFile,
                              Path layoutMap,
                              Path outputDir,
                              Path taxonomyRoot,
                              boolean skipArelle,
                              boolean failOnValidationIssues,
                              boolean requireViewerPlugin) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);

        ReportEnvelope envelope = ingestionService.loadFromJson(inputJson);
        MappingRegistry mappingRegistry = MappingRegistry.fromPath(mappingFile);
        mappingTaxonomyValidator.validate(mappingRegistry, taxonomyRoot);

        ContextBuilder.ContextBuildResult contexts = contextBuilder.build(envelope);
        FactBuilder.FactBuildResult facts = factBuilder.build(envelope, mappingRegistry, contexts.fieldOccurrenceContext());

        Path xbrlOut = outputDir.resolve("report-instance.xml");
        String schemaRefHref = "../xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd";
        xbrlInstanceWriter.write(
            xbrlOut,
            envelope,
            contexts.contexts(),
            facts.facts(),
            schemaRefHref
        );

        String renderedTemplate = templateRenderer.render(templateFile, envelope);
        String ixbrl = embeddingService.embed(
            renderedTemplate,
            facts.facts(),
            layoutMap,
            contexts.contexts(),
            schemaRefHref
        );
        Path ixbrlOut = outputDir.resolve("report-ixbrl.xhtml");
        embeddingService.writeOutput(ixbrlOut, ixbrl);

        List<ValidationIssue> validationIssues = new ArrayList<>();
        if (!skipArelle) {
            validationIssues.addAll(arelleValidator.validate(xbrlOut, taxonomyRoot, outputDir.resolve("arelle-xbrl.log")));
            validationIssues.addAll(arelleValidator.validate(ixbrlOut, taxonomyRoot, outputDir.resolve("arelle-ixbrl.log")));
        } else {
            validationIssues.add(new ValidationIssue("INFO", "ARELLE_SKIPPED", "Arelle validation skipped by configuration."));
            Files.writeString(outputDir.resolve("arelle-xbrl.log"), "Arelle skipped.\n", StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("arelle-ixbrl.log"), "Arelle skipped.\n", StandardCharsets.UTF_8);
        }

        Path viewerOut = outputDir.resolve("report-interaktiv.html");
        IxbrlViewerExporter.ViewerExportResult viewerExportResult;
        if (!skipArelle) {
            viewerExportResult = viewerExporter.export(ixbrlOut, viewerOut);
            if (requireViewerPlugin && viewerExportResult.fallbackUsed()) {
                validationIssues.add(new ValidationIssue(
                    "ERROR",
                    "VIEWER_PLUGIN_REQUIRED",
                    "Viewer plugin is required but fallback export was used. " + viewerExportResult.reason()
                ));
            }
        } else {
            Files.writeString(
                viewerOut,
                "<!doctype html><html><head><meta charset=\"utf-8\"><title>Interaktive Berichtssicht</title></head>"
                    + "<body><h1>Interaktive Berichtssicht (Stub)</h1><p>Viewer-Export wurde im Testlauf uebersprungen.</p></body></html>",
                StandardCharsets.UTF_8
            );
            viewerExportResult = new IxbrlViewerExporter.ViewerExportResult(false, 0, "Viewer export skipped by configuration.");
        }

        if (failOnValidationIssues && hasBlockingIssues(validationIssues)) {
            throw new IllegalStateException("Validation gate failed: " + summarize(validationIssues));
        }

        return new PipelineResult(xbrlOut, ixbrlOut, viewerOut, validationIssues, viewerExportResult.fallbackUsed());
    }

    private boolean hasBlockingIssues(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if ("ERROR".equalsIgnoreCase(issue.severity())) {
                return true;
            }
        }
        return false;
    }

    private String summarize(List<ValidationIssue> issues) {
        StringBuilder sb = new StringBuilder();
        for (ValidationIssue issue : issues) {
            if ("ERROR".equalsIgnoreCase(issue.severity())) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                sb.append(issue.code()).append(": ").append(issue.message());
            }
        }
        return sb.toString();
    }

    public record PipelineResult(Path xbrlPath,
                                 Path ixbrlPath,
                                 Path interactiveHtmlPath,
                                 List<ValidationIssue> validationIssues,
                                 boolean viewerFallbackUsed) {
    }
}
