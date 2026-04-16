package springAI.semantic;

import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class AzureTestController {

    private final EmbeddingModel embeddingModel;

    public AzureTestController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/embed")
    public ResponseEntity<?> testEmbed() {
        try {
            String text = "test embedding";
            var response = embeddingModel.embed(text);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "dimensionality", response.length,
                "firstValue", response[0]
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
} 