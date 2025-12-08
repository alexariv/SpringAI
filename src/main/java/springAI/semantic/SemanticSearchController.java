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

    @PostMapping("/semantic")
    public SemanticSearchResponse semanticSearch(@RequestBody SearchQueryRequest request) {
        return searchService.search(request);
    }
}
