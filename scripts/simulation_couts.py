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


def poser_question(jeton_jwt: str, modele: str, sans_cache: bool = True) -> dict:
    corps = json.dumps({"question": QUESTION, "historique": [], "modele": modele,
                        "sansCache": sans_cache}).encode()
    req = urllib.request.Request(f"{APP}/api/chat", data=corps, headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {jeton_jwt}",
    })
    return json.load(urllib.request.urlopen(req, timeout=300))


def comparer(jwt: str, cible: int) -> None:
    """Étape 21 : les deux factures côte à côte — avant/après cache + routeur."""
    print("══════════ FACTURE 1 — architecture naïve (Opus, sans cache) ══════════")
    base = poser_question(jwt, "opus", sans_cache=True)["utilisation"]
    cout_naif = base["coutDollars"]
    print(f"  1 requête mesurée : {base['tokensEntree']:,} tokens entrée · "
          f"${cout_naif:.4f}".replace(",", " "))
    facture_naive = cout_naif * cible
    print(f"  → {cible:,} requêtes × ${cout_naif:.4f} = ${facture_naive:,.2f}\n".replace(",", " "))

    print("══════ FACTURE 2 — architecture optimisée (routeur auto + cache Redis) ══════")
    premiere = poser_question(jwt, "auto", sans_cache=False)
    u1 = premiere["utilisation"]
    if premiere.get("depuisCache"):
        print("  (cache déjà chaud : 1re requête servie par Redis)")
        cout_premiere = 0.0
    else:
        cout_premiere = u1["coutDollars"]
        print(f"  1re requête (cache froid)  : {u1['modele']} · ${cout_premiere:.4f}"
              + (f" · {premiere.get('routage')}" if premiere.get("routage") else ""))
    suivante = poser_question(jwt, "auto", sans_cache=False)
    assert suivante.get("depuisCache"), "la 2e requête aurait dû venir du cache !"
    print(f"  requêtes suivantes          : ⚡ cache Redis · $0.0000")
    facture_optimisee = cout_premiere  # 1 miss + (cible-1) hits à ~0 $
    print(f"  → 1 appel modèle + {cible - 1:,} réponses servies par Redis "
          f"= ${facture_optimisee:,.4f}\n".replace(",", " "))

    print("══════════════════════════ COMPARAISON ══════════════════════════")
    print(f"  Architecture naïve     : ${facture_naive:>10,.2f}".replace(",", " "))
    print(f"  Cache + routeur        : ${facture_optimisee:>10,.4f}".replace(",", " "))
    if facture_optimisee > 0:
        print(f"  Économie               : {facture_naive / facture_optimisee:,.0f}× "
              f"(−{(1 - facture_optimisee / facture_naive) * 100:.2f} %)".replace(",", " "))


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--echantillon", type=int, default=5, help="nombre de requêtes réelles mesurées")
    p.add_argument("--cible", type=int, default=2000, help="volumétrie à projeter")
    p.add_argument("--modele", default="opus", choices=["opus", "haiku", "auto"])
    p.add_argument("--comparer", action="store_true",
                   help="étape 21 : facture naïve vs facture cache+routeur")
    args = p.parse_args()

    if args.comparer:
        comparer(jeton("marie.dupont", "nova123"), args.cible)
        return

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
