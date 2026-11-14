/**
 * Socle commun des serveurs MCP factices de Nova.
 * Chaque serveur expose :
 *   GET  /outils           -> découverte : définitions des outils (nom, description, paramètres JSON Schema)
 *   POST /outils/:nom      -> exécution d'un outil avec les arguments JSON
 */
const express = require("express");
const { Pool } = require("pg");

const pool = new Pool({
  host: "localhost",
  port: 5432,
  database: "nova",
  user: "nova",
  password: "nova",
});

function demarrerServeur(nomServeur, port, outils) {
  const app = express();
  app.use(express.json());

  app.get("/outils", (req, res) => {
    res.json(outils.map(({ nom, description, parametres }) => ({ nom, description, parametres })));
  });

  app.post("/outils/:nom", async (req, res) => {
    const outil = outils.find((o) => o.nom === req.params.nom);
    if (!outil) {
      return res.status(404).json({ erreur: `Outil inconnu : ${req.params.nom}` });
    }
    try {
      const resultat = await outil.executer(req.body || {});
      console.log(`[${nomServeur}] ${outil.nom}(${JSON.stringify(req.body)}) -> OK`);
      res.json(resultat);
    } catch (e) {
      console.error(`[${nomServeur}] ${outil.nom} -> ERREUR : ${e.message}`);
      res.status(400).json({ erreur: e.message });
    }
  });

  app.listen(port, () => console.log(`[MCP ${nomServeur}] en écoute sur le port ${port}`));
}

module.exports = { demarrerServeur, pool };
