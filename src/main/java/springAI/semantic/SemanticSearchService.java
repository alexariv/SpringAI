package springAI.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public SimpleSearchResponse search(SearchQueryRequest request) {
        
        QueryPlan plan = plannerService.plan(request.getQuery());

        String semanticQuery = plan.getSemanticQuery();
        String llmFilter = plan.getFilterExpression();   // can be null
        String dateFilter = buildDateFilter(
                request.getCreatedDateFrom(),
                request.getCreatedDateTo()
        );
        String logbookTagFilter = buildlogbookTagFilter(request.getLogbooks(), request.getTags());

        String finalFilterExpression = combineFilters(llmFilter, dateFilter, logbookTagFilter);

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
    
    public AnalysisResponse analyze(AnalysisRequest request) {
    SearchQueryRequest searchReq = new SearchQueryRequest();
    searchReq.setQuery(request.getQuery());
    SimpleSearchResponse searchResult = search(searchReq);
    String analysis = analyzeWithLlm(request.getQuery(), searchResult.getHits());
    return new AnalysisResponse(analysis, searchResult.getHits());
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

        // Build for modifyDate
       List<String> modifyParts = new ArrayList<>();
        if (hasFrom) modifyParts.add("modifyDate >= '" + fromDate + "'");
        if (hasTo)   modifyParts.add("modifyDate < '"  + toDate   + "'");
        if (!modifyParts.isEmpty()) {
            fieldFilters.add("(" + String.join(" && ", modifyParts) + ")");
        }

        if (fieldFilters.isEmpty()) return null;

        // Entry falls in range if either createdDate OR modifyDate matches
        return "(" + String.join(" || ", fieldFilters) + ")";
    }

    
    private String buildlogbookTagFilter(List<String> logbooks, List<String> tags) {
        List<String> parts = new ArrayList<>();
        if (logbooks != null && !logbooks.isEmpty()) {
            if (logbooks.size() == 1) {
            parts.add("logbooks_name == '" + logbooks.get(0) + "'");
            } else {
                String logbookOr = logbooks.stream()
                .map(lb -> "logbooks_name == '" + lb + "'")
                .collect(Collectors.joining(" || "));
                parts.add("(" + logbookOr + ")");
            }
    }
    
    if (tags != null && !tags.isEmpty()) {
        String tagsList = tags.stream()
            .map(t -> "'" + t + "'")
            .collect(Collectors.joining(", "));
            parts.add("tags_name in [" + tagsList + "]");
    }
    
    if (parts.isEmpty()) {
        return null;
    }
    
    return String.join(" && ", parts);
    }

    private String combineFilters(String llmFilter, String dateFilter, String uiFilter) {
        List<String> nonNullFilters = new ArrayList<>();
        
        if (StringUtils.hasText(llmFilter)) {
            nonNullFilters.add(llmFilter);}

        if (StringUtils.hasText(dateFilter)) {
            nonNullFilters.add(dateFilter);}

        if (StringUtils.hasText(uiFilter)) {
            nonNullFilters.add(uiFilter);}
        
        if (nonNullFilters.isEmpty()) {
            return null; }
    
    return String.join(" && ", nonNullFilters);
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
            sb.append("title and description: ").append(h.getContent()).append("\n");

            if (h.getMetadata() != null) {
                Object owner = h.getMetadata().get("owner");
                Object title = h.getMetadata().get("title");
                Object createdDate = h.getMetadata().get("createdDate");
                Object modifyDate = h.getMetadata().get("modifyDate");
                Object level = h.getMetadata().get("level");
                Object state = h.getMetadata().get("state");
                Object logbook = h.getMetadata().get("logbooks_name");
                Object tags = h.getMetadata().get("tags_name");
                Object events = h.getMetadata().get("events_name");


                if (owner != null) {
                    sb.append("owner: ").append(owner).append("\n");
                }
                if (title != null) {
                    sb.append("title: ").append(title).append("\n");
                }
                if (createdDate != null) {
                    sb.append("createdDate: ").append(createdDate).append("\n");
                }
                if (modifyDate != null) {
                    sb.append("modifyDate: ").append(modifyDate).append("\n");
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
                if (events != null) {
                    sb.append("events: ").append(events).append("\n");
                }
            }
            sb.append("\n");
        }

        String context = sb.toString();

        String systemPrompt = """
            You are an expert system administrator analyzing log entries.
                - Your task is to determine what the log entries show in relation to the user's question.
                - Focus on extracting insights from the log content and metadata.
                - Pay special attention to any patterns, anomalies, or important details that relate to the question.
            
            CRITICAL FORMATTING RULE:
            - Every time you reference a specific log entry, you MUST cite it using the format #N (e.g., #1, #5, #12).
            - If multiple entries support the same point, list them all: (#3, #7, #11).
            
            
            RESPONSE STRUCTURE:
            **Summary**
            - 2-3 bulletpoint overview of what the logs show in relation to the question. Cite entries inline as #N.
            
            **Key Findings**
            - List 3-5 specific observations. 
            - Point out anything important (e.g. major alarms, repeated issues, who owns the entries).
            - Always cite the relevant log entries for each finding using #N format.
            
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

