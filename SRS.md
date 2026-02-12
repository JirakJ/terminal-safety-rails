# Terminal Safety Rails — Software Requirements Specification (SRS)

**Verze:** 2026.2.1
**Autor:** Jakub Jirák
**Datum:** 12. února 2026
**Model:** Freemium JetBrains Plugin

---

## 1. Úvod

### 1.1 Účel
Terminal Safety Rails je JetBrains IDE plugin, který detekuje potenciálně nebezpečné příkazy v integrovaném terminálu a zobrazí varování/potvrzovací dialog před jejich spuštěním.

### 1.2 Rozsah
Plugin interceptuje příkazy zadávané do JetBrains Terminal, vyhodnocuje je oproti konfigurovatelné sadě pravidel a při detekci rizikového příkazu zobrazí modální potvrzovací dialog.

### 1.3 Reference
- JetBrains Platform SDK: Terminal API, `shellCommandHandler` Extension Point
- Bundle partner: Kubernetes Context Guard (DevOps Safety Kit)

---

## 2. Celkový popis

### 2.1 Perspektiva produktu
Žádný existující plugin neřeší safety guard pro IDE terminál. Všechny terminálové pluginy (Terminal Pro, Terminal Snippets, QuickTerminal) se zaměřují na UX/produktivitu.

### 2.2 Uživatelské skupiny
- **DevOps inženýři** — pracují s kubectl, terraform, helm v IDE terminálu
- **Backend vývojáři** — spouští destruktivní DB/filesystem příkazy
- **Team leads** — chtějí enforceovat bezpečnostní pravidla

### 2.3 Omezení
- JetBrains Terminal API (`shellCommandHandler`) má omezené možnosti interceptování
- Plugin nemůže blokovat příkazy spuštěné mimo IDE terminál

---

## 3. Funkční požadavky

### FR-1: Detekce nebezpečných příkazů
- **FR-1.1:** Plugin rozpozná předdefinovanou sadu nebezpečných příkazů:
  - `rm -rf /`, `rm -rf *`, `rm -rf ~`
  - `kubectl delete`, `kubectl apply` (na prod kontextu)
  - `terraform destroy`, `terraform apply` (bez plan)
  - `docker system prune -a`
  - `git push --force` na chráněné větve
  - `DROP DATABASE`, `DROP TABLE`, `TRUNCATE`
- **FR-1.2:** Detekce funguje na bázi regex pattern matching
- **FR-1.3:** Uživatel může přidávat vlastní pravidla (custom patterns)

### FR-2: Potvrzovací dialog
- **FR-2.1:** Při detekci rizikového příkazu se zobrazí modální dialog
- **FR-2.2:** Dialog obsahuje: příkaz, důvod varování, severity level, tlačítka "Spustit přesto" / "Zrušit"
- **FR-2.3:** Severity levels: 🔴 CRITICAL, 🟠 WARNING, 🟡 CAUTION
- **FR-2.4:** CRITICAL příkazy vyžadují manuální přepsání potvrzovacího textu (např. "yes, delete production")

### FR-3: Konfigurace pravidel
- **FR-3.1:** Settings page v IDE (Settings → Tools → Terminal Safety Rails)
- **FR-3.2:** Pravidla definovatelná jako:
  - Pattern (regex)
  - Severity (CRITICAL / WARNING / CAUTION)
  - Popis rizika
  - Kontext (např. "jen pokud kubectl context obsahuje 'prod'")
- **FR-3.3:** Import/export pravidel (JSON/YAML)
- **FR-3.4:** Team-shared pravidla přes VCS (.terminal-safety-rules.yaml v repo root)

### FR-4: Context-aware detekce (Freemium — Paid)
- **FR-4.1:** Detekce aktuálního kubectl kontextu
- **FR-4.2:** Detekce terraform workspace
- **FR-4.3:** Detekce git branch (produkční vs. dev)
- **FR-4.4:** Dynamické severity na základě kontextu

### FR-5: Audit log (Paid)
- **FR-5.1:** Logování všech interceptovaných příkazů s timestamp, rozhodnutím (allow/deny), uživatelem
- **FR-5.2:** Export logu (CSV/JSON)
- **FR-5.3:** Notifikace pro team lead při CRITICAL override

### FR-6: Statistiky (Paid)
- **FR-6.1:** Dashboard: kolikrát plugin zabránil potenciálnímu problému
- **FR-6.2:** Trend graf: intercepted commands over time

---

## 4. Nefunkční požadavky

### NFR-1: Výkon
- Vyhodnocení příkazu < 50ms (nesmí zpomalit terminál)
- Regex matching probíhá synchronně před odesláním příkazu do shellu

### NFR-2: Kompatibilita
- IntelliJ IDEA 2024.3+
- Všechny JetBrains IDE (Ultimate, Community, WebStorm, PyCharm, GoLand, etc.)

### NFR-3: Bezpečnost
- Pravidla se ukládají lokálně (Settings) nebo v repozitáři
- Audit log neobsahuje citlivé hodnoty z příkazů (env vars maskované)

### NFR-4: UX
- Zero-config out-of-the-box s rozumnými defaults
- Dialog nesmí blokovat jiné IDE operace

---

## 5. Datový model

### 5.1 SafetyRule
```
SafetyRule {
  id: String (UUID)
  name: String
  pattern: String (regex)
  severity: Enum (CRITICAL, WARNING, CAUTION)
  description: String
  contextCondition: String? (optional, např. "kubectl.context.contains('prod')")
  enabled: Boolean
  source: Enum (BUILTIN, USER, TEAM)
}
```

### 5.2 InterceptionEvent
```
InterceptionEvent {
  timestamp: Instant
  command: String (masked)
  matchedRule: SafetyRule
  decision: Enum (ALLOWED, BLOCKED, CANCELLED)
  user: String
}
```

---

## 6. Rozhraní

### 6.1 Extension Points
- `com.jakubjirak.terminalsafety.ruleProvider` — EP pro přidání pravidel z jiných pluginů
- `com.jakubjirak.terminalsafety.contextProvider` — EP pro custom kontext detekci

### 6.2 UI komponenty
- Settings page (Configurable)
- Confirmation dialog (DialogWrapper)
- Tool window pro audit log (ToolWindowFactory)
- Status bar widget (StatusBarWidgetFactory) — zobrazuje aktuální safety stav

---

## 7. Freemium model

### Free tier
- 15 built-in pravidel (rm, kubectl, terraform, docker, git force push, SQL drop)
- Potvrzovací dialog s 3 severity levels
- Custom pravidla (max 5)
- Repo-level sdílená pravidla (.terminal-safety-rules.yaml)

### Paid tier
- Neomezená custom pravidla
- Context-aware detekce (kubectl context, terraform workspace, git branch)
- Audit log s exportem
- Statistiky dashboard
- Team notifikace (webhook)
- Priority podpora
