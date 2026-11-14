package fr.nova.assistant.agent;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client des serveurs MCP factices de Nova (étape 11) : découverte des outils
 * exposés par chaque serveur (GET /outils) et exécution (POST /outils/{nom}).
 * C'est NOTRE code qui exécute les outils — le modèle ne fait que les demander.
 */
@Service
public class OutilsMcpService {

    private static final Logger log = LoggerFactory.getLogger(OutilsMcpService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Définition d'un outil telle que publiée par son serveur MCP. */
    public record DefinitionOutil(String serveur, String urlBase, String nom,
                                  String description, Map<String, Object> parametres) {}

    /** Trace d'un appel d'outil, renvoyée à l'interface (étape 12). */
    public record ActionExecutee(String serveur, String outil, String arguments, String resultat) {}

    private final Map<String, String> serveurs;
    private final RestClient http = RestClient.create();
    private volatile Map<String, DefinitionOutil> registre = Map.of();

    public OutilsMcpService(
            @Value("${nova.mcp.tickets:http://localhost:7001}") String urlTickets,
            @Value("${nova.mcp.remboursement:http://localhost:7002}") String urlRemboursement,
            @Value("${nova.mcp.notification:http://localhost:7003}") String urlNotification) {
        this.serveurs = Map.of(
                "tickets", urlTickets,
                "remboursement", urlRemboursement,
                "notification", urlNotification);
    }

    /** Interroge chaque serveur MCP et reconstruit le registre des outils. */
    @SuppressWarnings("unchecked")
    public synchronized void decouvrirOutils() {
        Map<String, DefinitionOutil> nouveau = new LinkedHashMap<>();
        serveurs.forEach((nomServeur, urlBase) -> {
            try {
                List<Map<String, Object>> definitions =
                        http.get().uri(urlBase + "/outils").retrieve().body(List.class);
                for (Map<String, Object> d : definitions) {
                    nouveau.put(String.valueOf(d.get("nom")), new DefinitionOutil(
                            nomServeur, urlBase,
                            String.valueOf(d.get("nom")),
                            String.valueOf(d.get("description")),
                            (Map<String, Object>) d.get("parametres")));
                }
            } catch (Exception e) {
                log.warn("Serveur MCP « {} » injoignable ({}) : {}", nomServeur, urlBase, e.getMessage());
            }
        });
        registre = nouveau;
        log.info("Outils MCP découverts : {}", registre.keySet());
    }

    private Map<String, DefinitionOutil> registre() {
        if (registre.isEmpty()) {
            decouvrirOutils();
        }
        return registre;
    }

    /** Les définitions d'outils au format attendu par l'API Claude. */
    @SuppressWarnings("unchecked")
    public List<Tool> outilsPourClaude() {
        List<Tool> outils = new ArrayList<>();
        for (DefinitionOutil def : registre().values()) {
            Tool.InputSchema.Properties.Builder proprietes = Tool.InputSchema.Properties.builder();
            Map<String, Object> props = (Map<String, Object>) def.parametres().getOrDefault("properties", Map.of());
            props.forEach((nom, schema) -> proprietes.putAdditionalProperty(nom, JsonValue.from(schema)));

            Tool.InputSchema.Builder schema = Tool.InputSchema.builder().properties(proprietes.build());
            Object requis = def.parametres().get("required");
            if (requis instanceof List<?> liste) {
                schema.required(liste.stream().map(String::valueOf).toList());
            }

            outils.add(Tool.builder()
                    .name(def.nom())
                    .description("[Serveur MCP " + def.serveur() + "] " + def.description())
                    .inputSchema(schema.build())
                    .build());
        }
        return outils;
    }

    /** Exécute un outil sur son serveur MCP et renvoie la trace complète. */
    public ActionExecutee executer(String nomOutil, JsonValue arguments) {
        DefinitionOutil def = registre().get(nomOutil);
        String args;
        try {
            args = MAPPER.writeValueAsString(arguments);
        } catch (Exception e) {
            args = String.valueOf(arguments);
        }
        if (def == null) {
            return new ActionExecutee("?", nomOutil, args,
                    "{\"erreur\": \"Outil inconnu des serveurs MCP : " + nomOutil + "\"}");
        }
        try {
            String resultat = http.post()
                    .uri(def.urlBase() + "/outils/" + nomOutil)
                    .header("Content-Type", "application/json")
                    .body(args)
                    .retrieve()
                    .body(String.class);
            return new ActionExecutee(def.serveur(), nomOutil, args, resultat);
        } catch (Exception e) {
            return new ActionExecutee(def.serveur(), nomOutil, args,
                    "{\"erreur\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
