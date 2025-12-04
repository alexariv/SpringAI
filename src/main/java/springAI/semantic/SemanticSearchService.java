package springAI.semantic;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;

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
        String llmFilter = plan.getFilterExpression();   //can be null
        String dateFilter = buildDateFilter(request.getCreatedDateFrom(), request.getCreatedDateTo());

        //filterExpression
        String finalFilterExpression = combineFilters(llmFilter, dateFilter);

        //SearchRequest
        SearchRequest searchRequest = SearchRequest.builder()
                .query(semanticQuery)
                .topK(20)  
                .filterExpression(finalFilterExpression)  // can be null
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    private String buildDateFilter(String from, String to) {
        List<String> parts = new ArrayList<>();

        if (from != null && !from.isBlank()) {
            parts.add("createdDate >= '" + from + "'");
        }
        if (to != null && !to.isBlank()) {
            parts.add("createdDate < '" + to + "'");
        }

        if (parts.isEmpty()) {
            return null;
        }

        return String.join(" && ", parts);
    }

    private String combineFilters(String llmFilter, String dateFilter) {
        if (llmFilter == null || llmFilter.isBlank()) {
            return (dateFilter == null || dateFilter.isBlank()) ? null : dateFilter;
        }
        if (dateFilter == null || dateFilter.isBlank()) {
            return llmFilter;
        }
        return llmFilter + " && " + dateFilter;
    }
}
