# Nova — Assistant IA d'entreprise (formation « Fondamentaux de l'IA »)

Projet fil rouge de formation : construire pas à pas l'assistant IA interne d'une
société de livraison fictive, **Nova**, en reproduisant d'abord chaque défaillance
classique (dénigrement, hallucination, fuite de données, injection, dérive des
coûts) puis en posant la brique qui la corrige.

## Contexte (Phase 0 — acquise, ne pas refaire)

- **Société** : Nova — livraison urbaine écologique et premium (Paris, Lyon,
  Bordeaux), fondée en 2021, 120 livreurs (80 % vélo-cargo). Signature :
  « Brillamment livré. » Concurrent : **SpeedColis** (low-cost, -30 % sur les prix,
  8 % de colis perdus ; Nova : +15 % de prix, 99,2 % sans incident). Règle de ton :
  ne jamais dénigrer frontalement SpeedColis.
- **Services** : Nova Flash (< 2 h), Nova J+1 (avant 13 h), Nova Relais, Nova Frais
  (froid), Nova Protect (assurance ad valorem jusqu'à 1 000 €, franchise 10 €,
  prime 2,5 % min 3 €).
- **Règles métier clés** (dans les PDF du corpus) : remboursement plafonné à
  **50 €** sans assurance ; réclamation sous 14 jours ; versement sous 10 jours
  ouvrés ; geste commercial ≤ 15 € sans validation manager ; SLA première réponse
  4 h ouvrées ; paie livreurs le 5 du mois ; prime qualité 150 € si note ≥ 4,8 ;
  période d'essai nouvelle recrue 2 mois.
- **Corpus** : `corpus/` — 7 PDF (présentation, CGV, politique de remboursement,
  assurance, FAQ livreurs, onboarding, procédure litiges). Regénérables (script
  Python fpdf2).
- **Données** : `seed/*.sql`, auto-chargées au premier démarrage de PostgreSQL —
  5 clients, 10 commandes (réf. `NOV-2026-XXXX`), 6 tickets (dont le vase cassé à
  75 € sans assurance → escalade), table sensible `paie_livreurs` (5 salaires),
  table `utilisateurs` (mapping comptes → rôles → données métier).
