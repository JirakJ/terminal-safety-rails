# Changelog

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
