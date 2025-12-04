package springAI.semantic;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SemanticSearchController {

    private final SemanticSearchService searchService;

    public SemanticSearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }
    @GetMapping("/semantic")
    public List<Document> semanticSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "createdDateFrom", required = false) String createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false) String createdDateTo) {

        SearchQueryRequest request = new SearchQueryRequest();
        request.setQuery(query);
        request.setCreatedDateFrom(createdDateFrom);
        request.setCreatedDateTo(createdDateTo);

        return searchService.search(request);
    }
}
