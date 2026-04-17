# Terminal Safety Rails

[![Build](https://github.com/JirakJ/terminal-safety-rails/actions/workflows/build.yml/badge.svg)](https://github.com/JirakJ/terminal-safety-rails/actions/workflows/build.yml)
[![Version](https://img.shields.io/badge/version-2026.2.1-blue.svg)](https://github.com/JirakJ/terminal-safety-rails/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

> Detects dangerous commands in your IDE terminal and shows a confirmation dialog before execution — protecting against accidental `rm -rf`, `kubectl delete`, `terraform destroy`, and more.

## ✨ Features

- **Command interception** — catches dangerous commands before they execute in the IDE terminal
- **4 risk levels** — 🔴 CRITICAL, 🟠 HIGH, 🟡 MEDIUM, 🟢 LOW
- **Color-coded confirmation dialog** — warning with command details and risk level
- **23 built-in rules** — dangerous command patterns out of the box covering filesystem, Kubernetes, Terraform, Docker, Git, SQL, system services, and permissions
- **Unlimited custom rules** — add your own patterns via settings
- **Enable/disable toggle** — quickly turn protection on and off
- **Context-aware detection** — smarter matching based on environment
- **Audit log** — track all intercepted commands with timestamps and actions (ALLOWED/BLOCKED)
- **Team-shared rules** — share rules via `.terminal-safety-rules.yaml` in your project
- **Statistics** — command interception analytics
- **Rule Effectiveness Analytics** — per-rule hit/block/allow counters with CSV export, persisted per project

## 📊 Rule Effectiveness Analytics

Every time a safety rule matches a command, Terminal Safety Rails records the outcome (allowed, blocked, confirmed-then-allowed, confirmed-then-blocked) in a project-level, persistent store. Open **Tools → Show Rule Effectiveness Analytics** to:

- See which rules actually fire (and which are dead weight you can prune)
- Measure noise — rules with a high confirm-then-allow rate are probably too broad
- Audit your safety posture with the **Export CSV…** button (RFC-4180 escaped)
- Reset counters per-rule or globally

The recorded *last matched command* is automatically sanitised — long hex/base64 tokens, `password=`, `token=`, `-p <value>`, and `Authorization: Bearer …` are replaced with `***` before being stored.

## 📦 Installation

### From JetBrains Marketplace
1. Open **Settings → Plugins → Marketplace**
2. Search for **"Terminal Safety Rails"**
3. Click **Install**

### From ZIP
1. Download the latest release from [Releases](https://github.com/JirakJ/terminal-safety-rails/releases)
2. Open **Settings → Plugins → ⚙️ → Install Plugin from Disk**
3. Select the downloaded ZIP file

## 🚀 Quick Start

1. Install the plugin and restart your IDE
2. Open the built-in IDE terminal
3. Type any dangerous command (e.g., `rm -rf /tmp/*`)
4. A color-coded confirmation dialog appears showing the risk level and matched rule
5. Choose **"Execute Anyway"** to proceed or **"Cancel"** to block the command

## ⚙️ Configuration

**Settings → Tools → Terminal Safety Rails**

| Setting | Default | Description |
|---------|---------|-------------|
| Enable plugin | `true` | Enable or disable command interception |
| Custom rules | *(empty)* | Table of custom command rules (pattern, risk level, category, description, regex toggle) |

### Custom Rule Fields

| Field | Description |
|-------|-------------|
| Pattern | Command pattern to match (glob or regex) |
| Risk Level | `CRITICAL`, `HIGH`, `MEDIUM`, or `LOW` |
| Category | `FILESYSTEM`, `KUBERNETES`, `TERRAFORM`, `DOCKER`, `GIT`, `DATABASE`, `SYSTEM`, `PERMISSIONS`, or `CUSTOM` |
| Description | Human-readable description shown in the confirmation dialog |
| Is Regex | Whether the pattern is a regex (`true`) or glob (`false`) |

## 🎯 Usage

### How Interception Works
1. You type a command in the IDE terminal
2. The `CommandInterceptor` checks the command against all effective rules (custom rules first, then built-in)
3. If a match is found, a modal `DangerousCommandDialog` is displayed
4. The dialog shows the matched command, risk level (color-coded), and rule description
5. You confirm or cancel execution

### Built-in Rules

#### 🔴 CRITICAL
| Pattern | Category | Description |
|---------|----------|-------------|
| `rm -rf /` | Filesystem | Recursive force delete from root |
| `rm -rf ~` | Filesystem | Recursive force delete of home directory |
| `rm -rf *` | Filesystem | Recursive force delete with wildcard |
| `mkfs` | System | Filesystem format command |
| `dd if=` | System | Raw disk write |
| `DROP TABLE` | Database | SQL DROP TABLE |
| `DROP DATABASE` | Database | SQL DROP DATABASE |
| `TRUNCATE` | Database | SQL TRUNCATE |

#### 🟠 HIGH
| Pattern | Category | Description |
|---------|----------|-------------|
| `kubectl delete` | Kubernetes | Kubernetes resource deletion |
| `kubectl apply --force` | Kubernetes | Kubernetes force apply |
| `terraform apply` | Terraform | May modify infrastructure |
| `terraform destroy` | Terraform | Removes infrastructure |

#### 🟡 MEDIUM
| Pattern | Category | Description |
|---------|----------|-------------|
| `docker system prune` | Docker | Removes all unused data |
| `docker rm -f $(docker ps` | Docker | Force remove all containers |
| `git push --force` / `git push -f` | Git | Rewrites remote history |
| `git reset --hard` | Git | Discards all changes |
| `chmod 777` | Permissions | World-writable permissions |
| `systemctl stop` | System | Stopping system service |
| `service ... stop` | System | Stopping system service |

### Team-Shared Rules
Place a `.terminal-safety-rules.yaml` file in your project root to define shared safety rules for your team. Team rules are loaded automatically and merged with built-in and custom rules.

### Risk Level Colors
| Level | Color | Hex |
|-------|-------|-----|
| 🔴 CRITICAL | Red | `#E53935` |
| 🟠 HIGH | Orange | `#FB8C00` |
| 🟡 MEDIUM | Yellow | `#FDD835` |
| 🟢 LOW | Green | `#66BB6A` |

## 📋 Requirements

- IntelliJ IDEA 2024.3+ (or any JetBrains IDE based on IntelliJ Platform)
- Java 17+
- Terminal plugin (bundled with JetBrains IDEs)

## 🏗️ Building from Source

```bash
git clone https://github.com/JirakJ/terminal-safety-rails.git
cd terminal-safety-rails
./gradlew build
```

## 🤝 Part of DevOps Safety Kit

Terminal Safety Rails pairs with [Kubernetes Context Guard](https://github.com/JirakJ/kubernetes-context-guard) and [Local Secrets Tripwire](https://github.com/JirakJ/local-secrets-tripwire) for comprehensive protection against accidental production changes and secret leaks.

## 📝 License

MIT License — see [LICENSE](LICENSE) for details.

## 👤 Author

**Jakub Jirák**
- GitHub: [@JirakJ](https://github.com/JirakJ)
