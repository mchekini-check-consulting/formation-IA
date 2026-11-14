-- Schéma métier Nova (données factices de formation)

CREATE TABLE clients (
    id              BIGSERIAL PRIMARY KEY,
    nom             VARCHAR(80)  NOT NULL,
    prenom          VARCHAR(80)  NOT NULL,
    email           VARCHAR(160) NOT NULL UNIQUE,
    telephone       VARCHAR(20),
    ville           VARCHAR(80),
    date_inscription DATE        NOT NULL
);

CREATE TABLE commandes (
    id              BIGSERIAL PRIMARY KEY,
    reference       VARCHAR(20)  NOT NULL UNIQUE,     -- ex. NOV-2026-0001
    client_id       BIGINT       NOT NULL REFERENCES clients(id),
    service         VARCHAR(20)  NOT NULL,            -- FLASH | J1 | RELAIS | FRAIS
    statut          VARCHAR(20)  NOT NULL,            -- EN_COURS | LIVREE | LITIGE | ANNULEE
    valeur_declaree NUMERIC(8,2) NOT NULL,
    assurance_protect BOOLEAN    NOT NULL DEFAULT FALSE,
    prix_transport  NUMERIC(6,2) NOT NULL,
    adresse_livraison VARCHAR(200) NOT NULL,
    date_commande   TIMESTAMP    NOT NULL,
    date_livraison  TIMESTAMP
);

CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    reference       VARCHAR(20)  NOT NULL UNIQUE,     -- ex. TCK-2026-0001
    commande_id     BIGINT       NOT NULL REFERENCES commandes(id),
    motif           VARCHAR(30)  NOT NULL,            -- PERTE | AVARIE | RETARD | ERREUR_LIVRAISON
    statut          VARCHAR(20)  NOT NULL,            -- OUVERT | EN_ATTENTE_CLIENT | ESCALADE | CLOS
    description     TEXT         NOT NULL,
    montant_demande NUMERIC(8,2),
    montant_accorde NUMERIC(8,2),
    date_ouverture  TIMESTAMP    NOT NULL,
    date_cloture    TIMESTAMP
);

-- Table SENSIBLE : utilisée pour les démonstrations de contrôle d'accès.
-- Seul le rôle MANAGER doit pouvoir y accéder via l'assistant.
CREATE TABLE paie_livreurs (
    id              BIGSERIAL PRIMARY KEY,
    matricule       VARCHAR(10)  NOT NULL UNIQUE,
    nom             VARCHAR(80)  NOT NULL,
    prenom          VARCHAR(80)  NOT NULL,
    ville           VARCHAR(80)  NOT NULL,
    salaire_brut_mensuel NUMERIC(8,2) NOT NULL,
    prime_qualite   NUMERIC(6,2) NOT NULL DEFAULT 0,  -- 150 € si note >= 4,8
    km_velo_mois    INTEGER      NOT NULL DEFAULT 0,  -- indemnité 0,25 €/km
    note_moyenne    NUMERIC(3,2),
    date_embauche   DATE         NOT NULL
);

-- Comptes applicatifs : le mot de passe vit dans Keycloak, jamais ici.
CREATE TABLE utilisateurs (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(60)  NOT NULL UNIQUE,     -- aligné sur le username Keycloak
    role            VARCHAR(20)  NOT NULL,            -- CLIENT | LIVREUR | NOUVELLE_RECRUE | MANAGER
    email           VARCHAR(160) NOT NULL,
    client_id       BIGINT REFERENCES clients(id),         -- renseigné pour les clients
    matricule_livreur VARCHAR(10)                           -- renseigné pour les livreurs
);
