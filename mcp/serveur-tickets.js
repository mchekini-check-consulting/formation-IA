/**
 * Serveur MCP « tickets » — consultation des commandes et gestion des réclamations.
 * Étape 15 : filtrage par rôle sur l'identité vérifiée du jeton — un CLIENT
 * n'accède qu'à SES commandes, un MANAGER à tout, les autres rôles à rien.
 */
const { demarrerServeur, pool, profilUtilisateur, commandeAppartientAuClient, accesRefuse } = require("./commun");

async function controlerAcces(reference, utilisateur) {
  if (utilisateur.roles.includes("MANAGER")) return;
  const profil = await profilUtilisateur(utilisateur.username);
  if (profil == null || profil.client_id == null) {
    throw accesRefuse(
      "Accès refusé : les dossiers de commande sont réservés au client concerné et aux managers. "
      + `Le compte authentifié « ${utilisateur.username} » (rôles : ${utilisateur.roles.join(", ")}) `
      + "n'est pas un compte client.");
  }
  if (!(await commandeAppartientAuClient(reference, profil.client_id))) {
    throw accesRefuse(
      `Accès refusé : la commande ${reference} n'appartient pas au compte authentifié `
      + `« ${utilisateur.username} ».`);
  }
}

const outils = [
  {
    nom: "consulter_commande",
    description:
      "Consulte une commande Nova par sa référence (format NOV-2026-XXXX) : statut, service, "
      + "valeur déclarée, assurance Nova Protect souscrite ou non, dates et adresse de livraison. "
      + "À appeler avant toute action sur un dossier.",
    parametres: {
      type: "object",
      properties: {
        reference: { type: "string", description: "Référence de la commande, ex. NOV-2026-0002" },
      },
      required: ["reference"],
    },
    executer: async ({ reference }, utilisateur) => {
      await controlerAcces(reference, utilisateur);
      const r = await pool.query(
        `SELECT c.reference, c.service, c.statut, c.valeur_declaree, c.assurance_protect,
                c.prix_transport, c.adresse_livraison, c.date_commande, c.date_livraison,
                cl.nom AS client_nom, cl.prenom AS client_prenom, cl.email AS client_email
         FROM commandes c JOIN clients cl ON cl.id = c.client_id
         WHERE c.reference = $1`, [reference]);
      if (r.rowCount === 0) throw new Error(`Commande introuvable : ${reference}`);
      const commande = r.rows[0];
      // Faits temporels dérivés : calculés ICI (le serveur a une horloge, pas le modèle).
      // La règle des 14 jours vient de la politique de remboursement.
      if (commande.date_livraison) {
        const jours = Math.floor((Date.now() - new Date(commande.date_livraison)) / 86_400_000);
        commande.jours_depuis_livraison = jours;
        commande.delai_reclamation_14j_depasse = jours > 14;
      } else {
        commande.jours_depuis_livraison = null;
        commande.delai_reclamation_14j_depasse = false;
      }
      return commande;
    },
  },
  {
    nom: "consulter_tickets_commande",
    description:
      "Liste les tickets de réclamation déjà ouverts pour une commande (motif, statut, montants "
      + "demandés et accordés). À appeler avant de créer un ticket pour éviter les doublons.",
    parametres: {
      type: "object",
      properties: {
        reference_commande: { type: "string", description: "Référence de la commande, ex. NOV-2026-0002" },
      },
      required: ["reference_commande"],
    },
    executer: async ({ reference_commande }, utilisateur) => {
      await controlerAcces(reference_commande, utilisateur);
      const r = await pool.query(
        `SELECT t.reference, t.motif, t.statut, t.description, t.montant_demande,
                t.montant_accorde, t.date_ouverture, t.date_cloture
         FROM tickets t JOIN commandes c ON c.id = t.commande_id
         WHERE c.reference = $1 ORDER BY t.date_ouverture`, [reference_commande]);
      return { tickets: r.rows };
    },
  },
  {
    nom: "creer_ticket",
    description:
      "Ouvre un ticket de réclamation pour une commande. Motifs possibles : PERTE, AVARIE, "
      + "RETARD, ERREUR_LIVRAISON. Renvoie la référence du ticket créé.",
    parametres: {
      type: "object",
      properties: {
        reference_commande: { type: "string", description: "Référence de la commande concernée" },
        motif: { type: "string", enum: ["PERTE", "AVARIE", "RETARD", "ERREUR_LIVRAISON"] },
        description: { type: "string", description: "Description factuelle du problème signalé" },
        montant_demande: { type: "number", description: "Montant réclamé par le client en euros (optionnel)" },
      },
      required: ["reference_commande", "motif", "description"],
    },
    executer: async ({ reference_commande, motif, description, montant_demande }, utilisateur) => {
      await controlerAcces(reference_commande, utilisateur);
      const c = await pool.query("SELECT id FROM commandes WHERE reference = $1", [reference_commande]);
      if (c.rowCount === 0) throw new Error(`Commande introuvable : ${reference_commande}`);
      const n = await pool.query("SELECT count(*)::int AS nb FROM tickets");
      const reference = `TCK-2026-${String(n.rows[0].nb + 1).padStart(4, "0")}`;
      await pool.query(
        `INSERT INTO tickets (reference, commande_id, motif, statut, description, montant_demande, date_ouverture)
         VALUES ($1, $2, $3, 'OUVERT', $4, $5, NOW())`,
        [reference, c.rows[0].id, motif, description, montant_demande ?? null]);
      return { reference, statut: "OUVERT", message: `Ticket ${reference} créé pour ${reference_commande}.` };
    },
  },
];

demarrerServeur("tickets", 7001, outils);
