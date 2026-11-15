package fr.nova.assistant.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    public record RequeteChat(
            @NotBlank(message = "La question est obligatoire") String question,
            List<ChatService.MessageHistorique> historique,
            Boolean sansPromptSysteme,
            String modele,
            Boolean sansRag,
            Boolean sansOutils,
            Boolean sansCache) {}

    @PostMapping
    public ChatService.ReponseChat chat(@Valid @RequestBody RequeteChat requete,
                                        Authentication authentification) {
        // Étape 14 : le jeton de l'appelant est propagé tel quel aux serveurs MCP,
        // et l'identité VÉRIFIÉE (issue du jeton, pas du prompt) est donnée au modèle
        Jwt jwt = (Jwt) authentification.getPrincipal();
        String utilisateur = jwt.getClaimAsString("preferred_username");
        List<String> roles = authentification.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        return chatService.repondre(requete.question(), requete.historique(),
                Boolean.TRUE.equals(requete.sansPromptSysteme()),
                requete.modele(),
                Boolean.TRUE.equals(requete.sansRag()),
                Boolean.TRUE.equals(requete.sansOutils()),
                Boolean.TRUE.equals(requete.sansCache()),
                jwt.getTokenValue(), utilisateur, roles);
    }
}
