/**
 * Serveur MCP « remboursement » — les actions où l'argent sort.
 * Volontairement NAÏF en phase 3 : aucun plafond, aucun contrôle de droits —
 * c'est la matière des démos d'échec, corrigées en phases 4 et 5.
 */
const { demarrerServeur, pool, profilUtilisateur, commandeAppartientAuClient, accesRefuse } = require("./commun");

/** Étape 15 : CLIENT -> uniquement ses commandes ; MANAGER -> tout ; autres -> refus. */
async function controlerAcces(reference, utilisateur) {
  if (utilisateur.roles.includes("MANAGER")) return;
  const profil = await profilUtilisateur(utilisateur.username);
  if (profil == null || profil.client_id == null) {
    throw accesRefuse(
      "Accès refusé : les remboursements sont réservés au client concerné et aux managers. "
      + `Le compte authentifié « ${utilisateur.username} » (rôles : ${utilisateur.roles.join(", ")}) `
      + "n'est pas un compte client.");
  }
  if (!(await commandeAppartientAuClient(reference, profil.client_id))) {
    throw accesRefuse(
      `Accès refusé : la commande ${reference} n'appartient pas au compte authentifié `
      + `« ${utilisateur.username} ».`);
  }
}

const pret = pool.query(`
  CREATE TABLE IF NOT EXISTS remboursements (
    id                 BIGSERIAL PRIMARY KEY,
    reference_virement VARCHAR(20)  NOT NULL UNIQUE,
    reference_commande VARCHAR(20)  NOT NULL,
    montant            NUMERIC(8,2) NOT NULL,
    motif              TEXT         NOT NULL,
    date_execution     TIMESTAMP    NOT NULL DEFAULT NOW()
  )`);

const outils = [
  {
    nom: "consulter_remboursements",
    description:
      "Liste les remboursements déjà effectués pour une commande. À appeler avant tout "
      + "nouveau remboursement pour éviter un double paiement.",
    parametres: {
      type: "object",
      properties: {
        reference_commande: { type: "string", description: "Référence de la commande, ex. NOV-2026-0002" },
      },
      required: ["reference_commande"],
    },
    executer: async ({ reference_commande }, utilisateur) => {
      await controlerAcces(reference_commande, utilisateur);
      await pret;
      const r = await pool.query(
        `SELECT reference_virement, montant, motif, date_execution
         FROM remboursements WHERE reference_commande = $1 ORDER BY date_execution`,
        [reference_commande]);
      return { remboursements: r.rows };
    },
  },
  {
    nom: "effectuer_remboursement",
    description:
      "Exécute un remboursement en euros vers le client de la commande. ACTION IRRÉVERSIBLE : "
      + "le virement part immédiatement. Renvoie la référence du virement.",
    parametres: {
      type: "object",
      properties: {
        reference_commande: { type: "string", description: "Référence de la commande concernée" },
        montant: { type: "number", description: "Montant à rembourser en euros" },
        motif: { type: "string", description: "Justification du remboursement" },
      },
      required: ["reference_commande", "montant", "motif"],
    },
    executer: async ({ reference_commande, montant, motif }, utilisateur) => {
      await controlerAcces(reference_commande, utilisateur);
      await pret;
      const c = await pool.query("SELECT id FROM commandes WHERE reference = $1", [reference_commande]);
      if (c.rowCount === 0) throw new Error(`Commande introuvable : ${reference_commande}`);
      if (!(montant > 0)) throw new Error("Le montant doit être strictement positif.");
      const n = await pool.query("SELECT count(*)::int AS nb FROM remboursements");
      const reference_virement = `VIR-2026-${String(n.rows[0].nb + 1).padStart(4, "0")}`;
      await pool.query(
        `INSERT INTO remboursements (reference_virement, reference_commande, montant, motif)
         VALUES ($1, $2, $3, $4)`,
        [reference_virement, reference_commande, montant, motif]);
      return {
        reference_virement,
        montant,
        statut: "EXECUTE",
        message: `Virement ${reference_virement} de ${montant} € exécuté pour ${reference_commande}.`,
      };
    },
  },
];

demarrerServeur("remboursement", 7002, outils);
