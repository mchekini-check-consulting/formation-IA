#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Étape 19 — Simulation de la facture des « Où est mon colis ? ».

Principe : on n'envoie PAS 2 000 vraies requêtes (coût et durée inutiles) — on
mesure un ÉCHANTILLON de requêtes réelles contre l'assistant, puis on extrapole
à la volumétrie cible. Chaque mesure vient du champ `utilisation` renvoyé par
l'API (tokens réels facturés par Anthropic, cumulés sur la boucle agentique).

Usage :
    python3 scripts/simulation_couts.py [--echantillon 5] [--cible 2000] [--modele opus|haiku]
"""
import argparse
import json
import sys
import urllib.request

APP = "http://localhost:8080"
KEYCLOAK = "http://localhost:8888/realms/nova/protocol/openid-connect/token"
QUESTION = "Bonjour, où en est mon colis NOV-2026-0001 ?"


def jeton(utilisateur: str, mot_de_passe: str) -> str:
    corps = (f"client_id=nova-app&grant_type=password"
             f"&username={utilisateur}&password={mot_de_passe}").encode()
    req = urllib.request.Request(KEYCLOAK, data=corps,
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    return json.load(urllib.request.urlopen(req))["access_token"]


def poser_question(jeton_jwt: str, modele: str) -> dict:
    corps = json.dumps({"question": QUESTION, "historique": [], "modele": modele}).encode()
    req = urllib.request.Request(f"{APP}/api/chat", data=corps, headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {jeton_jwt}",
    })
    return json.load(urllib.request.urlopen(req, timeout=300))


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--echantillon", type=int, default=5, help="nombre de requêtes réelles mesurées")
    p.add_argument("--cible", type=int, default=2000, help="volumétrie à projeter")
    p.add_argument("--modele", default="opus", choices=["opus", "haiku"])
    args = p.parse_args()

    print(f"Question simulée : « {QUESTION} »")
    print(f"Modèle : {args.modele} — échantillon : {args.echantillon} requêtes réelles\n")

    jwt = jeton("marie.dupont", "nova123")
    mesures = []
    for i in range(1, args.echantillon + 1):
        reponse = poser_question(jwt, args.modele)
        u = reponse.get("utilisation")
        if not u:
            sys.exit("L'API ne renvoie pas le champ utilisation — vérifier l'application.")
        mesures.append(u)
        print(f"  requête {i} : {u['tokensEntree']:>7,} tokens entrée · "
              f"{u['tokensSortie']:>5,} sortie · {u['appelsModele']} appel(s) · "
              f"${u['coutDollars']:.4f}".replace(",", " "))

    n = len(mesures)
    moy_entree = sum(m["tokensEntree"] for m in mesures) / n
    moy_sortie = sum(m["tokensSortie"] for m in mesures) / n
    moy_appels = sum(m["appelsModele"] for m in mesures) / n
    moy_cout = sum(m["coutDollars"] for m in mesures) / n

    print("\n--- Moyenne par requête ---")
    print(f"  {moy_entree:,.0f} tokens entrée · {moy_sortie:,.0f} sortie · "
          f"{moy_appels:.1f} appels modèle · ${moy_cout:.4f}".replace(",", " "))

    print(f"\n--- PROJECTION sur {args.cible:,} requêtes ---".replace(",", " "))
    print(f"  Tokens entrée : {moy_entree * args.cible:>14,.0f}".replace(",", " "))
    print(f"  Tokens sortie : {moy_sortie * args.cible:>14,.0f}".replace(",", " "))
    print(f"  Appels modèle : {moy_appels * args.cible:>14,.0f}".replace(",", " "))
    print(f"  FACTURE       : {'$':>10}{moy_cout * args.cible:,.2f}".replace(",", " "))
    print("\nRappel : chaque requête embarque le prompt système, les définitions des "
          "outils MCP\net les extraits RAG — c'est l'entrée qui coûte, pas la réponse.")


if __name__ == "__main__":
    main()
