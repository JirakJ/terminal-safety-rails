# Changelog

## [2026.3.10] - 2026-04-17

### Fixed
- Replaced deprecated `FileSaverDescriptor(String, String, String...)` constructor usages (flagged by the JetBrains Marketplace plugin verifier on IC 2025.1+) with the non-deprecated two-argument constructor plus `withExtensionFilter(...)`. Affected actions: Export Audit Log (`ExportAuditLogAction`, `ShowAuditLogAction`), Export Rule Analytics (`ShowRuleAnalyticsAction`), and Export Lint Report (`LintTeamRulesAction`). Behaviour (save dialogs, filters, filename defaults) is unchanged.

### Changed
- Bumped minimum supported IDE to 2025.1 (`sinceBuild=251`) — required by the non-deprecated `FileSaverDescriptor` API introduced in that release.
- Upgraded IntelliJ Platform compile target to `2025.1`.
- Updated plugin verifier matrix to the latest generally-available aarch64 builds: IC `2025.1.7` and `2025.2.6.1` (IC `2025.3.x` / `2026.1` aarch64 DMGs are not yet published on `download.jetbrains.com` at release time).

## [2026.3.9] - 2026-04-18

### Added
- **Command Risk Scoring & Heuristic Classifier** — new `com.jakubjirak.terminalsafety.risk` package providing a deterministic 0..100 heuristic score for any terminal command, independent of the rule engine. Scores are bucketed into five bands (`SAFE 0-19`, `LOW 20-39`, `MEDIUM 40-59`, `HIGH 60-79`, `CRITICAL 80-100`).
- **11 signal detectors** ship out-of-the-box: destructive file ops (`rm -rf /`, `rm -rf ~/$HOME`, `--no-preserve-root`, `find -delete`), privilege escalation (`sudo`, `su -`, `doas`, `pkexec`), shell injection (backticks, `$(…)`, `eval`), remote code execution (`curl|wget … | bash/sh`), credential exposure (`echo $SECRET`, hard-coded tokens), network exfiltration (`nc`, reverse shells, `/dev/tcp`), production target cues (hosts containing `prod`/`production`), disk wipe (`dd of=/dev/sdX`, `mkfs`, `shred`), fork bombs (`:(){ :|:& };:`), git history rewrites (`push --force`, `filter-branch`, `reset --hard origin/…`), and privileged package-manager use (`sudo npm -g`, `curl | sudo apt`, `sudo pip install`, `sudo gem install`).
- **`CommandRiskScorer`** — pure, dependency-free aggregator; clamps to `0..100`, sorts signals by weight desc, isolates detector exceptions so one bad detector cannot break scoring.
- **`RiskScoringService`** — project-level `@Service` with a 30-second, 256-entry cache to keep scoring off the hot path.
- **Markdown + JSON reporters** (`RiskAssessmentMarkdownReporter`, `RiskAssessmentJsonReporter`) — dependency-free, deterministic output suitable for PR comments and CI artefacts.
- **`CommandRiskCli`** — headless entry point (`--command` / `--file`, `--format=text|md|json`, `--min-level`, `--out`, `--fail-on`); atomic file writes; exits `0`/`1`/`2` for CI gating.
- **Tools → Analyze Command Risk** action — modal dialog that scores a pasted command and displays all signals in a sortable `JBTable`.
- **Tools → Show Recent High-Risk Commands** action — mines the incident audit log for the most recent HIGH/CRITICAL matches and renders them in a table.
- **`RiskAlertDialog`** — integrated into `CommandInterceptor`: when no explicit rule matches but heuristic scoring reports `HIGH`+, the user is shown the signals and asked to confirm before the command runs.
- Comprehensive JUnit 5 suite covering every detector (happy path + false-positive guard), the scorer (clamping, ordering, defensive exception handling), reporters (Markdown/JSON round-trip), CLI (exit codes 0/1/2, `--out` atomic write, `--min-level` filter), and the `RiskLevel` / `RiskSignal` value types.

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
