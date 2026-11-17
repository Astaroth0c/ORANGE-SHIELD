#!/usr/bin/env python3
"""
ORANGE CHAIN — validador comunitário (CI).

Infraestrutura comunitária real, sem servidor dedicado:
  1. Lê a cadeia em community/chain.json (tip = último bloco).
  2. Busca issues abertas com label "orange-chain".
  3. Para cada issue: extrai o bloco proposto, valida de verdade:
       - chainId correto
       - index == tip + 1 e prevHash == hash do tip (encadeamento)
       - merkleRoot confere com as entradas
       - cada entrada: reporterId == sha256(pubKey)[:16] e assinatura
         ECDSA P-256 (SHA256withECDSA, DER) válida sobre a string canônica
       - dedupe: entrada idêntica (e164 + reporterId) já presente é ignorada
  4. Bloco válido -> anexado à cadeia; reputation-feed.json é re-agregado
     a partir da cadeia (a cadeia é a fonte da verdade).
  5. Issue é fechada com comentário de aceite/rejeição (trilha de auditoria).

Toda a matemática é verificável: qualquer pessoa pode auditar a cadeia
recomputando os hashes e verificando as assinaturas.
"""

import base64
import hashlib
import json
import os
import sys
import urllib.request
import urllib.error

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

REPO = os.environ.get("GITHUB_REPOSITORY", "Astaroth0c/ORANGE-SHIELD")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
API = f"https://api.github.com/repos/{REPO}"
LABEL = "orange-chain"
CHAIN_ID = "orange-chain-cambira-pr"
ACCEPTED = {"blocked", "spam", "fraud_confirmed", "nuisance"}


def sha256_hex(s: str) -> str:
    return hashlib.sha256(s.encode()).hexdigest()


def merkle_root(items):
    if not items:
        return sha256_hex("ORANGE-CHAIN-GENESIS")
    level = [sha256_hex(i) for i in items]
    while len(level) > 1:
        nxt = []
        for i in range(0, len(level), 2):
            left = level[i]
            right = level[i + 1] if i + 1 < len(level) else level[i]
            nxt.append(sha256_hex(left + right))
        level = nxt
    return level[0]


def block_hash(block) -> str:
    canonical = f"v1|{block['index']}|{block['timestamp']}|{block['prevHash']}|{block['merkleRoot']}|{block['publisher']}"
    return sha256_hex(canonical)


def entry_canonical(entry) -> str:
    return f"v1|{entry['e164']}|{entry['classification']}|{entry['reportedAt']}|{entry['reporterId']}"


def verify_entry(entry) -> str | None:
    """Retorna None se válida ou o motivo da rejeição."""
    e164 = entry.get("e164", "")
    if not e164.startswith("+") or len(e164) < 9:
        return f"e164 inválido: {e164!r}"
    if entry.get("classification") not in ACCEPTED:
        return f"classification inválida: {entry.get('classification')!r}"
    pub_b64 = entry.get("pubKey", "")
    sig_b64 = entry.get("signature", "")
    try:
        pub = serialization.load_der_public_key(base64.b64decode(pub_b64))
        if not isinstance(pub, ec.EllipticCurvePublicKey) or not isinstance(pub.curve, ec.SECP256R1):
            return "chave pública não é EC P-256"
    except Exception as exc:
        return f"pubKey ilegível: {exc}"
    if sha256_hex(pub_b64)[:16] != entry.get("reporterId", ""):
        return "reporterId divergente da chave pública"
    try:
        pub.verify(base64.b64decode(sig_b64), entry_canonical(entry).encode(), ec.ECDSA(hashes.SHA256()))
    except Exception:
        return f"assinatura ECDSA inválida para {e164}"
    return None


