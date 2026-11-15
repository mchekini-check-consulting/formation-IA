/**
 * Serveur MCP « remboursement ».
 * Étape 18 : PLUS AUCUN virement direct — l'outil effectuer_remboursement enregistre
 * une DEMANDE en attente de validation humaine. La validation/le rejet passent par
 * des endpoints HTTP hors de portée du modèle (ils ne sont pas dans /outils) :
 * c'est l'interface web, donc un humain, qui clique.
 */
const { demarrerServeur, pool, profilUtilisateur, commandeAppartientAuClient, accesRefuse } = require("./commun");

const pret = (async () => {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS remboursements (
      id                 BIGSERIAL PRIMARY KEY,
      reference_virement VARCHAR(20)  NOT NULL UNIQUE,
      reference_commande VARCHAR(20)  NOT NULL,
      montant            NUMERIC(8,2) NOT NULL,
      motif              TEXT         NOT NULL,
      date_execution     TIMESTAMP    NOT NULL DEFAULT NOW()
    )`);
  await pool.query(`
    CREATE TABLE IF NOT EXISTS demandes_remboursement (
      id                 BIGSERIAL PRIMARY KEY,
      reference          VARCHAR(20)  NOT NULL UNIQUE,
      reference_commande VARCHAR(20)  NOT NULL,
      montant            NUMERIC(8,2) NOT NULL,
      motif              TEXT         NOT NULL,
      demandeur          VARCHAR(60)  NOT NULL,
      statut             VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
      date_demande       TIMESTAMP    NOT NULL DEFAULT NOW(),
      decideur           VARCHAR(60),
      date_decision      TIMESTAMP
    )`);
})();

/** CLIENT : uniquement ses commandes ; MANAGER : tout ; autres : refus. */
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

const outils = [
  {
    nom: "consulter_remboursements",
    description:
      "Liste les remboursements effectués ET les demandes en attente de validation humaine "
      + "pour une commande. À appeler avant toute nouvelle demande pour éviter les doublons.",
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
      const executes = await pool.query(
        `SELECT reference_virement, montant, motif, date_execution
         FROM remboursements WHERE reference_commande = $1 ORDER BY date_execution`,
        [reference_commande]);
      const demandes = await pool.query(
        `SELECT reference, montant, motif, statut, demandeur, date_demande
         FROM demandes_remboursement WHERE reference_commande = $1 ORDER BY date_demande`,
        [reference_commande]);
      return { remboursements_executes: executes.rows, demandes: demandes.rows };
    },
  },
  {
    nom: "effectuer_remboursement",
    description:
      "Enregistre une DEMANDE de remboursement pour une commande. Aucun virement ne part "
      + "immédiatement : chaque demande doit être validée par un humain dans l'interface "
      + "Nova (étape 18). Renvoie la référence de la demande en attente.",
    parametres: {
      type: "object",
      properties: {
        reference_commande: { type: "string", description: "Référence de la commande concernée" },
        montant: { type: "number", description: "Montant demandé en euros" },
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
      const n = await pool.query("SELECT count(*)::int AS nb FROM demandes_remboursement");
      const reference = `DEM-2026-${String(n.rows[0].nb + 1).padStart(4, "0")}`;
      await pool.query(
        `INSERT INTO demandes_remboursement (reference, reference_commande, montant, motif, demandeur)
         VALUES ($1, $2, $3, $4, $5)`,
        [reference, reference_commande, montant, motif, utilisateur.username]);
      return {
        reference_demande: reference,
        statut: "EN_ATTENTE_VALIDATION",
        message: `Demande ${reference} de ${montant} € enregistrée pour ${reference_commande}. `
          + "Aucun virement ne part sans validation humaine dans l'interface Nova.",
      };
    },
  },
];

/** Étape 18 : endpoints de validation HUMAINE — hors de portée du modèle (pas dans /outils). */
function routesValidation(app, { authentifier }) {
  const exigerManager = (req, res, next) => {
    if (!req.utilisateur.roles.includes("MANAGER")) {
      return res.status(403).json({ erreur: "Réservé aux managers." });
    }
    next();
  };

  app.get("/demandes", authentifier, exigerManager, async (req, res) => {
    await pret;
    const r = await pool.query(
      `SELECT reference, reference_commande, montant, motif, demandeur, date_demande
       FROM demandes_remboursement WHERE statut = 'EN_ATTENTE' ORDER BY date_demande`);
    res.json({ demandes: r.rows });
  });

  app.post("/demandes/:ref/valider", authentifier, exigerManager, async (req, res) => {
    await pret;
    const d = await pool.query(
      "SELECT * FROM demandes_remboursement WHERE reference = $1 AND statut = 'EN_ATTENTE'",
      [req.params.ref]);
    if (d.rowCount === 0) return res.status(404).json({ erreur: "Demande introuvable ou déjà décidée." });
    const demande = d.rows[0];
    const n = await pool.query("SELECT count(*)::int AS nb FROM remboursements");
    const virement = `VIR-2026-${String(n.rows[0].nb + 1).padStart(4, "0")}`;
    await pool.query(
      `INSERT INTO remboursements (reference_virement, reference_commande, montant, motif)
       VALUES ($1, $2, $3, $4)`,
      [virement, demande.reference_commande, demande.montant,
       demande.motif + ` (demande ${demande.reference} validée par ${req.utilisateur.username})`]);
    await pool.query(
      `UPDATE demandes_remboursement SET statut = 'VALIDEE', decideur = $1, date_decision = NOW()
       WHERE reference = $2`, [req.utilisateur.username, req.params.ref]);
    console.log(`[remboursement] ${req.utilisateur.username} VALIDE ${req.params.ref} -> ${virement}`);
    res.json({ reference_demande: req.params.ref, reference_virement: virement, statut: "VALIDEE" });
  });

  app.post("/demandes/:ref/rejeter", authentifier, exigerManager, async (req, res) => {
    await pret;
    const r = await pool.query(
      `UPDATE demandes_remboursement SET statut = 'REJETEE', decideur = $1, date_decision = NOW()
       WHERE reference = $2 AND statut = 'EN_ATTENTE'`,
      [req.utilisateur.username, req.params.ref]);
    if (r.rowCount === 0) return res.status(404).json({ erreur: "Demande introuvable ou déjà décidée." });
    console.log(`[remboursement] ${req.utilisateur.username} REJETTE ${req.params.ref}`);
    res.json({ reference_demande: req.params.ref, statut: "REJETEE" });
  });
}

demarrerServeur("remboursement", 7002, outils, routesValidation);
