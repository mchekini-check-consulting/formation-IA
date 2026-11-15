package fr.nova.assistant.observabilite;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.nova.assistant.chat.ChatService.ReponseChat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Étape 22 : observabilité — chaque échange est tracé dans Langfuse via son API
 * d'ingestion : la trace (question → réponse, utilisateur, métadonnées), un span
 * pour la recherche RAG (documents retrouvés et scores), un span par appel
 * d'outil MCP (arguments → résultat) et la génération (modèle, tokens, coût).
 * Envoi ASYNCHRONE et résilient : si Langfuse est indisponible, l'assistant
 * continue — l'observabilité ne doit jamais casser le service.
 */
@Service
public class LangfuseService {

    private static final Logger log = LoggerFactory.getLogger(LangfuseService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient http = RestClient.create();
    private final String url;
    private final String autorisation;
    private final boolean actif;

    public LangfuseService(
            @Value("${nova.langfuse.url:http://localhost:3002}") String url,
            @Value("${nova.langfuse.cle-publique:pk-lf-nova}") String clePublique,
            @Value("${nova.langfuse.cle-secrete:sk-lf-nova-secret}") String cleSecrete,
            @Value("${nova.langfuse.actif:true}") boolean actif) {
        this.url = url;
        this.autorisation = "Basic " + Base64.getEncoder()
                .encodeToString((clePublique + ":" + cleSecrete).getBytes());
        this.actif = actif;
    }

    /** Trace un échange complet. Ne bloque jamais la réponse à l'utilisateur. */
    public void tracer(String utilisateur, ReponseChat reponse, long dureeMs,
                       boolean sansPromptSysteme, boolean sansRag, boolean sansOutils) {
        if (!actif) return;
        CompletableFuture.runAsync(() -> {
            try {
                envoyer(utilisateur, reponse, dureeMs, sansPromptSysteme, sansRag, sansOutils);
            } catch (Exception e) {
                log.warn("Langfuse indisponible (trace perdue) : {}", e.getMessage());
            }
        });
    }

    private void envoyer(String utilisateur, ReponseChat r, long dureeMs,
                         boolean sansPromptSysteme, boolean sansRag, boolean sansOutils)
            throws Exception {
        String traceId = UUID.randomUUID().toString();
        Instant fin = Instant.now();
        Instant debut = fin.minusMillis(dureeMs);
        List<Map<String, Object>> lot = new ArrayList<>();

        // La trace : l'échange complet
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("modele", r.utilisation() == null ? null : r.utilisation().modele());
        meta.put("routage", r.routage());
        meta.put("depuisCache", r.depuisCache());
        meta.put("sansPromptSysteme", sansPromptSysteme);
        meta.put("sansRag", sansRag);
        meta.put("sansOutils", sansOutils);
        meta.put("nbPassagesRag", r.passages() == null ? 0 : r.passages().size());
        meta.put("nbActionsOutils", r.actions() == null ? 0 : r.actions().size());
        lot.add(evenement("trace-create", Map.of(
                "id", traceId,
                "timestamp", debut.toString(),
                "name", "chat-nova",
                "userId", utilisateur,
                "input", Map.of("question", r.question()),
                "output", Map.of("reponse", r.reponse()),
                "metadata", meta,
                "tags", List.of(r.depuisCache() ? "cache"
                        : (r.utilisation() == null ? "inconnu" : r.utilisation().modele())))));

        // Span RAG : les documents retrouvés et leurs scores
        if (r.passages() != null && !r.passages().isEmpty()) {
            lot.add(evenement("span-create", Map.of(
                    "id", UUID.randomUUID().toString(),
                    "traceId", traceId,
                    "name", "rag-recherche-pgvector",
                    "startTime", debut.toString(),
                    "endTime", debut.plusMillis(200).toString(),
                    "input", Map.of("question", r.question()),
                    "output", Map.of("documents", r.passages().stream()
                            .map(p -> Map.of("document", p.document(), "score", p.score()))
                            .toList()))));
        }

        // Un span par appel d'outil MCP
        if (r.actions() != null) {
            for (var action : r.actions()) {
                lot.add(evenement("span-create", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "traceId", traceId,
                        "name", "outil-mcp:" + action.outil(),
                        "startTime", debut.toString(),
                        "endTime", fin.toString(),
                        "input", tronquer(action.arguments()),
                        "output", tronquer(action.resultat()),
                        "metadata", Map.of("serveur", action.serveur()))));
            }
        }

        // La génération : modèle, tokens, coût (sauf réponse servie par le cache).
        // Son input est le prompt RÉELLEMENT envoyé au modèle : prompt système
        // effectif + message utilisateur augmenté des extraits RAG — pas la
        // question brute (elle, est sur la trace).
        if (!r.depuisCache() && r.utilisation() != null && r.utilisation().appelsModele() > 0) {
            Map<String, Object> entree = new LinkedHashMap<>();
            if (r.promptFinal() != null) {
                entree.put("promptSysteme", r.promptFinal().promptSysteme() == null
                        ? "(aucun — interrupteur « prompt système » sur off)"
                        : r.promptFinal().promptSysteme());
                entree.put("messageUtilisateurAugmente", r.promptFinal().messageUtilisateur());
            } else {
                entree.put("question", r.question());
            }
            lot.add(evenement("generation-create", Map.of(
                    "id", UUID.randomUUID().toString(),
                    "traceId", traceId,
                    "name", "generation-claude",
                    "model", r.utilisation().modele(),
                    "startTime", debut.toString(),
                    "endTime", fin.toString(),
                    "input", entree,
                    "output", Map.of("reponse", r.reponse()),
                    "usage", Map.of(
                            "input", r.utilisation().tokensEntree(),
                            "output", r.utilisation().tokensSortie(),
                            "unit", "TOKENS"),
                    "metadata", Map.of(
                            "appelsModele", r.utilisation().appelsModele(),
                            "coutDollars", r.utilisation().coutDollars(),
                            "tokensCacheEcrits", r.utilisation().tokensCacheEcrits(),
                            "tokensCacheLus", r.utilisation().tokensCacheLus()))));
        }

        http.post().uri(url + "/api/public/ingestion")
                .header("Authorization", autorisation)
                .header("Content-Type", "application/json")
                .body(JSON.writeValueAsString(Map.of("batch", lot)))
                .retrieve()
                .toBodilessEntity();
    }

    private static Map<String, Object> evenement(String type, Map<String, Object> corps) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "timestamp", Instant.now().toString(),
                "body", corps);
    }

    private static String tronquer(String texte) {
        if (texte == null) return "";
        return texte.length() <= 3000 ? texte : texte.substring(0, 3000) + "…";
    }
}
