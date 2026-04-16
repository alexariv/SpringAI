package springAI.semantic;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);
    private static final long CACHE_TTL_SECONDS = 900; // 15 minutes

    private final RestTemplate restTemplate;
    private final String ologBaseUrl;

    private volatile List<String> cachedTags = Collections.emptyList();
    private volatile List<String> cachedLogbooks = Collections.emptyList();
    private volatile Instant lastFetched = Instant.EPOCH;

    public MetadataService(RestTemplate restTemplate,
                           @Value("${olog.base-url}") String ologBaseUrl) {
        this.restTemplate = restTemplate;
        this.ologBaseUrl = ologBaseUrl;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public List<String> getTags() {
        refreshIfStale();
        return cachedTags;
    }

    public List<String> getLogbooks() {
        refreshIfStale();
        return cachedLogbooks;
    }

    private void refreshIfStale() {
        if (Instant.now().isAfter(lastFetched.plusSeconds(CACHE_TTL_SECONDS))) {
            refresh();
        }
    }

    private synchronized void refresh() {
        if (Instant.now().isBefore(lastFetched.plusSeconds(CACHE_TTL_SECONDS))) {
            return;
        }
        try {
            cachedTags = fetchTags();
            cachedLogbooks = fetchLogbooks();
            lastFetched = Instant.now();
            logger.info("Refreshed metadata: {} tags, {} logbooks",
                    cachedTags.size(), cachedLogbooks.size());
        } catch (Exception e) {
            logger.error("Failed to refresh metadata, keeping stale cache: {}", e.getMessage());
        }
    }

    private List<String> fetchTags() {
        List<OlogTag> tags = restTemplate.exchange(
                ologBaseUrl + "/tags",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<OlogTag>>() {}
        ).getBody();

        if (tags == null) return Collections.emptyList();

        return tags.stream()
                .filter(t -> t.getName() != null)
                .map(OlogTag::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> fetchLogbooks() {
        List<OlogLogbook> logbooks = restTemplate.exchange(
                ologBaseUrl + "/logbooks",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<OlogLogbook>>() {}
        ).getBody();

        if (logbooks == null) return Collections.emptyList();

        return logbooks.stream()
                .filter(l -> l.getName() != null)
                .map(OlogLogbook::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    // Simple response DTOs matching the Olog API shape
    public static class OlogTag {
        private String name;
        private String state;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    public static class OlogLogbook {
        private String name;
        private String owner;
        private String state;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOwner() { return owner; }
        public void setState(String state) { this.state = state; }
        public String getState() { return state; }
    }
}