-- Données factices Nova

INSERT INTO clients (nom, prenom, email, telephone, ville, date_inscription) VALUES
('Dupont',   'Marie',    'marie.dupont@exemple.fr',    '06 12 34 56 01', 'Paris',    '2024-03-12'),
('Lambert',  'Thomas',   'thomas.lambert@exemple.fr',  '06 12 34 56 02', 'Lyon',     '2024-07-25'),
('Nguyen',   'Linh',     'linh.nguyen@exemple.fr',     '06 12 34 56 03', 'Bordeaux', '2025-01-18'),
('Rossi',    'Elena',    'elena.rossi@exemple.fr',     '06 12 34 56 04', 'Paris',    '2025-06-02'),
('Petit',    'Julien',   'julien.petit@exemple.fr',    '06 12 34 56 05', 'Lyon',     '2025-11-30');

INSERT INTO commandes (reference, client_id, service, statut, valeur_declaree, assurance_protect, prix_transport, adresse_livraison, date_commande, date_livraison) VALUES
('NOV-2026-0001', 1, 'FLASH',  'LIVREE',   35.00,  FALSE, 12.90, '12 rue de la Roquette, 75011 Paris',      '2026-10-02 09:14', '2026-10-02 10:48'),
('NOV-2026-0002', 1, 'J1',     'LITIGE',   75.00,  FALSE,  8.90, '8 avenue Parmentier, 75011 Paris',        '2026-10-15 16:02', '2026-10-16 11:20'),
('NOV-2026-0003', 2, 'RELAIS', 'LIVREE',   18.50,  FALSE,  4.90, 'Relais Presse du Rhône, 69002 Lyon',      '2026-10-20 11:45', '2026-10-22 09:15'),
('NOV-2026-0004', 2, 'FRAIS',  'LIVREE',   42.00,  FALSE, 12.40, '33 cours Gambetta, 69003 Lyon',           '2026-10-28 08:30', '2026-10-28 12:05'),
('NOV-2026-0005', 3, 'J1',     'LITIGE',  480.00,  TRUE,  20.90, '5 rue Sainte-Catherine, 33000 Bordeaux',  '2026-11-03 14:21', NULL),
('NOV-2026-0006', 3, 'FLASH',  'LIVREE',   29.90,  FALSE, 12.90, '18 quai des Chartrons, 33000 Bordeaux',   '2026-11-05 10:05', '2026-11-05 11:32'),
('NOV-2026-0007', 4, 'J1',     'EN_COURS', 55.00,  FALSE,  8.90, '2 rue Oberkampf, 75011 Paris',            '2026-11-12 17:40', NULL),
('NOV-2026-0008', 4, 'RELAIS', 'LIVREE',   12.00,  FALSE,  4.90, 'Relais Tabac du Marais, 75004 Paris',     '2026-11-08 09:55', '2026-11-10 10:10'),
('NOV-2026-0009', 5, 'J1',     'LITIGE',   38.00,  FALSE,  8.90, '77 rue Garibaldi, 69006 Lyon',            '2026-11-09 13:12', '2026-11-11 15:44'),
('NOV-2026-0010', 5, 'FLASH',  'EN_COURS', 22.50,  FALSE, 12.90, '4 place Bellecour, 69002 Lyon',           '2026-11-14 08:50', NULL);

INSERT INTO tickets (reference, commande_id, motif, statut, description, montant_demande, montant_accorde, date_ouverture, date_cloture) VALUES
('TCK-2026-0001', 2, 'AVARIE', 'ESCALADE',
 'Vase en céramique reçu brisé. Le client réclame 75 €. Sans assurance Protect, le remboursement est plafonné à 50 € : escalade au manager pour arbitrage.',
 75.00, NULL, '2026-10-16 14:05', NULL),
('TCK-2026-0002', 3, 'RETARD', 'CLOS',
 'Livraison en relais effectuée à J+2 au lieu de J+1. Avoir de 20 % du transport accordé (0,98 €).',
 NULL, 0.98, '2026-10-22 10:02', '2026-10-23 09:40'),
('TCK-2026-0003', 5, 'PERTE', 'OUVERT',
 'Colis assuré Nova Protect (valeur déclarée 480 €) non livré depuis le 3 novembre. Recherche en cours au hub de Bordeaux. Dossier assurance pré-ouvert, franchise 10 € applicable.',
 480.00, NULL, '2026-11-06 09:30', NULL),
('TCK-2026-0004', 9, 'ERREUR_LIVRAISON', 'EN_ATTENTE_CLIENT',
 'Colis remis au voisin sans mandat. Le client doit confirmer s''il a pu le récupérer avant tout dédommagement.',
 NULL, NULL, '2026-11-11 16:20', NULL),
('TCK-2026-0005', 4, 'AVARIE', 'CLOS',
 'Chaîne du froid respectée mais carton d''oeufs fissuré. Geste commercial de 12 € accordé par le conseiller (sous le seuil de 15 €).',
 20.00, 12.00, '2026-10-28 13:10', '2026-10-29 11:55'),
('TCK-2026-0006', 8, 'RETARD', 'CLOS',
 'Demande d''avoir pour retard : rejetée, la livraison en relais était dans les délais contractuels.',
 NULL, 0.00, '2026-11-10 11:25', '2026-11-10 15:02');

INSERT INTO paie_livreurs (matricule, nom, prenom, ville, salaire_brut_mensuel, prime_qualite, km_velo_mois, note_moyenne, date_embauche) VALUES
('LIV-042', 'Benali',  'Karim',   'Paris',    2250.00, 150.00, 820, 4.91, '2022-04-11'),
('LIV-057', 'Martin',  'Léa',     'Paris',    2100.00,   0.00, 310, 4.65, '2026-10-01'),
('LIV-018', 'Diallo',  'Moussa',  'Lyon',     2320.00, 150.00, 905, 4.88, '2021-09-06'),
('LIV-033', 'Garcia',  'Sofia',   'Bordeaux', 2250.00,   0.00, 640, 4.52, '2023-02-20'),
('LIV-061', 'Lefort',  'Hugo',    'Lyon',     2180.00, 150.00, 770, 4.83, '2024-08-12');

INSERT INTO utilisateurs (username, role, email, client_id, matricule_livreur) VALUES
('marie.dupont',  'CLIENT',          'marie.dupont@exemple.fr',        1,    NULL),
('karim.benali',  'LIVREUR',         'karim.benali@nova-livraison.fr', NULL, 'LIV-042'),
('lea.martin',    'NOUVELLE_RECRUE', 'lea.martin@nova-livraison.fr',   NULL, 'LIV-057'),
('sophie.moreau', 'MANAGER',         'sophie.moreau@nova-livraison.fr', NULL, NULL);
