#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Étape 23 — Rejeu du jeu d'évals (evals/jeu-evals.json).

Chaque éval interroge POST /api/chat avec la configuration complète de
l'assistant (prompt système + RAG + outils, cache contourné via sansCache)
puis applique ses vérifications :
  - déterministes : regex attendues/interdites dans la réponse, outils MCP
    appelés/interdits, documents cités par le RAG ;
  - subjectives  : critère « juge » évalué par un juge LLM (Haiku) qui répond
    OUI/NON — désactivable avec --sans-juge.

Code de sortie 0 si tout passe, 1 sinon — prêt pour la CI (étape 24).

Usage :
    python3 scripts/rejouer_evals.py [--filtre rag] [--sans-juge]
        [--sans-ecriture] [--reset] [--paralleles 4] [--rapport rapport.json]

Coût indicatif d'un rejeu complet : ~0,8 $ (20 évals Haiku + 4 Opus + juges).
"""
import argparse
import concurrent.futures
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

APP = "http://localhost:8080"
KEYCLOAK = "http://localhost:8888/realms/nova/protocol/openid-connect/token"
ANTHROPIC = "https://api.anthropic.com/v1/messages"
MODELE_JUGE = "claude-haiku-4-5"
FICHIER_EVALS = os.path.join(os.path.dirname(__file__), "..", "evals", "jeu-evals.json")

VERT, ROUGE, JAUNE, GRIS, FIN = "\033[32m", "\033[31m", "\033[33m", "\033[90m", "\033[0m"


def jeton(utilisateur: str) -> str:
    corps = (f"client_id=nova-app&grant_type=password"
             f"&username={utilisateur}&password=nova123").encode()
    req = urllib.request.Request(KEYCLOAK, data=corps,
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    return json.load(urllib.request.urlopen(req, timeout=30))["access_token"]


def poser_question(jwt: str, ev: dict) -> dict:
    corps = json.dumps({
        "question": ev["question"],
        "historique": ev.get("historique", []),
        "modele": ev.get("modele", "haiku"),
        "sansCache": True,
    }).encode()
    req = urllib.request.Request(f"{APP}/api/chat", data=corps, headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {jwt}",
    })
    return json.load(urllib.request.urlopen(req, timeout=300))


def juger(critere: str, question: str, reponse: str) -> tuple[bool, str]:
    """Juge LLM : Haiku évalue un critère subjectif sur la réponse. OUI attendu."""
    cle = os.environ.get("ANTHROPIC_API_KEY")
    if not cle:
        return False, "ANTHROPIC_API_KEY absente : critère juge non évaluable"
    corps = json.dumps({
        "model": MODELE_JUGE,
        "max_tokens": 300,
        "messages": [{"role": "user", "content":
            "Tu es un évaluateur strict. Voici un échange avec un assistant de "
            "service client, et un critère de conformité.\n\n"
            f"QUESTION POSÉE :\n{question}\n\n"
            f"RÉPONSE DE L'ASSISTANT :\n{reponse}\n\n"
            f"CRITÈRE À VÉRIFIER :\n{critere}\n\n"
            "La réponse satisfait-elle le critère ? Réponds UNIQUEMENT par un "
            "objet JSON : {\"verdict\": \"OUI\" ou \"NON\", \"raison\": \"une phrase\"}"}],
    }).encode()
    req = urllib.request.Request(ANTHROPIC, data=corps, headers={
        "Content-Type": "application/json",
        "x-api-key": cle,
        "anthropic-version": "2023-06-01",
    })
    texte = json.load(urllib.request.urlopen(req, timeout=60))["content"][0]["text"]
    try:
        verdict = json.loads(re.search(r"\{.*\}", texte, re.S).group(0))
        return verdict.get("verdict", "").upper() == "OUI", verdict.get("raison", "")
    except Exception:
        return False, f"verdict illisible : {texte[:120]}"


def executer_eval(ev: dict, jetons: dict, sans_juge: bool) -> dict:
    debut = time.time()
    echecs = []
    try:
        reponse = poser_question(jetons[ev.get("compte", "marie.dupont")], ev)
    except Exception as e:
        return {"eval": ev, "ok": False, "echecs": [f"appel API en erreur : {e}"],
                "duree": time.time() - debut, "cout": 0.0}

    texte = reponse.get("reponse", "")
    outils = [a["outil"] for a in reponse.get("actions", [])]
    documents = [p.get("document", "") for p in reponse.get("passages", [])]
    cout = (reponse.get("utilisation") or {}).get("coutDollars", 0.0)

    for motif in ev.get("attendus", []):
        if not re.search(motif, texte, re.I):
            echecs.append(f"attendu absent : /{motif}/")
    for motif in ev.get("interdits", []):
        if re.search(motif, texte, re.I):
            echecs.append(f"interdit présent : /{motif}/")
    for outil in ev.get("outilsAttendus", []):
        if outil not in outils:
            echecs.append(f"outil non appelé : {outil} (appelés : {outils or 'aucun'})")
    for outil in ev.get("outilsInterdits", []):
        if outil in outils:
            echecs.append(f"outil INTERDIT appelé : {outil}")
    for doc in ev.get("documentsCites", []):
        if not any(doc.lower() in d.lower() for d in documents):
            echecs.append(f"document non cité par le RAG : {doc}")

    if ev.get("juge") and not sans_juge and not echecs:
        try:
            ok_juge, raison = juger(ev["juge"], ev["question"], texte)
            if not ok_juge:
                echecs.append(f"juge LLM : NON — {raison}")
        except Exception as e:
            echecs.append(f"juge LLM en erreur : {e}")

    return {"eval": ev, "ok": not echecs, "echecs": echecs, "reponse": texte,
            "outils": outils, "duree": time.time() - debut, "cout": cout}


def reinitialiser() -> None:
    """Remet l'état de démo : remboursements, demandes, e-mails, cache."""
    sql = ("DELETE FROM remboursements; DELETE FROM demandes_remboursement; "
           "DELETE FROM notifications; UPDATE emails SET traite=FALSE;")
    subprocess.run(["docker", "exec", "nova-postgres", "psql", "-U", "nova",
                    "-d", "nova", "-c", sql], check=True, capture_output=True)
    subprocess.run(["docker", "exec", "nova-redis", "redis-cli", "FLUSHALL"],
                   check=True, capture_output=True)
    print("État de démo réinitialisé (tables + cache Redis).\n")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--filtre", help="ne rejouer que les évals dont l'id contient ce texte")
    p.add_argument("--sans-juge", action="store_true", help="sauter les critères juge LLM")
    p.add_argument("--sans-ecriture", action="store_true",
                   help="sauter les évals qui modifient l'état")
    p.add_argument("--reset", action="store_true",
                   help="réinitialiser l'état de démo avant le rejeu")
    p.add_argument("--paralleles", type=int, default=4)
    p.add_argument("--rapport", help="écrire le rapport détaillé dans ce fichier JSON")
    args = p.parse_args()

    with open(FICHIER_EVALS, encoding="utf-8") as f:
        evals = json.load(f)["evals"]
    if args.filtre:
        evals = [e for e in evals if args.filtre in e["id"]]
    if args.sans_ecriture:
        evals = [e for e in evals if not e.get("ecriture")]
    if not evals:
        sys.exit("Aucune éval ne correspond au filtre.")

    if args.reset:
        reinitialiser()

    comptes = {e.get("compte", "marie.dupont") for e in evals}
    jetons = {c: jeton(c) for c in comptes}

    print(f"Rejeu de {len(evals)} évals (parallélisme {args.paralleles}, "
          f"juge {'désactivé' if args.sans_juge else MODELE_JUGE})…\n")

    debut = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.paralleles) as pool:
        resultats = list(pool.map(
            lambda ev: executer_eval(ev, jetons, args.sans_juge), evals))

    reussites, cout_total = 0, 0.0
    groupe_courant = None
    for r in resultats:
        ev = r["eval"]
        if ev.get("groupe") != groupe_courant:
            groupe_courant = ev.get("groupe")
            print(f"{GRIS}── {groupe_courant} ──{FIN}")
        statut = f"{VERT}PASS{FIN}" if r["ok"] else f"{ROUGE}FAIL{FIN}"
        marque = f" {JAUNE}[écriture]{FIN}" if ev.get("ecriture") else ""
        print(f"  {statut}  {ev['id']:34s} {r['duree']:5.1f}s  ${r['cout']:.4f}{marque}")
        for echec in r["echecs"]:
            print(f"        {ROUGE}✗ {echec}{FIN}")
        reussites += r["ok"]
        cout_total += r["cout"]

    print(f"\n{'─' * 60}")
    print(f"  {reussites}/{len(resultats)} évals réussies · "
          f"{time.time() - debut:.0f}s · coût total ${cout_total:.4f}")

    if args.rapport:
        with open(args.rapport, "w", encoding="utf-8") as f:
            json.dump([{**{"id": r["eval"]["id"], "ok": r["ok"]},
                        **{k: r[k] for k in ("echecs", "reponse", "outils",
                                             "duree", "cout") if k in r}}
                       for r in resultats], f, ensure_ascii=False, indent=2)
        print(f"  Rapport détaillé : {args.rapport}")

    sys.exit(0 if reussites == len(resultats) else 1)


if __name__ == "__main__":
    main()
