/** Serveur MCP « notification d'équipe » — escalades et alertes internes (simulées, persistées). */
const { demarrerServeur, pool } = require("./commun");

const pret = pool.query(`
  CREATE TABLE IF NOT EXISTS notifications (
    id             BIGSERIAL PRIMARY KEY,
    reference      VARCHAR(20) NOT NULL UNIQUE,
    equipe         VARCHAR(20) NOT NULL,
    sujet          VARCHAR(200) NOT NULL,
    message        TEXT        NOT NULL,
    urgence        VARCHAR(10) NOT NULL DEFAULT 'NORMALE',
    date_envoi     TIMESTAMP   NOT NULL DEFAULT NOW()
  )`);

const outils = [
  {
    nom: "notifier_equipe",
    description:
      "Envoie une notification interne à une équipe Nova. À utiliser pour les escalades "
      + "(remboursement au-delà des règles, geste commercial > 15 €, litige complexe -> MANAGER), "
      + "les incidents de tournée (-> DISPATCH) ou les sujets salariés (-> RH).",
    parametres: {
      type: "object",
      properties: {
        equipe: { type: "string", enum: ["MANAGER", "DISPATCH", "RH"], description: "Équipe destinataire" },
        sujet: { type: "string", description: "Objet court de la notification" },
        message: { type: "string", description: "Contenu détaillé : contexte, références, action attendue" },
        urgence: { type: "string", enum: ["NORMALE", "HAUTE"], description: "Niveau d'urgence (défaut : NORMALE)" },
      },
      required: ["equipe", "sujet", "message"],
    },
    executer: async ({ equipe, sujet, message, urgence }, utilisateur) => {
      // Traçabilité : l'émetteur vérifié (jeton) est consigné dans la notification
      message = `[émis via l'assistant par ${utilisateur.username}] ` + message;
      await pret;
      const n = await pool.query("SELECT count(*)::int AS nb FROM notifications");
      const reference = `NOT-2026-${String(n.rows[0].nb + 1).padStart(4, "0")}`;
      await pool.query(
        `INSERT INTO notifications (reference, equipe, sujet, message, urgence)
         VALUES ($1, $2, $3, $4, $5)`,
        [reference, equipe, sujet, message, urgence ?? "NORMALE"]);
      return { reference, statut: "ENVOYEE", message: `Notification ${reference} transmise à l'équipe ${equipe}.` };
    },
  },
];

demarrerServeur("notification", 7003, outils);
