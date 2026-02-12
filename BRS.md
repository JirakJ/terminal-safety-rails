# Terminal Safety Rails — Business Requirements Specification (BRS)

**Verze:** 2026.2.1
**Autor:** Jakub Jirák
**Datum:** 12. února 2026
**Model:** Freemium JetBrains Plugin

---

## 1. Executive Summary

Terminal Safety Rails chrání vývojáře před spuštěním destruktivních příkazů v IDE terminálu. Plugin detekuje nebezpečné příkazy (rm -rf, kubectl delete na produkci, terraform destroy) a vyžaduje explicitní potvrzení. Jedná se o blue ocean produkt — žádný konkurent na JetBrains Marketplace neexistuje.

---

## 2. Obchodní cíle

### 2.1 Primární cíle
1. **Virální adopce** — „plugin, který mi zachránil produkci" = organický word-of-mouth
2. **Brand awareness** — vstupní bod do ekosystému pluginů (DevOps Safety Kit)
3. **Freemium konverze** — 5–8 % free → paid (cíl: 3–5 % v prvním roce)

### 2.2 KPI
| Metrika | Cíl (6 měsíců) | Cíl (12 měsíců) |
|---------|:---:|:---:|
| Celkové instalace | 5 000 | 20 000 |
| Aktivní uživatelé (MAU) | 2 000 | 8 000 |
| Paid konverze | 3 % | 5 % |
| Marketplace rating | ≥ 4.0 | ≥ 4.2 |
| Retention (30d) | 40 % | 50 % |

---

## 3. Cílový trh

### 3.1 Primární segmenty
- **DevOps/SRE inženýři** (50 %) — kubectl, terraform, helm uživatelé
- **Backend vývojáři** (30 %) — DB operace, filesystem, git force push
- **Team leads / Security** (20 %) — policy enforcement, audit

### 3.2 Velikost trhu
- JetBrains IDE uživatelé: ~15M (2025 data)
- Terminál v IDE používá: ~60 % = ~9M
- Cílovka (devops + backend s prod přístupem): ~2M
- Adresovatelný trh: ~500k (awareness + need)

---

## 4. Konkurenční prostředí

### 4.1 Přímá konkurence
**Neexistuje.** Žádný plugin na JetBrains Marketplace neřeší safety guard pro terminál.

### 4.2 Nepřímá konkurence
| Řešení | Omezení |
|--------|---------|
| Shell aliasy / .bashrc guarding | Per-user, nesynchronizované, bez UI |
| Terraform Sentinel / OPA | Server-side, ne IDE-level |
| kubectl admission controllers | Cluster-level, ne client-side |

### 4.3 Diferenciace
- **IDE-native UX** — dialog, ne CLI prompt
- **Team-shared pravidla** — VCS-based config
- **Context-aware** — ví o kubectl kontextu, terraform workspace
- **Bundle s K8s Context Guard** — DevOps Safety Kit brand

---

## 5. Revenue model

### 5.1 Cenová strategie
| Tier | Cena | Obsah |
|------|------|-------|
| **Free** | $0 | 15 built-in pravidel, dialog, max 5 custom pravidel |
| **Pro** | $3.90/měsíc nebo $39/rok | Neomezená pravidla, context-aware, audit log |
| **Team** | $7.90/user/měsíc | Pro + team notifikace, centrální policy, admin dashboard |

### 5.2 Projekce příjmů
| Období | Instalace | Paid users (5%) | MRR |
|--------|:---------:|:---------------:|:---:|
| M6 | 5 000 | 250 | $975 |
| M12 | 20 000 | 1 000 | $3 900 |
| M18 | 40 000 | 2 000 | $7 800 |

---

## 6. Go-to-Market strategie

### 6.1 Launch
1. **Soft launch** — publikace na Marketplace, Reddit (r/devops, r/kubernetes, r/IntelliJIDEA)
2. **Content marketing** — blog post: „How a JetBrains Plugin Saved My Production Database"
3. **Bundle marketing** — společný launch s K8s Context Guard jako „DevOps Safety Kit"

### 6.2 Growth
- **Virální loop** — „Terminal Safety Rails blocked a dangerous command" notifikace → share
- **Conference talks** — DevOps/SRE meetupy (české + mezinárodní)
- **Integration partners** — Terraform, kubectl, Helm dokumentace/community

### 6.3 Retention
- Gamifikace: „X nebezpečných příkazů zachyceno tento měsíc"
- Pravidelné aktualizace pravidel (nové patterns)

---

## 7. Rizika a mitigace

| Riziko | Pravděpodobnost | Dopad | Mitigace |
|--------|:---:|:---:|---------|
| JetBrains Terminal API omezení | Vysoká | Vysoký | Prozkoumat alternativní hooking (shell wrapper) |
| JetBrains absorbuje funkci | Střední | Vysoký | Rychlá iterace, enterprise features jako diferenciátor |
| Nízká konverze free→paid | Střední | Střední | A/B test paywall pozice, value messaging |
| False positives otravují uživatele | Střední | Vysoký | Smart defaults, easy dismiss, learning mode |

---

## 8. Milníky

| Milestone | Popis |
|-----------|-------|
| **M1: MVP** | 15 built-in pravidel, dialog, settings page, publikace na Marketplace |
| **M2: Context-aware** | kubectl/terraform/git context detection |
| **M3: Team features** | Shared rules, audit log, export |
| **M4: Bundle** | DevOps Safety Kit (+ K8s Context Guard) |
| **M5: Enterprise** | Team tier, admin dashboard, webhook notifikace |

---

## 9. Success Criteria

Plugin je úspěšný pokud:
1. Dosáhne 10 000+ instalací do 9 měsíců
2. Marketplace rating ≥ 4.0
3. Alespoň 3 % konverze na paid tier
4. Vytvoří brand awareness pro „DevOps Safety Kit" ekosystém
5. Alespoň 1 virální moment (blog/tweet s 1000+ interakcemi)
