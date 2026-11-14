package fr.nova.assistant.rag;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final IngestionService ingestionService;

    public RagController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** État de l'index vectoriel. */
    @GetMapping("/statut")
    public Map<String, Object> statut() {
        return Map.of("chunksIndexes", ingestionService.nombreDeChunks());
    }

    /** Vide puis ré-indexe tout le corpus (sera protégé par rôle en phase 4). */
    @PostMapping("/ingestion")
    public Map<String, Object> reindexer() {
        return Map.of("chunksIndexes", ingestionService.reindexer());
    }
}
