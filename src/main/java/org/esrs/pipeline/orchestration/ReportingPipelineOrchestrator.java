package org.esrs.pipeline.orchestration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.esrs.pipeline.api.ApiIngestionService;
import org.esrs.pipeline.config.PipelineConfig;
import org.esrs.pipeline.ixbrl.embedding.IxbrlEmbeddingService;
import org.esrs.pipeline.ixbrl.template.IxbrlTemplateRenderer;
import org.esrs.pipeline.ixbrl.viewer.IxbrlViewerExporter;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.mapping.MappingScopeValidator;
import org.esrs.pipeline.mapping.MappingTaxonomyValidator;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.model.ValidationIssue;
import org.esrs.pipeline.validation.arelle.ArelleValidator;
import org.esrs.pipeline.xbrl.context.ContextBuilder;
import org.esrs.pipeline.xbrl.fact.FactBuilder;
import org.esrs.pipeline.xbrl.serializer.XbrlInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportingPipelineOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(ReportingPipelineOrchestrator.class);

    private final ApiIngestionService ingestionService;
    private final ContextBuilder contextBuilder;
    private final FactBuilder factBuilder;
    private final XbrlInstanceWriter xbrlInstanceWriter;
    private final IxbrlTemplateRenderer templateRenderer;
    private final IxbrlEmbeddingService embeddingService;
    private final ArelleValidator arelleValidator;
    private final IxbrlViewerExporter viewerExporter;
    private final MappingTaxonomyValidator mappingTaxonomyValidator;
    private final MappingScopeValidator mappingScopeValidator;
    private final PipelineConfig config;

    public ReportingPipelineOrchestrator(String arelleCommand) {
        this(new PipelineConfig(
            Path.of(".").toAbsolutePath().normalize(),
            Path.of("src/main/resources/testdata/fictive-esrs-input.json"),
            Path.of("mapping/map-esrs-2023-12-22.json"),
            Path.of("templates/report-base.xhtml"),
            Path.of("mapping/report-layout-map.json"),
            Path.of("output"),
            Path.of("."),
            Path.of("mapping/scopes/esrs-full-scope.json"),
            arelleCommand,
            true,
            true,
            false,
            true,
            null,
            null,
            "iXBRLViewerPlugin"
        ));
    }

    public ReportingPipelineOrchestrator(PipelineConfig config) {
        this.config = config;
        this.ingestionService = new ApiIngestionService();
        this.contextBuilder = new ContextBuilder();
        this.factBuilder = new FactBuilder();
        this.xbrlInstanceWriter = new XbrlInstanceWriter();
        this.templateRenderer = new IxbrlTemplateRenderer();
        this.embeddingService = new IxbrlEmbeddingService();
        this.arelleValidator = new ArelleValidator(config.arelleCommand(), config.arelleDisclosureSystem(), config.arelleLogFormat());
        this.viewerExporter = new IxbrlViewerExporter(config.arelleCommand(), config.ixbrlViewerPlugin());
        this.mappingTaxonomyValidator = new MappingTaxonomyValidator();
        this.mappingScopeValidator = new MappingScopeValidator();
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
        LOG.info("Pipeline run started. skipArelle={}, failOnValidationIssues={}, requireViewerPlugin={}",
            skipArelle,
            failOnValidationIssues,
            requireViewerPlugin);
        Files.createDirectories(outputDir);

        ReportEnvelope envelope = ingestionService.loadFromJson(inputJson);
        MappingRegistry mappingRegistry = MappingRegistry.fromPath(mappingFile);
        if (config.enforceMappingScope()) {
            mappingScopeValidator.validate(mappingRegistry, layoutMap, config.mappingScopeFile());
        }
        mappingTaxonomyValidator.validate(mappingRegistry, taxonomyRoot);

        ContextBuilder.ContextBuildResult contexts = contextBuilder.build(envelope, mappingRegistry);
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
            LOG.error("Validation gate failed with blocking issues.");
            throw new IllegalStateException("Validation gate failed: " + summarize(validationIssues));
        }

        LOG.info("Pipeline run completed. validationIssues={}, viewerFallbackUsed={}",
            validationIssues.size(),
            viewerExportResult.fallbackUsed());

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
