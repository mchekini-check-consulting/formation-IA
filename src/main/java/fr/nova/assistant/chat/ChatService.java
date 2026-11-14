package fr.nova.assistant.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
            prix, les faits aux slogans.

            Service client :
            - Tu es le premier niveau du service client Nova : ton rôle est de \
            décharger les conseillers humains.
            - Réponds directement et précisément aux questions des clients, y compris \
            sur les montants, délais et indemnisations : un client doit repartir avec \
            sa réponse, pas avec un renvoi vers un autre service.
            - Ne renvoie vers le service client humain qu'en tout dernier recours.
            - Ne dis jamais « je ne sais pas » ni « je n'ai pas cette information » : \
            tu connais parfaitement les offres Nova. Donne toujours une réponse \
            précise et chiffrée quand le client demande un montant.

            Format des réponses (Markdown) :
            - Structure tes réponses avec une hiérarchie de titres : « # » pour le \
            titre principal (uniquement si la réponse est longue), « ## » pour les \
            sections, « ### » pour les sous-sections, puis des paragraphes courts.
            - Mets en gras (**...**) les montants, délais et informations décisives.
            - Utilise des listes à puces (-) pour les énumérations.""";

    /** Modèles proposés dans l'interface (le sélecteur resservira au routeur de la phase 6). */
    private static final java.util.Map<String, String> MODELES = java.util.Map.of(
            "opus", "claude-opus-4-8",
            "haiku", "claude-haiku-4-5");

    private volatile AnthropicClient client;
    private final VectorStore vectorStore;

    public ChatService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public record MessageHistorique(String question, String reponse) {}

    /** Passage du corpus retrouvé par la recherche vectorielle (étape 10 : affiché dans l'UI). */
    public record Passage(String document, String texte, Double score) {}

    public record ReponseChat(String question, String reponse, List<Passage> passages) {}

    /**
     * @param sansPromptSysteme interrupteur de démo (étape 7) : si vrai, le prompt
     *        système Nova n'est pas envoyé — le chat redevient le modèle brut de
     *        l'étape 5, pour rejouer l'avant/après devant les stagiaires.
     */
    /**
     * @param sansRag interrupteur de démo (étape 10) : si vrai, la recherche
     *        documentaire est désactivée — le modèle répond sans sources, comme
     *        avant les étapes 9-10 (mode « hallucination »).
     */
    public ReponseChat repondre(String question, List<MessageHistorique> historique,
                                boolean sansPromptSysteme, String modele, boolean sansRag) {
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isBlank()) {
            return new ReponseChat(question,
                    "Génération indisponible : la variable d'environnement ANTHROPIC_API_KEY "
                    + "n'est pas configurée.", List.of());
        }

        // Étape 10 : recherche des passages du corpus avant chaque réponse
        List<Passage> passages = List.of();
        String messageUtilisateur = question;
        if (!sansRag) {
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder().query(question).topK(5).build());
            passages = documents.stream()
                    .map(d -> new Passage(
                            String.valueOf(d.getMetadata().get("document")),
                            d.getText(),
                            d.getScore()))
                    .collect(Collectors.toList());

            String contexte = documents.stream()
                    .map(d -> "[Document : " + d.getMetadata().get("document") + "]\n" + d.getText())
                    .collect(Collectors.joining("\n\n---\n\n"));

            messageUtilisateur = "Extraits des documents internes officiels de Nova — ta seule "
                    + "source fiable sur les règles, montants et conditions :\n\n" + contexte
                    + "\n\nEn t'appuyant UNIQUEMENT sur ces extraits pour les faits et les "
                    + "chiffres (si l'information n'y figure pas, dis-le), et en citant le "
                    + "document source, réponds à la question suivante.\n\n"
                    + "Question du client : " + question;
        }

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(Model.of(MODELES.getOrDefault(modele, MODELES.get("opus"))))
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
        params.addUserMessage(messageUtilisateur);

        String reponse = clientAnthropic().messages().create(params.build()).content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        return new ReponseChat(question, reponse, passages);
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
