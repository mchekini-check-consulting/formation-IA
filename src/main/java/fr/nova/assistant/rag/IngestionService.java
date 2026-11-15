package fr.nova.assistant.rag;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Étape 9 du plan : découper les 7 documents du corpus Nova (PDF embarqués dans
 * le jar), calculer les embeddings (modèle multilingue local, ONNX) et les
 * indexer dans pgvector (table vector_store).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver;
    private final JdbcTemplate jdbcTemplate;

    public IngestionService(VectorStore vectorStore, ResourcePatternResolver resolver,
                            JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.resolver = resolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nombreDeChunks() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
        return count == null ? 0 : count;
    }

    /** Vide l'index puis ré-indexe tout le corpus. Renvoie le nombre de chunks créés. */
    public long reindexer() {
        jdbcTemplate.update("DELETE FROM vector_store");
        return indexerCorpus();
    }

    private long indexerCorpus() {
        try {
            Resource[] pdfs = resolver.getResources("classpath:corpus/*.pdf");
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(400)
                    .withMinChunkSizeChars(200)
                    .build();

            long total = 0;
            for (Resource pdf : pdfs) {
                List<Document> pages = new PagePdfDocumentReader(pdf).get();
                List<Document> chunks = splitter.apply(pages);
                chunks.forEach(c -> c.getMetadata().put("document", pdf.getFilename()));
                vectorStore.add(chunks);
                total += chunks.size();
                log.info("Corpus Nova : {} indexé ({} chunks)", pdf.getFilename(), chunks.size());
            }
            return total;
        } catch (Exception e) {
            throw new IllegalStateException("Échec de l'indexation du corpus : " + e.getMessage(), e);
        }
    }

    /** Au démarrage : indexe le corpus si la base vectorielle est vide. */
    @Bean
    ApplicationRunner indexationInitiale() {
        return args -> {
            if (nombreDeChunks() > 0) {
                log.info("Corpus Nova déjà indexé ({} chunks).", nombreDeChunks());
                return;
            }
            log.info("Corpus Nova indexé au démarrage : {} chunks.", indexerCorpus());
        };
    }
}
