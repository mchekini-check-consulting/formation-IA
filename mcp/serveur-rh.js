/**
 * Serveur MCP « RH » — données de paie des livreurs.
 * Étape 15 : le filtrage se fait ICI, sur l'identité VÉRIFIÉE du jeton :
 *   - MANAGER          : accès à toutes les fiches ;
 *   - LIVREUR / NOUVELLE_RECRUE : uniquement SA propre fiche (matricule du profil) ;
 *   - CLIENT et autres : aucun accès.
 * Ce que l'utilisateur RACONTE dans le prompt n'a aucune importance.
 */
const { demarrerServeur, pool, profilUtilisateur, accesRefuse } = require("./commun");

const outils = [
  {
    nom: "consulter_paie",
    description:
      "Consulte la fiche de paie d'un livreur Nova : salaire brut mensuel, prime qualité, "
      + "kilomètres vélo du mois, note moyenne et date d'embauche. Recherche par matricule "
      + "(ex. LIV-042) ou par nom de famille. Accès : un livreur ne peut consulter que sa "
      + "propre fiche ; un manager peut tout consulter.",
    parametres: {
      type: "object",
      properties: {
        matricule: { type: "string", description: "Matricule du livreur, ex. LIV-042" },
        nom: { type: "string", description: "Nom de famille du livreur, ex. Benali" },
      },
      required: [],
    },
    executer: async ({ matricule, nom }, utilisateur) => {
      const estManager = utilisateur.roles.includes("MANAGER");

      if (!estManager) {
        const profil = await profilUtilisateur(utilisateur.username);
        const monMatricule = profil?.matricule_livreur;
        if (!monMatricule) {
          throw accesRefuse(
            `Accès refusé : les données de paie sont réservées aux livreurs (leur propre fiche) `
            + `et aux managers. Le compte authentifié « ${utilisateur.username} » `
            + `(rôles : ${utilisateur.roles.join(", ")}) n'y a pas accès.`);
        }
        // Un livreur ne voit QUE sa fiche — quels que soient les critères demandés
        if ((matricule && matricule !== monMatricule) || nom) {
          throw accesRefuse(
            `Accès refusé : le compte authentifié « ${utilisateur.username} » ne peut consulter `
            + `que sa propre fiche de paie (${monMatricule}). La fiche demandée ne lui appartient pas.`);
        }
        matricule = monMatricule;
        nom = undefined;
      }

      if (!matricule && !nom) throw new Error("Fournir un matricule ou un nom.");
      const r = await pool.query(
        `SELECT matricule, nom, prenom, ville, salaire_brut_mensuel, prime_qualite,
                km_velo_mois, note_moyenne, date_embauche
         FROM paie_livreurs
         WHERE ($1::text IS NULL OR matricule = $1)
           AND ($2::text IS NULL OR lower(nom) = lower($2))`,
        [matricule ?? null, nom ?? null]);
      if (r.rowCount === 0) throw new Error("Aucun livreur trouvé avec ces critères.");
      return { fiches_de_paie: r.rows };
    },
  },
];

demarrerServeur("rh", 7004, outils);
