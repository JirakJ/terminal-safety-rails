# Changelog

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
- Freemium licensing gate (free: 5 custom rules max; pro: unlimited)
- Balloon notifications for blocked commands
- Persistent settings stored in `terminalSafetySettings.xml`
- Part of DevOps Safety Kit bundle
