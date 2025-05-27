// scripts/map_fields.groovy
// Example script to map fields from an input Map (e.g., parsed JSON)

// Access bound variables
//def payload = inputPayload // The data from NormalizedDataEvent.parsedPayload
//def sInfo = sourceInfo
//def resultsCtx = scriptResultsCtx // Mutable map for results

logger.info("Executing map_fields.groovy for source: " + sourceInfo.requestTarget)

if (!(inputPayload instanceof Map)) {
    logger.warn("Input payload is not a Map, skipping transformation. Type: " + (inputPayload == null ? "null" : inputPayload.getClass().name))
    // To signal an error and stop further processing in a chain:
    // throw new RuntimeException("Input payload is not a Map for map_fields.groovy")
    return inputPayload // Return original payload if not a map
}

def output = [:]
output.new_id = inputPayload.old_id ?: "default_id" // Use old_id or a default
output.transformed_value = (inputPayload.value ?: 0) * 10
output.source_protocol = sourceInfo.protocol.toString()
output.static_field = "Transformed by Groovy"
output.original_payload_type = inputPayloadType.toString()


// Example: adding custom metadata
def newMetadata = [:]
newMetadata.put("transformation_script_applied", "map_fields.groovy")
newMetadata.put("transformation_timestamp", System.currentTimeMillis().toString())
resultsCtx.put("processingMetadata", newMetadata)

// Example: changing payload type (if needed)
// resultsCtx.put("outputPayloadType", "JSON") // Assuming output is still effectively JSON

logger.info("map_fields.groovy transformation complete.")
return output // The transformed payload
