package springAI.semantic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IngestService {

    private final ElasticsearchClient esClient;
    private final ElasticsearchVectorStore vectorStore;

    public IngestService(ElasticsearchClient esClient,
                         ElasticsearchVectorStore vectorStore) {
        this.esClient = esClient;
        this.vectorStore = vectorStore;
    }

    public void ingestAll() throws IOException {

        final int PAGE_SIZE = 1000;

        // Use raw Map to avoid generics conflict from Map.class
        SearchResponse<Map> response = esClient.search(
                s -> s.index("operation_logs").size(PAGE_SIZE),
                Map.class
        );

        List<Hit<Map>> hits = response.hits().hits();

        processHits(hits);
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

        if (flat.getDescription() == null || flat.getDescription().isBlank()) {
            continue;  // nothing to embed
        }

        Map<String, Object> metadata = new HashMap<>();

        // Only put non-null values
        if (flat.getId() != null) {
            metadata.put("id", flat.getId());
        }
        if (flat.getOwner() != null) {
            metadata.put("owner", flat.getOwner());
        }
        if (flat.getCreatedDate() != null) {
            metadata.put("createdDate", flat.getCreatedDate());
        }
        if (flat.getEventStart() != null) {
            metadata.put("eventStart", flat.getEventStart());
        }
        if (flat.getLevel() != null) {
            metadata.put("level", flat.getLevel());
        }
        if (flat.getState() != null) {
            metadata.put("state", flat.getState());
        }
        if (flat.getLogbooksName() != null) {
            metadata.put("logbooks_name", flat.getLogbooksName());
        }

        // tags_name- non-null list
        List<String> tags = flat.getTagsName();
        if (tags == null) {
            tags = Collections.emptyList();
        }
        metadata.put("tags_name", tags);

        // description (content) embedded text
        Document doc = new Document(flat.getDescription(), metadata);
        docsToInsert.add(doc);
    }

    if (!docsToInsert.isEmpty()) {
        vectorStore.add(docsToInsert);
    }
    }


    private OperationLogDocument flatten(Map<String, Object> src) {

        OperationLogDocument doc = new OperationLogDocument();

        doc.setId((String) src.get("@id"));
        doc.setDescription((String) src.get("description"));
        doc.setOwner((String) src.get("@owner"));
        doc.setCreatedDate((String) src.get("@createdDate"));
        doc.setEventStart((String) src.get("@eventStart"));
        doc.setLevel((String) src.get("@level"));
        doc.setState((String) src.get("@state"));

        // flatten logbooks to logbooks_name
        try {
            Map<String, Object> logbooks = (Map<String, Object>) src.get("logbooks");
            if (logbooks != null) {
                Map<String, Object> logbookObj =
                        (Map<String, Object>) logbooks.get("logbook");
                if (logbookObj != null) {
                    doc.setLogbooksName((String) logbookObj.get("@name"));
                }
            }
        } catch (Exception e) {
            doc.setLogbooksName(null);
        }

        // flatten tags to tags_name
        try {
            Map<String, Object> tags = (Map<String, Object>) src.get("tags");
            if (tags != null) {
                Object tagObj = tags.get("tag");

                List<String> names = new ArrayList<>();

                if (tagObj instanceof List) {
                    List<Map<String, Object>> tagList = (List<Map<String, Object>>) tagObj;
                    names = tagList.stream()
                            .map(t -> (String) t.get("@name"))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                } else if (tagObj instanceof Map) {
                    Map<String, Object> singleTag = (Map<String, Object>) tagObj;
                    String name = (String) singleTag.get("@name");
                    if (name != null) {
                        names.add(name);
                    }
                }

                doc.setTagsName(names);
            }
        } catch (Exception e) {
            doc.setTagsName(Collections.emptyList());
        }

        return doc;
    }
}

