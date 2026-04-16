package springAI.semantic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

@Service
public class IngestService {
    //azure only allowed up to 96 per bach
    private static final int PAGE_SIZE = 50;
    private static final int BATCH_SIZE = 50;

    private final ElasticsearchClient esClient;
    private final ElasticsearchVectorStore vectorStore;

    public IngestService(ElasticsearchClient esClient,
                         ElasticsearchVectorStore vectorStore) {
        this.esClient = esClient;
        this.vectorStore = vectorStore;
    }

    public void ingestAll() throws IOException {
    int totalIngested = 0;
    String lastId = null;

    while (true) {
        final String currentLastId = lastId;

        SearchResponse<Map> response = esClient.search(
            s -> {
                s.index("operation_logs") //look at this I think it needs to be changed 
                 .size(PAGE_SIZE)
                 .sort(sort -> sort.field(f -> f.field("id")
                     .order(SortOrder.Asc)));
                if (currentLastId != null) {
                    s.searchAfter(FieldValue.of(currentLastId));
                }
                return s;
            },
            Map.class
        );

        List<Hit<Map>> hits = response.hits().hits();
        if (hits.isEmpty()) break;

        processHits(hits);
        totalIngested += hits.size();
        System.out.println("Current ingest total: " + totalIngested);

        lastId = (String) hits.get(hits.size() - 1).source().get("id");
        if (hits.size() < PAGE_SIZE) break;
    }

    System.out.println("Total ingested: " + totalIngested);
}
    private void processHits(List<Hit<Map>> hits) {

    List<Document> docsToInsert = new ArrayList<>();

    for (Hit<Map> hit : hits) {

        Map sourceRaw = hit.source();
        if (sourceRaw == null) {
            continue;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> src = (Map<String, Object>) sourceRaw;

        OperationLogDocument flat = flatten(src);

        String embeddings = buildEmbeddings(flat.getTitle(), flat.getDescription());
        if (embeddings == null || embeddings.isBlank()) {
            continue;  // nothing to embed but there should always be something to embed 
        }

        Map<String, Object> metadata = new HashMap<>();

        // Only put non-null values
        if (flat.getId() != null) {
            metadata.put("id", flat.getId());
        }
        if (flat.getOwner() != null) {
            metadata.put("owner", flat.getOwner());
        }
        if (flat.getTitle() != null) {
            metadata.put("title", flat.getTitle());
        }
        if (flat.getSource() != null) {
            metadata.put("source", flat.getSource());
        }
         if (flat.getLevel() != null) {
            metadata.put("level", flat.getLevel());
        }
        if (flat.getState() != null) {
            metadata.put("state", flat.getState());
        }
        if (flat.getCreatedDate() != null) {
            metadata.put("createdDate", flat.getCreatedDate());
        }
        if (flat.getModifyDate() != null) {
            metadata.put("modifyDate", flat.getModifyDate());
        }
    
       // Flattened nested fields — required for Spring AI Filter.Expression compatibility
       metadata.put("logbooks_name", flat.getLogbooksName() != null
            ? flat.getLogbooksName() : Collections.emptyList());
        metadata.put("tags_name", flat.getTagsName() != null
            ? flat.getTagsName() : Collections.emptyList());
        metadata.put("events_name", flat.getEventsName() != null
             ? flat.getEventsName() : Collections.emptyList());

        docsToInsert.add(new Document(embeddings, metadata));
    }
    System.out.println(">>> Docs to embed (title & description): " + docsToInsert.size());

        for (int i = 0; i < docsToInsert.size(); i += BATCH_SIZE) {
            List<Document> batch = docsToInsert.subList(i, 
                Math.min(i + BATCH_SIZE, docsToInsert.size()));
            vectorStore.add(batch);
        }
    }

private String buildEmbeddings(String title, String description) {
        boolean hasTitle       = title != null && !title.isBlank();
        boolean hasDescription = description != null && !description.isBlank();

        if (hasTitle && hasDescription) return title.strip() + "\n" + description.strip();
        if (hasTitle)                   return title.strip();
        if (hasDescription)             return description.strip();
        return null;
    }

    @SuppressWarnings("unchecked")
    private OperationLogDocument flatten(Map<String, Object> src) {
        OperationLogDocument doc = new OperationLogDocument();

        doc.setId(toString(src.get("id")));
        doc.setOwner((String) src.get("owner"));
        doc.setTitle((String) src.get("title"));
        doc.setDescription((String) src.get("description"));
        doc.setSource((String) src.get("source"));
        doc.setLevel((String) src.get("level"));
        doc.setState((String) src.get("state"));
        doc.setCreatedDate(toString(src.get("createdDate")));
        doc.setModifyDate(toString(src.get("modifyDate")));

        doc.setLogbooksName(extractNestedNames(src, "logbooks", "name"));
        doc.setTagsName(extractNestedNames(src, "tags", "name"));
        doc.setEventsName(extractNestedNames(src, "events", "name"));

        return doc;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractNestedNames(Map<String, Object> src,
                                            String nestedField,
                                            String nameKey) {
        try {
            Object raw = src.get(nestedField);
            if (raw == null) return Collections.emptyList();

            List<String> names = new ArrayList<>();

            if (raw instanceof List) {
                for (Object item : (List<?>) raw) {
                    if (item instanceof Map) {
                        String name = (String) ((Map<String, Object>) item).get(nameKey);
                        if (name != null) names.add(name);
                    }
                }
            } else if (raw instanceof Map) {
                String name = (String) ((Map<String, Object>) raw).get(nameKey);
                if (name != null) names.add(name);
            }

            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}

