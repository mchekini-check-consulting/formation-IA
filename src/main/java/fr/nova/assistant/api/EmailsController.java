package fr.nova.assistant.api;

import com.anthropic.core.JsonValue;
import fr.nova.assistant.agent.OutilsMcpService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vitrine pédagogique de la démo d'injection (étape 16) : montrer côte à côte
 * la boîte support TELLE QU'EN BASE (ce que l'équipe a reçu) et la sortie de
 * l'outil `lire_emails_support` (ce qui est transmis à l'agent). Identiques à
 * l'étape 16 — l'étape 17 fera diverger la seconde (marqueurs + alertes).
 * Réservé aux managers.
 */
@RestController
@RequestMapping("/api/emails")
public class EmailsController {

    private final JdbcTemplate jdbc;
    private final OutilsMcpService outilsMcp;

    public EmailsController(JdbcTemplate jdbc, OutilsMcpService outilsMcp) {
        this.jdbc = jdbc;
        this.outilsMcp = outilsMcp;
    }

    private static void exigerManager(Authentication auth) {
        boolean manager = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        if (!manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La boîte support est réservée aux managers.");
        }
    }

    /** Ce que l'équipe support a reçu : la table emails, telle qu'en base. */
    @GetMapping("/recus")
    public List<Map<String, Object>> recus(Authentication auth) {
        exigerManager(auth);
        return jdbc.queryForList(
                "SELECT id, expediteur, objet, corps, recu_le, traite "
                + "FROM emails ORDER BY recu_le");
    }

    /** Rejouer la démo : repasse un e-mail à « non traité ». */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/reinitialiser")
    public Map<String, Object> reinitialiser(@org.springframework.web.bind.annotation.PathVariable long id,
                                             Authentication auth) {
        exigerManager(auth);
        jdbc.update("UPDATE emails SET traite = FALSE WHERE id = ?", id);
        return Map.of("id", id, "statut", "NON_TRAITE");
    }

    /**
     * Ce que l'agent reçoit : la sortie BRUTE de l'outil lire_emails_support,
     * appelé avec le jeton de l'utilisateur — exactement ce que voit le modèle.
     */
    @GetMapping(value = "/agent", produces = "application/json")
    public String agent(Authentication auth) {
        exigerManager(auth);
        Jwt jwt = (Jwt) auth.getPrincipal();
        return outilsMcp.executer("lire_emails_support",
                JsonValue.from(Map.of()), jwt.getTokenValue()).resultat();
    }
}
