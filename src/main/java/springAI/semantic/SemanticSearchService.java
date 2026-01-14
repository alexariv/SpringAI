package springAI.semantic;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;

@Service
public class SemanticSearchService {

    private final QueryPlannerService plannerService;
    private final ElasticsearchVectorStore vectorStore;
    private final ChatClient chatClient;

    public SemanticSearchService(QueryPlannerService plannerService,
                                ElasticsearchVectorStore vectorStore,
                                ChatClient.Builder chatClientBuilder) {
        this.plannerService = plannerService;
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public SemanticSearchResponse search(SearchQueryRequest request) {
        
        QueryPlan plan = plannerService.plan(request.getQuery());

        String semanticQuery = plan.getSemanticQuery();
        String llmFilter = plan.getFilterExpression();   // can be null
        String dateFilter = buildDateFilter(
                request.getCreatedDateFrom(),
                request.getCreatedDateTo()
        );

        String finalFilterExpression = combineFilters(llmFilter, dateFilter);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(semanticQuery)
                .topK(20)
                .filterExpression(finalFilterExpression)  // can be null
                .build();
        List<Document> docs = vectorStore.similaritySearch(searchRequest);

        List<SearchHitDto> hits = docs.stream()
                .map(d -> new SearchHitDto(d.getText(), d.getMetadata()))
                .collect(Collectors.toList());

        // LLM analyze 
        //String analysis = analyzeWithLlm(request.getQuery(), hits);

        return new SimpleSearchResponse(hits);
    }

    /**
     * Build a filter expression that checks either createdDate OR eventStart
     * falls inside the given date range (yyyy-MM-dd).
     */
    private String buildDateFilter(String fromDate, String toDate) {
        boolean hasFrom = StringUtils.hasText(fromDate);
        boolean hasTo = StringUtils.hasText(toDate);

        if (!hasFrom && !hasTo) {
            return null;
        }

        List<String> fieldFilters = new ArrayList<>();

        // Build for createdDate
        List<String> createdParts = new ArrayList<>();
        if (hasFrom) {
            createdParts.add("createdDate >= '" + fromDate + "'");
        }
        if (hasTo) {
            createdParts.add("createdDate < '" + toDate + "'");
        }
        if (!createdParts.isEmpty()) {
            fieldFilters.add("(" + String.join(" && ", createdParts) + ")");
        }

        // Build for eventStart
        List<String> eventParts = new ArrayList<>();
        if (hasFrom) {
            eventParts.add("eventStart >= '" + fromDate + "'");
        }
        if (hasTo) {
            eventParts.add("eventStart < '" + toDate + "'");
        }
        if (!eventParts.isEmpty()) {
            fieldFilters.add("(" + String.join(" && ", eventParts) + ")");
        }

        if (fieldFilters.isEmpty()) {
            return null;
        }

        // Either createdDate in range OR eventStart in range
        return "(" + String.join(" || ", fieldFilters) + ")";
    }

    private String combineFilters(String llmFilter, String dateFilter) {
        boolean hasLlm = StringUtils.hasText(llmFilter);
        boolean hasDate = StringUtils.hasText(dateFilter);

        if (!hasLlm && !hasDate) {
            return null;
        }
        if (!hasLlm) {
            return dateFilter;
        }
        if (!hasDate) {
            return llmFilter;
        }
        return llmFilter + " && " + dateFilter;
    }
    private String analyzeWithLlm(String originalQuestion, List<SearchHitDto> hits) {

        if (hits.isEmpty()) {
            return "No matching log entries were found for this query.";
        }

        // LLM context
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SearchHitDto h = hits.get(i);
            sb.append("Entry #").append(i + 1).append(":\n");
            sb.append("description: ").append(h.getContent()).append("\n");

            if (h.getMetadata() != null) {
                Object owner = h.getMetadata().get("owner");
                Object createdDate = h.getMetadata().get("createdDate");
                Object level = h.getMetadata().get("level");
                Object state = h.getMetadata().get("state");
                Object logbook = h.getMetadata().get("logbooks_name");
                Object tags = h.getMetadata().get("tags_name");

                if (owner != null) {
                    sb.append("owner: ").append(owner).append("\n");
                }
                if (createdDate != null) {
                    sb.append("createdDate: ").append(createdDate).append("\n");
                }
                if (level != null) {
                    sb.append("level: ").append(level).append("\n");
                }
                if (state != null) {
                    sb.append("state: ").append(state).append("\n");
                }
                if (logbook != null) {
                    sb.append("logbook: ").append(logbook).append("\n");
                }
                if (tags != null) {
                    sb.append("tags: ").append(tags).append("\n");
                }
            }
            sb.append("\n");
        }

        String context = sb.toString();

        String systemPrompt = """
            You are an expert system administrator analyzing log entries.
            1. Briefly summarize what was found (mention key patterns: owners, logbooks, tags, severity/level).
            2. Point out anything important (e.g. major alarms, repeated issues, who owns the entries).
            3. If nothing is directly relevant, say so clearly.

            Be concise but specific. Do NOT invent entries that are not in the list.
            """;

        String userMessage = """
            User question:
            %s

            Retrieved log entries:
            %s

            Based on these entries, explain what they show and how they relate to the user's question.
            """.formatted(originalQuestion, context);

        return this.chatClient
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
}

