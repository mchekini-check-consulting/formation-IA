package fr.nova.assistant.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Étape 18 : façade des validations humaines. L'interface web appelle ces
 * endpoints (boutons Valider/Rejeter) — le MODÈLE n'y a pas accès : ils ne font
 * pas partie des outils MCP découverts. Le jeton de l'utilisateur est relayé au
 * serveur remboursement qui exige le rôle MANAGER.
 */
@RestController
@RequestMapping("/api/validations")
public class ValidationsController {

    private final RestClient http = RestClient.create();
    private final String urlRemboursement;

    public ValidationsController(
            @Value("${nova.mcp.remboursement:http://localhost:7002}") String urlRemboursement) {
        this.urlRemboursement = urlRemboursement;
    }

    private String jeton(Authentication auth) {
        return ((Jwt) auth.getPrincipal()).getTokenValue();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String demandesEnAttente(Authentication auth) {
        return http.get().uri(urlRemboursement + "/demandes")
                .header("Authorization", "Bearer " + jeton(auth))
                .retrieve().body(String.class);
    }

    @PostMapping(value = "/{reference}/valider", produces = MediaType.APPLICATION_JSON_VALUE)
    public String valider(@PathVariable String reference, Authentication auth) {
        return http.post().uri(urlRemboursement + "/demandes/" + reference + "/valider")
                .header("Authorization", "Bearer " + jeton(auth))
                .retrieve().body(String.class);
    }

    @PostMapping(value = "/{reference}/rejeter", produces = MediaType.APPLICATION_JSON_VALUE)
    public String rejeter(@PathVariable String reference, Authentication auth) {
        return http.post().uri(urlRemboursement + "/demandes/" + reference + "/rejeter")
                .header("Authorization", "Bearer " + jeton(auth))
                .retrieve().body(String.class);
    }
}
