flowchart TD

subgraph group_core["Pipeline Core"]
  node_application["Java application<br/>Maven entry point"]
  node_orchestrator["Reporting orchestrator<br/>pipeline coordinator"]
  node_ingestion["API ingestion<br/>input adapter"]
  node_envelope["Report envelope<br/>shared report model"]
  node_sample_inputs["Representative JSON input<br/>input fixtures"]
end

subgraph group_taxonomy["Mapping &amp; Taxonomy"]
  node_mapping_registry["Mapping registry<br/>mapping loader"]
  node_mapping_set["ESRS mapping set<br/>mapping data"]
  node_mapping_validator["Mapping taxonomy validator<br/>compatibility gate"]
  node_taxonomy[("Local ESRS taxonomy<br/>authoritative taxonomy package<br/>[esrs_all.xsd]")]
end

subgraph group_generation["XBRL &amp; Presentation"]
  node_context_builder["Context builder<br/>XBRL context factory"]
  node_fact_builder["Fact builder<br/>XBRL fact factory<br/>[FactBuilder.java]"]
  node_instance_writer["XBRL instance writer<br/>XBRL serializer"]
  node_xbrl_output["XBRL instance<br/>generated artifact"]
  node_template_renderer["iXBRL template renderer<br/>XHTML renderer"]
  node_embedding["iXBRL embedding<br/>inline fact embedder"]
  node_viewer_exporter["Viewer exporter<br/>interactive HTML exporter"]
  node_layout_map["Report layout map<br/>presentation mapping"]
  node_viewer_plugin["Bundled iXBRL viewer<br/>Python and browser plugin<br/>[iXBRLViewer.py]"]
end

subgraph group_quality["Validation &amp; Delivery"]
  node_arelle_validator["Arelle validator<br/>external CLI adapter"]
  node_validation_parser["Validation report parser<br/>issue parser"]
  node_taxonomy_visualizer["Taxonomy visualization exporter<br/>HTML analysis exporter"]
  node_strict_gate["Strict production gate<br/>PowerShell quality gate"]
  node_ci["GitHub Actions CI<br/>continuous integration"]
end

node_application -->|"starts"| node_orchestrator
node_sample_inputs -->|"supplies"| node_ingestion
node_ingestion -->|"adapts to"| node_envelope
node_orchestrator -->|"coordinates"| node_envelope
node_mapping_set -->|"loaded by"| node_mapping_registry
node_mapping_registry -->|"validates mappings"| node_mapping_validator
node_taxonomy -->|"defines compatibility"| node_mapping_validator
node_envelope -->|"entity, period, dimensions"| node_context_builder
node_envelope -->|"disclosures"| node_fact_builder
node_mapping_registry -->|"resolves concepts"| node_fact_builder
node_context_builder -->|"contexts and units"| node_instance_writer
node_fact_builder -->|"facts"| node_instance_writer
node_instance_writer -->|"writes"| node_xbrl_output
node_envelope -->|"rendering context"| node_template_renderer
node_layout_map -->|"places report sections"| node_template_renderer
node_template_renderer -->|"XHTML base"| node_embedding
node_fact_builder -->|"inline facts"| node_embedding
node_embedding -->|"iXBRL document"| node_viewer_exporter
node_viewer_plugin -->|"viewer bundle"| node_viewer_exporter
node_xbrl_output -->|"validates"| node_arelle_validator
node_arelle_validator -->|"CLI logs"| node_validation_parser
node_validation_parser -->|"validation issues"| node_envelope
node_taxonomy -->|"analyzes"| node_taxonomy_visualizer
node_mapping_set -->|"analyzes mappings"| node_taxonomy_visualizer
node_layout_map -->|"analyzes allocation"| node_taxonomy_visualizer
node_strict_gate -.->|"controls execution"| node_arelle_validator
node_strict_gate -.->|"requires plugin"| node_viewer_exporter
node_ci -->|"builds and runs"| node_application

click node_application "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/EsrsPipelineApplication.java"
click node_orchestrator "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/orchestration/ReportingPipelineOrchestrator.java"
click node_ingestion "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/api/ApiIngestionService.java"
click node_envelope "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/model/ReportEnvelope.java"
click node_sample_inputs "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/resources/testdata/fictive-esrs-input-full.json"
click node_mapping_registry "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/mapping/MappingRegistry.java"
click node_mapping_set "https://github.com/pseboek/xbrl-taxonomy/blob/main/mapping/map-esrs-2023-12-22.json"
click node_mapping_validator "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/mapping/MappingTaxonomyValidator.java"
click node_taxonomy "https://github.com/pseboek/xbrl-taxonomy/blob/main/xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd"
click node_context_builder "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/xbrl/context/ContextBuilder.java"
click node_fact_builder "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/xbrl/fact/FactBuilder.java"
click node_instance_writer "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/xbrl/serializer/XbrlInstanceWriter.java"
click node_template_renderer "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/ixbrl/template/IxbrlTemplateRenderer.java"
click node_embedding "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/ixbrl/embedding/IxbrlEmbeddingService.java"
click node_viewer_exporter "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/ixbrl/viewer/IxbrlViewerExporter.java"
click node_layout_map "https://github.com/pseboek/xbrl-taxonomy/blob/main/mapping/report-layout-map.json"
click node_viewer_plugin "https://github.com/pseboek/xbrl-taxonomy/blob/main/arelle/plugin/iXBRLViewerPlugin/iXBRLViewer.py"
click node_arelle_validator "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/validation/arelle/ArelleValidator.java"
click node_validation_parser "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/validation/arelle/ValidationReportParser.java"
click node_taxonomy_visualizer "https://github.com/pseboek/xbrl-taxonomy/blob/main/src/main/java/org/esrs/pipeline/visualization/TaxonomyVisualizationExporter.java"
click node_strict_gate "https://github.com/pseboek/xbrl-taxonomy/blob/main/scripts/run-strict-production-gate.ps1"
click node_ci "https://github.com/pseboek/xbrl-taxonomy/blob/main/.github/workflows/ci-xbrl-ixbrl-java.yml"

classDef toneNeutral fill:#f8fafc,stroke:#334155,stroke-width:1.5px,color:#0f172a
classDef toneBlue fill:#dbeafe,stroke:#2563eb,stroke-width:1.5px,color:#172554
classDef toneAmber fill:#fef3c7,stroke:#d97706,stroke-width:1.5px,color:#78350f
classDef toneMint fill:#dcfce7,stroke:#16a34a,stroke-width:1.5px,color:#14532d
classDef toneRose fill:#ffe4e6,stroke:#e11d48,stroke-width:1.5px,color:#881337
classDef toneIndigo fill:#e0e7ff,stroke:#4f46e5,stroke-width:1.5px,color:#312e81
classDef toneTeal fill:#ccfbf1,stroke:#0f766e,stroke-width:1.5px,color:#134e4a
class node_application,node_orchestrator,node_ingestion,node_envelope,node_sample_inputs toneBlue
class node_mapping_registry,node_mapping_set,node_mapping_validator,node_taxonomy toneAmber
class node_context_builder,node_fact_builder,node_instance_writer,node_xbrl_output,node_template_renderer,node_embedding,node_viewer_exporter,node_layout_map,node_viewer_plugin toneMint
class node_arelle_validator,node_validation_parser,node_taxonomy_visualizer,node_strict_gate,node_ci toneRose