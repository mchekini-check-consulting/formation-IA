# Nova — Assistant IA d'entreprise (formation)

Projet fil rouge de formation : construire pas à pas l'assistant IA interne de
**Nova**, société de livraison fictive — en reproduisant d'abord chaque défaillance
classique de l'IA en entreprise, puis la brique qui la corrige.

> **Le plan complet des 8 phases est dans [`CLAUDE.md`](CLAUDE.md).**
> État actuel : **Phase 0 terminée** (cadrage, corpus, données, stack). Tout le
> reste se construit pendant la formation.

## La société fictive

| | |
|---|---|
| **Nom** | Nova |
| **Métier** | Livraison urbaine de colis, écologique et premium |
| **Implantations** | Paris, Lyon, Bordeaux — 120 livreurs (80 % vélo-cargo) |
| **Signature** | « Brillamment livré. » |
| **Concurrent** | **SpeedColis** — low-cost (-30 %), 8 % de colis perdus ; Nova : +15 %, 99,2 % sans incident |

**Services** : Nova Flash (< 2 h), Nova J+1 (avant 13 h), Nova Relais, Nova Frais
(chaîne du froid), Nova Protect (assurance ad valorem jusqu'à 1 000 €).

## Contenu du dépôt (Phase 0)

- **`corpus/`** — 7 documents internes PDF : présentation, CGV, politique de
  remboursement (**plafond 50 €**), assurance (**1 000 €**, franchise 10 €),
  FAQ livreurs, guide d'onboarding, procédure de litiges (**geste commercial
  ≤ 15 €**). Pièges sémantiques volontaires pour le RAG.
- **`seed/`** — données factices SQL, auto-chargées au premier démarrage de
  PostgreSQL : 5 clients, 10 commandes, 6 tickets, table sensible `paie_livreurs`,
  table `utilisateurs`.
- **`docker-compose.yml`** — l'infrastructure complète.

## Les 4 comptes de démonstration

| Compte | Rôle |
|--------|------|
| `marie.dupont` | CLIENT |
| `karim.benali` | LIVREUR |
| `lea.martin` | NOUVELLE_RECRUE (période d'essai) |
| `sophie.moreau` | MANAGER |

(Comptes créés dans Keycloak en Phase 4 — jamais de mots de passe en base.)

## Démarrer l'infrastructure

```bash
docker compose up -d
```

| Service | URL locale | Identifiants |
|---------|-----------|--------------|
| PostgreSQL + pgvector | `localhost:5432` | base/user/mdp : `nova` |
| Keycloak | http://localhost:8888 | admin / admin |
| Redis | `localhost:6379` | — |
| Langfuse | http://localhost:3002 | compte à créer au premier accès |

Pour réinitialiser les données : `docker compose down -v && docker compose up -d`.
