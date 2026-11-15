/**
 * Socle commun des serveurs MCP de Nova.
 *
 * Chaque serveur expose :
 *   GET  /outils           -> découverte : définitions des outils (libre d'accès)
 *   POST /outils/:nom      -> exécution d'un outil (JETON JWT OBLIGATOIRE — étape 15)
 *
 * Étape 15 : le serveur VÉRIFIE la signature du jeton auprès de Keycloak (JWKS)
 * et transmet l'identité vérifiée {username, roles} à chaque outil. Le filtrage
 * se fait ICI, côté serveur — jamais dans le prompt.
 */
const express = require("express");
const { Pool } = require("pg");
const { verify } = require("jsonwebtoken");
const jwksRsa = require("jwks-rsa");

const pool = new Pool({
  host: "localhost",
  port: 5432,
  database: "nova",
  user: "nova",
  password: "nova",
});

const clientJwks = jwksRsa({
  jwksUri: "http://localhost:8888/realms/nova/protocol/openid-connect/certs",
  cache: true,
});

function clePublique(entete, rappel) {
  clientJwks.getSigningKey(entete.kid, (err, cle) =>
    rappel(err, cle && cle.getPublicKey()));
}

/** Middleware : exige un jeton Keycloak valide, attache req.utilisateur = {username, roles}. */
function authentifier(req, res, next) {
  const correspondance = (req.headers.authorization || "").match(/^Bearer (.+)$/);
  if (!correspondance) {
    return res.status(401).json({ erreur: "Appel non authentifié : jeton JWT manquant." });
  }
  verify(correspondance[1], clePublique, { algorithms: ["RS256"] }, (err, charge) => {
    if (err) {
      return res.status(401).json({ erreur: `Jeton invalide : ${err.message}` });
    }
    req.utilisateur = {
      username: charge.preferred_username,
      roles: (charge.realm_access?.roles || []).filter((r) => r === r.toUpperCase()),
    };
    next();
  });
}

/** Profil applicatif de l'utilisateur authentifié (mapping username -> métier). */
async function profilUtilisateur(username) {
  const r = await pool.query(
    "SELECT role, client_id, matricule_livreur FROM utilisateurs WHERE username = $1",
    [username]);
  return r.rows[0] || null;
}

/** La commande appartient-elle au client identifié ? */
async function commandeAppartientAuClient(reference, clientId) {
  if (clientId == null) return false;
  const r = await pool.query(
    "SELECT 1 FROM commandes WHERE reference = $1 AND client_id = $2",
    [reference, clientId]);
  return r.rowCount > 0;
}

function demarrerServeur(nomServeur, port, outils, routesSupplementaires) {
  const app = express();
  app.use(express.json());
  if (routesSupplementaires) {
    routesSupplementaires(app, { authentifier });
  }

  app.get("/outils", (req, res) => {
    res.json(outils.map(({ nom, description, parametres }) => ({ nom, description, parametres })));
  });

  app.post("/outils/:nom", authentifier, async (req, res) => {
    const outil = outils.find((o) => o.nom === req.params.nom);
    if (!outil) {
      return res.status(404).json({ erreur: `Outil inconnu : ${req.params.nom}` });
    }
    try {
      const resultat = await outil.executer(req.body || {}, req.utilisateur);
      console.log(`[${nomServeur}] ${req.utilisateur.username} -> ${outil.nom}(${JSON.stringify(req.body)}) OK`);
      res.json(resultat);
    } catch (e) {
      const statut = e.statut || 400;
      console.error(`[${nomServeur}] ${req.utilisateur.username} -> ${outil.nom} REFUS/ERREUR : ${e.message}`);
      res.status(statut).json({ erreur: e.message });
    }
  });

  app.listen(port, () => console.log(`[MCP ${nomServeur}] en écoute sur le port ${port}`));
}

/** Erreur 403 à lever dans un outil quand l'accès est refusé. */
function accesRefuse(message) {
  const e = new Error(message);
  e.statut = 403;
  return e;
}

module.exports = { demarrerServeur, pool, profilUtilisateur, commandeAppartientAuClient, accesRefuse };
