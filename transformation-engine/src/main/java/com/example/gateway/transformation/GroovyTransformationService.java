package com.example.gateway.transformation;

import com.example.gateway.core.model.NormalizedDataEvent;
import com.example.gateway.core.model.PayloadType;
import com.example.gateway.core.model.TransformedDataEvent;
import com.example.gateway.core.model.TransformationStatus;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct; // Ensure this is used if on Spring Boot 3+
// import javax.annotation.PostConstruct; // For Spring Boot 2.x or Java EE environments

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GroovyTransformationService {

    private static final Logger logger = LoggerFactory.getLogger(GroovyTransformationService.class);
    private final GroovyShell groovyShell;
    private final Map<String, Script> scriptCache = new ConcurrentHashMap<>();

    @Value("${gateway.transformation-engine.script-location-pattern:classpath:/scripts/**/*.groovy}")
    private String scriptLocationPattern;

    @Value("${gateway.transformation-engine.cache-scripts:true}")
    private boolean cacheScripts;

    public GroovyTransformationService() {
        // Consider adding CompilerConfiguration for sandboxing if scripts are untrusted
        // CompilerConfiguration compilerConfig = new CompilerConfiguration();
        // SecureASTCustomizer customizer = new SecureASTCustomizer();
        // customizer.setImportsBlacklist(Arrays.asList("java.io.File")); // Example restriction
        // compilerConfig.addCompilationCustomizers(customizer);
        // this.groovyShell = new GroovyShell(compilerConfig);
        this.groovyShell = new GroovyShell(); // Default GroovyShell
    }

    @PostConstruct
    public void loadAllScripts() {
        logger.info("Loading Groovy transformation scripts from pattern: {}", scriptLocationPattern);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        int scriptsLoaded = 0;
        try {
            Resource[] scriptResources = resolver.getResources(scriptLocationPattern);
            for (Resource resource : scriptResources) {
                String scriptName = resource.getFilename();
                if (scriptName == null || !scriptName.endsWith(".groovy")) {
                    logger.warn("Skipping non-groovy file: {}", resource.getFilename());
                    continue;
                }
                // Use filename without .groovy as script ID
                String scriptId = scriptName.substring(0, scriptName.length() - ".groovy".length());
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    Script script = groovyShell.parse(reader);
                    if (cacheScripts) {
                        scriptCache.put(scriptId, script);
                    }
                    logger.info("Loaded and parsed Groovy script: {} (ID: {})", resource.getFilename(), scriptId);
                    scriptsLoaded++;
                } catch (CompilationFailedException | IOException e) {
                    logger.error("Failed to load or parse Groovy script: {}", resource.getFilename(), e);
                }
            }
            logger.info("Total Groovy scripts loaded: {}", scriptsLoaded);
        } catch (IOException e) {
            logger.error("Error scanning for Groovy scripts at location pattern {}: {}", scriptLocationPattern, e.getMessage(), e);
        }
    }


    public TransformedDataEvent transform(NormalizedDataEvent event, List<String> scriptIdsToApply) {
        TransformedDataEvent transformedEvent = new TransformedDataEvent();
        transformedEvent.setRequestId(event.getRequestId());
        transformedEvent.setSourceInfo(event.getSourceInfo());
        transformedEvent.setProcessingMetadata(new HashMap<>(event.getProcessingMetadata() != null ? event.getProcessingMetadata() : Collections.emptyMap())); // Copy metadata

        Object currentPayload = event.getParsedPayload();
        PayloadType currentPayloadType = event.getProcessedPayloadType(); // Type of event.getParsedPayload()
        List<String> transformationErrors = new ArrayList<>();
        TransformationStatus overallStatus = TransformationStatus.SUCCESS;

        if (scriptIdsToApply == null || scriptIdsToApply.isEmpty()) {
            logger.debug("No transformation scripts specified for request {}", event.getRequestId());
            transformedEvent.setTransformedPayload(currentPayload);
            transformedEvent.setPayloadType(currentPayloadType);
            transformedEvent.setStatus(TransformationStatus.SUCCESS);
            return transformedEvent;
        }

        for (String scriptId : scriptIdsToApply) {
            Script script = getScript(scriptId);
            if (script == null) {
                logger.error("Transformation script with ID '{}' not found for request {}.", scriptId, event.getRequestId());
                transformationErrors.add("Script not found: " + scriptId);
                overallStatus = TransformationStatus.FAILURE;
                break; // Stop processing this chain
            }

            Binding binding = new Binding();
            binding.setVariable("inputPayload", currentPayload);
            binding.setVariable("inputPayloadType", currentPayloadType); // Pass the type of the inputPayload
            binding.setVariable("sourceInfo", event.getSourceInfo());
            // Pass a mutable map for results like outputPayloadType, additional metadata
            Map<String, Object> scriptResultsCtx = new HashMap<>();
            binding.setVariable("scriptResultsCtx", scriptResultsCtx);
            binding.setVariable("logger", LoggerFactory.getLogger("GroovyScript." + scriptId)); // Script-specific logger

            script.setBinding(binding);

            try {
                logger.debug("Executing Groovy script '{}' for request {}", scriptId, event.getRequestId());
                Object result = script.run();
                currentPayload = result; // Output of one script is input to next

                // Script can change payload type via scriptResultsCtx
                if (scriptResultsCtx.containsKey("outputPayloadType")) {
                    try {
                        currentPayloadType = PayloadType.valueOf(String.valueOf(scriptResultsCtx.get("outputPayloadType")));
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Script {} for request {} tried to set invalid outputPayloadType: {}. Retaining previous type: {}",
                                scriptId, event.getRequestId(), scriptResultsCtx.get("outputPayloadType"), currentPayloadType);
                        transformationErrors.add("Invalid outputPayloadType '" + scriptResultsCtx.get("outputPayloadType") + "' from script " + scriptId);
                        // Depending on policy, this could be a failure. For now, just a warning.
                    }
                }
                 // Scripts can add to processingMetadata via scriptResultsCtx.processingMetadata
                if (scriptResultsCtx.containsKey("processingMetadata")) {
                    Object metaObj = scriptResultsCtx.get("processingMetadata");
                    if (metaObj instanceof Map) {
                        try {
                            @SuppressWarnings("unchecked") // Suppress warning for this specific cast
                            Map<String, String> additionalMetadata = (Map<String, String>) metaObj;
                            transformedEvent.getProcessingMetadata().putAll(additionalMetadata);
                        } catch (ClassCastException e) {
                            logger.warn("Script {} for request {} provided 'processingMetadata' in scriptResultsCtx that was not Map<String, String>.", scriptId, event.getRequestId());
                        }
                    }
                }

                logger.debug("Groovy script '{}' executed successfully for request {}", scriptId, event.getRequestId());
            } catch (Exception e) {
                logger.error("Error executing Groovy script '{}' for request {}: {}", scriptId, event.getRequestId(), e.getMessage(), e);
                transformationErrors.add("Error in script " + scriptId + ": " + e.getMessage());
                overallStatus = TransformationStatus.FAILURE;
                break; // Stop processing this chain
            }
        }

        transformedEvent.setTransformedPayload(currentPayload);
        transformedEvent.setPayloadType(currentPayloadType);
        transformedEvent.setStatus(overallStatus);
        transformedEvent.setTransformationErrors(transformationErrors);

        return transformedEvent;
    }

    private Script getScript(String scriptId) {
        if (cacheScripts && scriptCache.containsKey(scriptId)) {
            return scriptCache.get(scriptId);
        } else if (!cacheScripts) { // Load on demand if caching is disabled
            logger.debug("Script caching disabled. Attempting to load script: {}", scriptId);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // This is simplified; assumes scriptId directly maps to a filename in the known pattern.
            // A more robust approach would re-scan or have a direct path lookup.
            try {
                 // Construct a more specific pattern to find the script by ID
                String specificScriptPattern = scriptLocationPattern.replace("**/", "/**/").replace("*.groovy", scriptId + ".groovy");
                Resource[] scriptResources = resolver.getResources(specificScriptPattern);
                if (scriptResources.length > 0) {
                    Resource resource = scriptResources[0]; // Take the first match
                     if (resource.exists()) {
                        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                            return groovyShell.parse(reader);
                        }
                     }
                }
            } catch (IOException | CompilationFailedException e) {
                logger.error("Failed to load Groovy script on demand: {}", scriptId, e);
                return null;
            }
        }
        logger.warn("Script not found in cache (caching enabled) or failed to load on demand: {}", scriptId);
        return null;
    }
}
