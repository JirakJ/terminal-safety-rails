# Changelog

## [2026.3.8] - 2026-04-17

### Added
- **Incident Audit Log** — per-event, append-only forensic record of every rule match, persisted as JSON Lines at `<projectBase>/.idea/terminal-safety/audit.log.jsonl` with automatic size-based rotation (10 MiB, last 5 archives retained). Captures UUID event id, epoch + ISO-8601 UTC timestamp, OS user, host name, project name/path, matched rule id & action, user decision (BLOCKED / ALLOWED / CONFIRMED_ALLOWED / CONFIRMED_BLOCKED / RAN_ANYWAY), masked command, and a SHA-256 hash of the original command for duplicate correlation without storing the cleartext. Designed for SOC2 CC7.2, ISO 27001 A.12.4, and PCI DSS 10.x evidence.
- **`CommandMasker`** — conservative secret redactor covering long hex tokens, base64-ish tokens, `password=`, `--password=`, `token=`, `secret=`, `api[_-]?key=`, `Authorization: Bearer …`, MySQL-style `-p <value>`, and credit-card-like 13–19 digit sequences, preserving the key prefix where possible (e.g. `password=***MASKED***`). Total command truncated at 2000 chars.
- **`AuditLogStore`** — thread-safe append-only JSONL store with atomic rotation (`ATOMIC_MOVE`) and bounded archive retention; concurrent appends from up to 16 writers yield a corruption-free log under test.
- **Tools → Show Incident Audit Log** action: filterable `JBTable` viewer (date range, rule id, decision checkboxes, command-contains, limit) with CSV / JSON export, clear log, and "Reveal File".
- **Tools → Export Incident Audit Log** action: headless CSV/JSON exporter for CI pipelines; extension of chosen file selects the format.
- Full JUnit 5 coverage for the masker (every pattern + no false positives + truncation), store (append, rotation, archive cap, concurrent 16 × 500 writers, clear), exporter (CSV RFC 4180 quoting, stable column order, JSON round-trip), and filter (date range, rule ids, decisions, commandContains, userEquals, AND semantics, limit).

## [2026.3.7] - 2026-04-17

### Added
- **Team Rule Linter & Validator** — pre-flight checks for team-shared rule bundles. `RuleLinter` detects invalid regex (via `java.util.regex.Pattern.compile`), empty/missing patterns, duplicate rule ids, conflicting actions on identical patterns, unreachable rules shadowed by earlier broader patterns (heuristic, skips permissive LOW rules to avoid false positives on disjoint patterns), and overly-broad patterns such as `.*`/`.+`/`^.*$`.
- **Tools → Lint Team Rules File** action runs the linter on the current project's `.terminal-safety-rules.yaml` (or a picked file) on a background task and shows results in a `JBTable` with Copy-Markdown, Export-Report, and Open-Source-File controls; status bar summarises error/warning/info counts.
- **`RuleLintCli`** — IDE-free CLI entry point with `--source=`, `--format=markdown|json|plain`, and `--fail-on=error|warning` flags; exits 0/1/2 for CI gating.
- **`LintMarkdownFormatter`** produces GitHub-flavoured Markdown reports with collapsible `<details>` sections when any severity group exceeds 20 issues; pipes and backticks in messages are escaped.

## [2026.3.6] - 2026-04-17

### Added
- **Rule Effectiveness Analytics** — project-level service (`RuleAnalyticsService`) that records every rule match (allow / block / confirm-then-allow / confirm-then-block), persisted across IDE restarts.
- New **Tools → Show Rule Effectiveness Analytics** action opens a sortable `JBTable` of hits, block rate, last-seen time, and last matched command per rule.
- CSV export (with RFC-4180 escaping) and "Reset All" controls in the analytics dialog.
- Sensitive-argument masking for recorded commands: long hex/base64 tokens, `password=`, `token=`, `-p <value>`, and `Authorization: Bearer …` are replaced with `***`.

## [2026.2.4] - 2026-02-13

### Added
- Added ProGuard obfuscation for code protection


## [2026.2.3] - 2026-02-13

### Changed
- Version bump for JetBrains Marketplace publication


## [2026.2.2] - 2026-02-13

### Changed
- Expanded compatibility range: sinceBuild 232.0+ (IntelliJ 2023.2+)
- Removed upper version bound for future IDE compatibility


All notable changes to this project will be documented in this file.

## [2026.2.1] - 2026-02-13

### Added
- Initial release
- Terminal command interception via `TerminalShellCommandHandler` extension point
- 4 risk levels: 🔴 CRITICAL, 🟠 HIGH, 🟡 MEDIUM, 🟢 LOW
- Color-coded confirmation dialog with command details and risk level
- 23 built-in dangerous command rules:
  - Filesystem: `rm -rf /`, `rm -rf ~`, `rm -rf *` (CRITICAL)
  - System: `mkfs`, `dd if=`, `systemctl stop`, `service stop` (CRITICAL/MEDIUM)
  - Database: `DROP TABLE`, `DROP DATABASE`, `TRUNCATE` (CRITICAL)
  - Kubernetes: `kubectl delete`, `kubectl apply --force` (HIGH)
  - Terraform: `terraform apply`, `terraform destroy` (HIGH)
  - Docker: `docker system prune`, `docker rm -f` (MEDIUM)
  - Git: `git push --force`, `git push -f`, `git reset --hard` (MEDIUM)
  - Permissions: `chmod 777` (MEDIUM)
- Custom rules with configurable pattern, risk level, category, and description
- Regex and glob pattern matching support
- 9 command categories: FILESYSTEM, KUBERNETES, TERRAFORM, DOCKER, GIT, DATABASE, SYSTEM, PERMISSIONS, CUSTOM
- Enable/disable toggle via settings
- Configurable settings page (Settings → Tools → Terminal Safety Rails)
- Audit log tracking intercepted commands with timestamps and ALLOWED/BLOCKED status
- Team-shared rules via `.terminal-safety-rules.yaml` project file
- Balloon notifications for blocked commands
- Persistent settings stored in `terminalSafetySettings.xml`
- Part of DevOps Safety Kit bundle
