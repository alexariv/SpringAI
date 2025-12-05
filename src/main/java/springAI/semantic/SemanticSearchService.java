package springAI.semantic;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SemanticSearchService {

    private final QueryPlannerService plannerService;
    private final ElasticsearchVectorStore vectorStore;

    public SemanticSearchService(QueryPlannerService plannerService,
                                 ElasticsearchVectorStore vectorStore) {
        this.plannerService = plannerService;
        this.vectorStore = vectorStore;
    }

    public List<Document> search(SearchQueryRequest request) {

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

        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * Build a filter expression that checks either createdDate or eventStart
     * falls inside the given date range.
     *
     * Dates coming from UI are yyyy-MM-dd (date-only).
     * Elasticsearch date fields (createdDate, eventStart) are strings like:
     * "2025-11-18T16:11:39.672-05:00"
     *
     * When you compare them to "2025-11-18" or "2025-11-19", ES will interpret them
     * as instants and apply normal date comparisons, so you don't have to manually
     * add time components.
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
            // Use < to treat the end date as exclusive (i.e., up to end of that day)
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
}

