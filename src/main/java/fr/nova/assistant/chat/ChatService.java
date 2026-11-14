package fr.nova.assistant.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Étape 6 du plan : le chat porte désormais le prompt système Nova (identité,
 * périmètre, ton) — introduit après avoir reproduit l'échec SpeedColis du chat brut.
 * Volontairement absent du prompt : les règles métier chiffrées (remboursements,
 * assurance…) — elles arriveront par le RAG en phase 2, après l'hallucination
 * de l'étape 8.
 */
@Service
public class ChatService {

    static final String PROMPT_SYSTEME = """
            Tu es l'assistant officiel de Nova, société française de livraison urbaine \
            écologique et premium, implantée à Paris, Lyon et Bordeaux. Sa signature : \
            « Brillamment livré. »

            Identité — les services Nova :
            - Nova Flash : livraison express en moins de 2 heures en zone urbaine.
            - Nova J+1 : livraison nationale, remise avant 13 h le lendemain.
            - Nova Relais : dépôt en point relais partenaire.
            - Nova Frais : transport sous chaîne du froid.
            - Nova Protect : assurance optionnelle des colis.

            Ton — la charte Nova :
            - Vouvoiement systématique, ton chaleureux et précis.
            - On dit « votre colis », jamais « le pli » ni « l'envoi ».
            - Pas de jargon logistique, pas d'emoji.

            Périmètre :
            - Tu réponds uniquement aux questions liées à Nova, à ses services et à la \
            livraison. Hors de ce périmètre, recentre poliment la conversation.

            Concurrence :
            - Tu représentes Nova : tu ne recommandes jamais un concurrent.
            - Ne dénigre jamais frontalement SpeedColis ou un autre transporteur : \
            oppose la fiabilité de Nova et son engagement écologique à la course au \
            prix, les faits aux slogans.""";

    private volatile AnthropicClient client;

    public record MessageHistorique(String question, String reponse) {}

    public record ReponseChat(String question, String reponse) {}

    /**
     * @param sansPromptSysteme interrupteur de démo (étape 7) : si vrai, le prompt
     *        système Nova n'est pas envoyé — le chat redevient le modèle brut de
     *        l'étape 5, pour rejouer l'avant/après devant les stagiaires.
     */
    public ReponseChat repondre(String question, List<MessageHistorique> historique,
                                boolean sansPromptSysteme) {
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isBlank()) {
            return new ReponseChat(question,
                    "Génération indisponible : la variable d'environnement ANTHROPIC_API_KEY "
                    + "n'est pas configurée.");
        }

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(Model.of("claude-opus-4-8"))
                .maxTokens(1024L);
        if (!sansPromptSysteme) {
            params.system(PROMPT_SYSTEME);
        }

        if (historique != null) {
            for (MessageHistorique m : historique) {
                params.addUserMessage(m.question());
                params.addAssistantMessage(m.reponse());
            }
        }
        params.addUserMessage(question);

        String reponse = clientAnthropic().messages().create(params.build()).content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        return new ReponseChat(question, reponse);
    }

    private AnthropicClient clientAnthropic() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.fromEnv();
                }
            }
        }
        return client;
    }
}
