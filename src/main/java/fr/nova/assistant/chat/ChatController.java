package fr.nova.assistant.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
            Boolean sansOutils) {}

    @PostMapping
    public ChatService.ReponseChat chat(@Valid @RequestBody RequeteChat requete) {
        return chatService.repondre(requete.question(), requete.historique(),
                Boolean.TRUE.equals(requete.sansPromptSysteme()),
                requete.modele(),
                Boolean.TRUE.equals(requete.sansRag()),
                Boolean.TRUE.equals(requete.sansOutils()));
    }
}