def api(method: str, path: str, body=None):
    req = urllib.request.Request(
        API + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "orange-chain-validator",
            "Accept": "application/vnd.github+json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read() or b"{}")


def extract_payload(body: str):
    """Extrai o JSON do payload entre cercas ```json ... ```."""
    marker = "```json"
    start = body.find(marker)
    if start < 0:
        return None
    start += len(marker)
    end = body.find("```", start)
    if end < 0:
        return None
    try:
        return json.loads(body[start:end].strip())
    except json.JSONDecodeError:
        return None


def aggregate_feed(chain):
    """Re-agrega reputation-feed.json a partir da cadeia (fonte da verdade)."""
    reports = {}
    for block in chain["blocks"]:
        for entry in block.get("entries", []):
            key = entry["e164"]
            cur = reports.setdefault(key, {"e164": key, "classification": entry["classification"], "reportCount": 0, "confidence": 0.0, "reporters": set()})
            cur["reportCount"] += 1
            cur["reporters"].add(entry["reporterId"])
            rank = {"fraud_confirmed": 3, "blocked": 2, "spam": 1, "nuisance": 1}
            if rank.get(entry["classification"], 0) > rank.get(cur["classification"], 0):
                cur["classification"] = entry["classification"]
            cur["confidence"] = max(cur["confidence"], min(0.5 + 0.1 * len(cur["reporters"]), 0.95))
    entries = []
    for item in reports.values():
        item.pop("reporters")
        entries.append(item)
    entries.sort(key=lambda x: (-x["reportCount"], x["e164"]))
    return {"version": 1, "chainId": CHAIN_ID, "updatedAt": __import__("datetime").datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"), "entries": entries}


def main() -> int:
    with open("community/chain.json", encoding="utf-8") as fh:
        chain = json.load(fh)
    if chain.get("chainId") != CHAIN_ID:
        print(f"::error::chainId inesperado: {chain.get('chainId')}")
        return 1

    # Revalida a cadeia existente do zero (nós também fazem isso).
    prev = "0" * 64
    for i, block in enumerate(chain["blocks"]):
        if block["index"] != i or block["prevHash"] != prev:
            print(f"::error::cadeia quebrada no bloco {i}")
            return 1
        if block["merkleRoot"] != merkle_root([entry_canonical(e) for e in block.get("entries", [])]):
            print(f"::error::merkle inválido no bloco {i}")
            return 1
        for entry in block.get("entries", []):
            reason = verify_entry(entry)
            if reason:
                print(f"::error::entrada inválida no bloco {i}: {reason}")
                return 1
        prev = block_hash(block)
    tip = chain["blocks"][-1]
    print(f"Cadeia válida: {len(chain['blocks'])} blocos, tip={tip['index']} hash={prev[:12]}…")

    # Issues abertas com a label.
    status, issues = api("GET", f"/issues?labels={LABEL}&state=open&per_page=50")
    if status != 200:
        print(f"::error::falha ao listar issues: {status}")
        return 1

    known = {(e["e164"], entry_canonical(e)) for b in chain["blocks"] for e in b.get("entries", [])}
    known_keys = {(b["entries"] and [(e["e164"], e["reporterId"]) for e in b["entries"]] or []) for b in chain["blocks"]}
    seen_pairs = set()
    for b in chain["blocks"]:
        for e in b.get("entries", []):
            seen_pairs.add((e["e164"], e["reporterId"]))

    appended = 0
    for issue in issues:
        number = issue["number"]
        payload = extract_payload(issue.get("body") or "")
        if not payload or payload.get("chainId") != CHAIN_ID or "block" not in payload:
            api("POST", f"/issues/{number}/comments", {"body": "❌ Payload ausente, ilegível ou chainId divergente. Bloco não aceito."})
            api("PATCH", f"/issues/{number}", {"state": "closed"})
            continue
        block = payload["block"]
        reasons = []
        if block.get("index") != tip["index"] + 1:
            reasons.append(f"index {block.get('index')} != {tip['index'] + 1} (bloco concorrente; reenvie atualizado)")
        if block.get("prevHash") != block_hash(tip):
            reasons.append("prevHash diverge do tip atual")
        entries = block.get("entries", [])
        if not entries:
            reasons.append("bloco sem entradas")
        fresh = []
        for entry in entries:
            if (entry.get("e164"), entry.get("reporterId")) in seen_pairs:
                continue  # dedupe idempotente
            reason = verify_entry(entry)
            if reason:
                reasons.append(reason)
            else:
                fresh.append(entry)
        if reasons:
            api("POST", f"/issues/{number}/comments", {"body": "❌ Bloco rejeitado pelo consenso:\n- " + "\n- ".join(reasons)})
            api("PATCH", f"/issues/{number}", {"state": "closed"})
            continue
        if not fresh:
            api("POST", f"/issues/{number}/comments", {"body": "ℹ️ Todas as entradas já constam na cadeia (dedupe). Nada a acrescentar."})
            api("PATCH", f"/issues/{number}", {"state": "closed"})
            continue
        new_block = {
            "index": tip["index"] + 1,
            "timestamp": int(__import__("datetime").datetime.utcnow().timestamp() * 1000),
            "prevHash": block_hash(tip),
            "merkleRoot": merkle_root([entry_canonical(e) for e in fresh]),
            "publisher": "ORANGE-CI",
            "entries": fresh,
        }
        chain["blocks"].append(new_block)
        tip = new_block
        appended += 1
        for e in fresh:
            seen_pairs.add((e["e164"], e["reporterId"]))
        api("POST", f"/issues/{number}/comments", {"body": f"✅ Bloco {new_block['index']} aceito: {len(fresh)} entrada(s) validada(s) (ECDSA + encadeamento + Merkle). Hash do bloco: `{block_hash(new_block)}`."})
        api("PATCH", f"/issues/{number}", {"state": "closed"})

    if appended:
        chain["updatedAt"] = __import__("datetime").datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
        with open("community/chain.json", "w", encoding="utf-8") as fh:
            json.dump(chain, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        feed = aggregate_feed(chain)
        with open("community/reputation-feed.json", "w", encoding="utf-8") as fh:
            json.dump(feed, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        print(f"{appended} bloco(s) anexado(s). Novo tip: {tip['index']} hash={block_hash(tip)[:12]}…")
    else:
        print("Nenhum bloco novo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
