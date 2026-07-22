package org.esrs.pipeline.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public record PipelineConfig(
    Path root,
    Path inputJson,
    Path mappingFile,
    Path templateFile,
    Path layoutMap,
    Path outputDir,
    Path taxonomyRoot,
    Path mappingScopeFile,
    String arelleCommand,
    boolean skipArelle,
    boolean failOnValidationIssues,
    boolean requireViewerPlugin,
    boolean enforceMappingScope,
    String arelleDisclosureSystem,
    String arelleLogFormat,
    String ixbrlViewerPlugin
) {
    public static PipelineConfig load(Path root) throws IOException {
        return load(root, System.getenv());
    }

    static PipelineConfig load(Path root, Map<String, String> env) throws IOException {
        Properties defaults = loadDefaults();

        Path resolvedRoot = root.toAbsolutePath().normalize();
        Path inputJson = resolvedRoot.resolve(get(defaults, env, "pipeline.inputJson", "PIPELINE_INPUT_JSON"));
        Path mappingFile = resolvedRoot.resolve(get(defaults, env, "pipeline.mappingFile", "PIPELINE_MAPPING_FILE"));
        Path templateFile = resolvedRoot.resolve(get(defaults, env, "pipeline.templateFile", "PIPELINE_TEMPLATE_FILE"));
        Path layoutMap = resolvedRoot.resolve(get(defaults, env, "pipeline.layoutMap", "PIPELINE_LAYOUT_MAP"));
        Path outputDir = resolvedRoot.resolve(get(defaults, env, "pipeline.outputDir", "PIPELINE_OUTPUT_DIR"));
        Path taxonomyRoot = resolvedRoot.resolve(get(defaults, env, "pipeline.taxonomyRoot", "PIPELINE_TAXONOMY_ROOT"));
        Path mappingScopeFile = resolvedRoot.resolve(get(defaults, env, "pipeline.mappingScopeFile", "PIPELINE_MAPPING_SCOPE_FILE"));

        String arelleCommand = get(defaults, env, "pipeline.arelle.command", "ARELLE_CMD");
        boolean skipArelle = getBoolean(defaults, env, "pipeline.arelle.skip", "SKIP_ARELLE");
        boolean failOnValidationIssues = getBoolean(defaults, env, "pipeline.validation.failOnIssues", "FAIL_ON_VALIDATION_ISSUES");
        boolean requireViewerPlugin = getBoolean(defaults, env, "pipeline.viewer.requirePlugin", "REQUIRE_VIEWER_PLUGIN");
        boolean enforceMappingScope = getBoolean(defaults, env, "pipeline.mappingScope.enforce", "ENFORCE_MAPPING_SCOPE");

        String disclosureSystem = get(defaults, env, "pipeline.arelle.disclosureSystem", "ARELLE_DISCLOSURE_SYSTEM");
        String logFormat = get(defaults, env, "pipeline.arelle.logFormat", "ARELLE_LOG_FORMAT");
        String viewerPlugin = get(defaults, env, "pipeline.viewer.plugin", "IXBRL_VIEWER_PLUGIN");

        return new PipelineConfig(
            resolvedRoot,
            inputJson,
            mappingFile,
            templateFile,
            layoutMap,
            outputDir,
            taxonomyRoot,
            mappingScopeFile,
            arelleCommand,
            skipArelle,
            failOnValidationIssues,
            requireViewerPlugin,
            enforceMappingScope,
            toNullable(disclosureSystem),
            toNullable(logFormat),
            toNullable(viewerPlugin)
        );
    }

    private static Properties loadDefaults() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PipelineConfig.class.getResourceAsStream("/pipeline-defaults.properties")) {
            if (in == null) {
                throw new IOException("Missing configuration resource: /pipeline-defaults.properties");
            }
            properties.load(in);
        }
        return properties;
    }

    private static String get(Properties defaults, Map<String, String> env, String key, String envName) {
        String envValue = env.get(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaults.getProperty(key);
    }

    private static boolean getBoolean(Properties defaults, Map<String, String> env, String key, String envName) {
        return Boolean.parseBoolean(get(defaults, env, key, envName));
    }

    private static String toNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}