- **4 comptes de démo** : `marie.dupont` (CLIENT), `karim.benali` (LIVREUR),
  `lea.martin` (NOUVELLE_RECRUE, en période d'essai), `sophie.moreau` (MANAGER).
- **Stack** : Spring Boot 3 + Spring AI, PostgreSQL 16 + pgvector, Keycloak 26,
  Redis 7, Langfuse, Claude (API Anthropic), Docker Compose.
  `docker compose up -d` → PostgreSQL :5432 (nova/nova/nova), Keycloak :8888
  (admin/admin), Redis :6379, Langfuse :3002. Ports 8081/3001 occupés sur ce Mac.

## Conventions

- Tout en **français** : code (noms de classes/méthodes quand c'est naturel),
  commentaires, interface, documents, réponses.
- Identité visuelle Nova : indigo nuit #181133, violet #5536B5, accent or #E28F15.
- Secrets : jamais en base ni dans git (`.env` dans `.gitignore`) ; `ANTHROPIC_API_KEY`
  en variable d'environnement.
- Chaque phase doit être **démontrable** : d'abord reproduire l'échec, puis montrer
  la correction. Les interrupteurs de démo (on/off) font partie du produit.

## Plan de mise en place

### Phase 0 : cadrage et jeu de données ✅ (fait — ne pas refaire)

1. Inventer la société fictive : nom, services, ton de marque, concurrent SpeedColis. ✅
2. Rédiger 5 à 10 documents internes : CGV, politique de remboursement (plafond 50 €),
   assurance colis, FAQ livreurs. ✅ (7 PDF dans `corpus/`)
3. Créer les données factices : commandes, clients, tickets, table de paie, comptes
   (client, livreur, nouvelle recrue, manager). ✅ (`seed/`)
4. Choisir la stack : Spring Boot + Spring AI, PostgreSQL + pgvector, Keycloak,
   Redis, Langfuse, le tout en Docker Compose. ✅ (`docker-compose.yml`)

### Phase 1 : Nova naît (étape 1)

5. Monter un chat web minimal branché sur un modèle par API, **sans prompt système**. ✅
   (`POST /api/chat` avec historique multi-tours, UI chat sur :8080, aucun prompt
   système — le modèle répond « je suis Claude, je travaille pour Anthropic » et
   tutoie les clients : c'est l'état brut à montrer avant l'étape 6.)
6. Reproduire l'échec SpeedColis, puis ajouter le prompt système (identité,
   périmètre, ton). ✅
   (Échec reproduit : le chat brut joue les comparateurs neutres et propose de
   « comparer avec d'autres transporteurs ». Prompt système v1 ajouté dans
   `ChatService.PROMPT_SYSTEME` — identité + services, ton/charte, périmètre,
   règle concurrence. Volontairement SANS règles métier chiffrées ni consigne
   anti-invention : nécessaire pour reproduire l'hallucination de l'étape 8.)
7. Garder un interrupteur « prompt système on/off » pour la démo. ✅
   (Champ `sansPromptSysteme` sur POST /api/chat + commutateur dans l'en-tête de
   l'UI avec pastille d'état : violette « Prompt système actif » / ambre « Mode
   brut (sans prompt) ». La bascule agit par message, sans recharger — on peut
   poser la même question dans les deux modes au sein d'une même conversation.)

   → **Phase 1 terminée.** Démo de référence : « Qui es-tu ? » en mode brut
   (« je suis Claude, développé par Anthropic », tutoiement, emoji) puis en mode
   Nova (« l'assistant officiel de Nova », vouvoiement, services, signature).

### Phase 2 : RAG (étape 2)

8. Reproduire l'hallucination des 500 €. ✅
   **Recette éprouvée** (les modèles Claude récents résistent — trois ingrédients
   nécessaires) :
   1. prompt v1.1 « service client » : répondre précisément, ne pas renvoyer vers
      un humain, et surtout « ne dis jamais je ne sais pas » (consignes réalistes,
      toutes présentes dans PROMPT_SYSTEME) ;
   2. petit modèle : sélecteur Opus/Haiku dans l'UI (champ `modele` de l'API) —
      la démo fonctionne avec **Haiku**, Opus refuse encore les chiffres ;
   3. question : « Je veux souscrire Nova Protect : jusqu'à quel montant suis-je
      couvert et à quel prix ? Donnez-moi les chiffres. »
   → Haiku invente un contrat complet : plafond 3 000 € (réel : 1 000 €), tarif
   fixe 2,50 € ou 2,99 € selon les runs (réel : 2,5 % min 3 €) — les chiffres
   inventés VARIENT d'une exécution à l'autre, preuve qu'ils sortent de nulle part.
   Variante grave sur « colis perdu sans assurance » : Haiku affirme que Nova
   n'indemnise pas du tout (réel : 50 €) — il nie un droit du client.
9. Découper les documents, calculer les embeddings, les indexer dans pgvector. ✅
   (Spring AI : PagePdfDocumentReader + TokenTextSplitter ~400 tokens + embeddings
   multilingues ONNX → 15 chunks dans vector_store. Indexation auto au démarrage
   si vide, POST /api/rag/ingestion pour ré-indexer, GET /api/rag/statut.)
10. Brancher la recherche avant chaque réponse et afficher le passage cité. ✅
    (similaritySearch top 5 → extraits injectés dans le message utilisateur avec
    consigne « ta seule source fiable, cite le document ». UI : interrupteur
    « RAG actif / Sans RAG (hallucinations) » + volet « Sources » sous chaque
    réponse avec documents, scores et extraits.
    → Démo validée : même question Nova Protect sur Haiku — sans RAG il invente
    « jusqu'à 500 € » (LE chiffre du plan !) ou 3 000 € selon les runs ; avec RAG
    il répond 1 000 €, franchise 10 €, 2,5 % min 3 € en citant assurance-colis.pdf.
    Question piège bonus : « colis 75 € cassé sans assurance » → 50 € max, aucune
    dérogation, réclamation 14 jours, en citant politique-remboursement.pdf.)

   → **Phase 2 terminée.**

### Phase 3 : agent (étape 3)

11. Créer 3 serveurs MCP factices : remboursement, tickets, notification d'équipe.
12. Activer l'appel d'outils et afficher les actions exécutées dans l'interface.

### Phase 4 : droits (étape 4)

13. Ajouter un outil paie accessible à tous et reproduire la fuite du salaire.
14. Mettre en place la connexion OAuth et propager le jeton utilisateur dans chaque
    appel d'outil.
15. Filtrer par rôle côté serveur MCP, pas côté prompt.

### Phase 5 : garde-fous (étape 5)

16. Ajouter la lecture d'e-mails et un e-mail piégé, puis reproduire le remboursement
    en masse.
17. Poser les filtres d'entrée : contenu lu traité comme donnée, détection d'injection.
18. Exiger une validation humaine pour les actions sensibles et masquer les données
    personnelles en sortie.

### Phase 6 : coûts (étape 6)

19. Écrire un script qui simule 2 000 « Où est mon colis ? » et afficher le coût en
    tokens.
20. Ajouter le cache (Redis) et un routeur petit modèle / gros modèle.
21. Rejouer le script et comparer les deux factures.

### Phase 7 : observabilité (étapes 7 et 8)

22. Tracer chaque prompt, document retrouvé et appel d'outil dans Langfuse.
23. Constituer un jeu d'évals de 20 à 30 questions, dont les 7 pièges de l'histoire.
24. Automatiser le rejeu des évals à chaque modification (CI).
25. Scénariser la chute : modifier le prompt, sauter les évals, montrer la
    régression, puis la CI qui la bloque.

### Phase 8 : intégration à la formation

26. Préparer un mode démo : un bouton par étape pour activer chaque brique, en
    miroir des pastilles « Nova contient » du deck.
27. Rédiger le script formateur et la fiche de l'exercice final.
28. Faire un test à blanc chronométré, avec captures d'écran de secours.

## Acquis des itérations précédentes (à réutiliser, le code a été supprimé)

- Embeddings : modèle **multilingue** obligatoire pour le français
  (`paraphrase-multilingual-MiniLM-L12-v2`, ONNX, 384 dim.) — le all-MiniLM
  anglophone rate des passages.
- SDK Anthropic Java 2.34.0 : la constante Opus 4.8 n'existe pas, utiliser
  `Model.of("claude-opus-4-8")`.
- Leçon d'architecture démontrée : les garde-fous sont en couches (prompt système /
  habillage du prompt augmenté / contenu du corpus) et **aucune n'est une protection
  de sécurité** — seul le contrôle d'accès côté code l'est (phases 4-5).
- Keycloak : flux *password grant* sur un client public = le plus simple pour les
  démos en ligne de commande et une UI maison.
