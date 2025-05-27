package com.example.gateway.routinglimiting.ratelimiting;

import com.example.gateway.core.model.TransformedDataEvent; // Input event
import com.example.gateway.core.model.SourceInfo;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // TODO: Configuration for rate limits will be externalized later.
    // For now, using some hardcoded defaults for demonstration.

    public RateLimitingService() {
        // Example: Create a default bucket for anonymous users or a global default
        // This is just an example; dynamic creation based on config is the goal.
        // createBucket("anonymous", 10, Duration.ofSeconds(1)); // 10 requests per second
        // createBucket("high-priority-source:kafka://some-topic", 100, Duration.ofSeconds(1));
    }

    /**
     * Creates or reconfigures a bucket.
     * In a real system, this would be driven by configuration updates.
     */
    public Bucket createBucket(String bucketId, long capacity, Duration refillPeriod) {
        Refill refill = Refill.greedy(capacity, refillPeriod);
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        Bucket bucket = Bucket.builder().addLimit(limit).build();
        buckets.put(bucketId, bucket);
        logger.info("Created/Reconfigured rate limit bucket ID '{}' with capacity {} per {}", bucketId, capacity, refillPeriod);
        return bucket;
    }


    /**
     * Attempts to consume a token from the appropriate bucket for the given event.
     *
     * @param event The TransformedDataEvent to rate limit.
     * @return true if consumption is allowed, false otherwise.
     */
    public boolean tryConsume(TransformedDataEvent event) {
        if (event == null) {
            logger.warn("TransformedDataEvent is null, cannot apply rate limiting.");
            return true; // Or false, depending on policy for null events
        }

        String bucketId = determineBucketId(event);
        Bucket bucket = buckets.get(bucketId);

        if (bucket == null) {
            // Fallback or default bucket strategy if no specific bucket found.
            // For now, if no specific bucket, try a generic one or permit.
            // A more robust system would load these from config or have a clear default.
            logger.warn("No specific rate limit bucket found for ID '{}' (derived from request {}). Attempting 'default_general' bucket.", bucketId, event.getRequestId());
            bucket = buckets.computeIfAbsent("default_general", id -> {
                logger.info("Creating default_general bucket (1000 tokens per minute) as it was not found.");
                return createBucket(id, 1000, Duration.ofMinutes(1));
            });

            // If even default cannot be created or policy is to deny if no specific bucket:
            // logger.warn("No rate limit bucket found for ID '{}' (request {}). Denying request.", bucketId, event.getRequestId());
            // return false;
        }

        boolean consumed = bucket.tryConsume(1); // Try to consume 1 token

        if (consumed) {
            logger.debug("Token consumed from bucket '{}' for request {}", bucketId, event.getRequestId());
            return true;
        } else {
            logger.warn("Rate limit exceeded for bucket '{}' (request {}).", bucketId, event.getRequestId());
            return false;
        }
    }

    /**
     * Determines the bucket ID based on the event.
     * This logic will be enhanced by configuration later (e.g., using SpEL on event fields).
     *
     * @param event The TransformedDataEvent.
     * @return A string representing the bucket ID.
     */
    private String determineBucketId(TransformedDataEvent event) {
        // Example logic:
        // 1. Check for API Key in metadata (e.g., from original HTTP headers)
        // 2. Check for User ID or Client ID in payload (if parsed and available)
        // 3. Use SourceInfo (e.g., Kafka topic, HTTP path)
        // 4. Default to a general bucket or one based on source IP

        if (event.getProcessingMetadata() != null && event.getProcessingMetadata().containsKey("X-API-Key")) {
            return "apikey:" + event.getProcessingMetadata().get("X-API-Key");
        }

        SourceInfo sourceInfo = event.getSourceInfo();
        if (sourceInfo != null) {
            // Example: "protocol:target" -> "HTTP:/ingest/someSource" or "KAFKA:my-topic"
            return sourceInfo.getProtocol().name() + ":" + sourceInfo.getRequestTarget();
        }

        // Fallback to a generic ID if no specific criteria match
        return "general_anonymous_source";
    }

    // Method to allow dynamic configuration (will be called by a config management component later)
    public void updateRateLimit(String bucketId, long capacity, Duration refillPeriod) {
        createBucket(bucketId, capacity, refillPeriod);
        logger.info("Rate limit updated for bucket ID '{}': capacity {}, period {}", bucketId, capacity, refillPeriod);
    }

    public Map<String, Bucket> getBuckets() {
        return buckets;
    }
}
