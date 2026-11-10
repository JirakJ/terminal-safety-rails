# Terminal Safety Rails — JetBrains Marketplace Listing

---

## 📝 Plugin Information

| Field | Value |
|-------|-------|
| **Name** | Terminal Safety Rails |
| **Plugin ID** | `com.jakubjirak.terminalsafety` |
| **Version** | 2026.2.8 |
| **Category** | DevOps |
| **Tags** | `terminal`, `safety`, `devops`, `command-protection`, `production-safety`, `kubernetes`, `terraform`, `docker`, `dangerous-commands` |

---

## 🎯 Short Description

> Intercept dangerous terminal commands before execution — block rm -rf, kubectl delete, terraform destroy, and more with color-coded risk levels and confirmation dialogs.

---

## 📖 Full Description

### 🛡️ The Safety Net Every Developer Needs in Their Terminal

**The Problem:** One wrong command in the IDE terminal can delete production data, destroy infrastructure, or wipe a filesystem. `rm -rf /`, `kubectl delete namespace production`, `terraform destroy` — these mistakes happen to everyone, and there's no undo button.

**The Solution:** Terminal Safety Rails intercepts dangerous commands before they reach the shell. It matches commands against 23 built-in patterns covering filesystem, Kubernetes, Terraform, Docker, Git, SQL, system services, and permissions — then shows a color-coded confirmation dialog with risk level details so you can proceed deliberately or cancel safely.

---

### ✨ Key Features

#### 🚨 Pre-Execution Interception
Catches dangerous commands before they execute in the IDE terminal. Commands are matched against rules before reaching the shell — not after.

#### 🎨 4 Risk Levels with Color-Coded Dialogs
- 🔴 **CRITICAL** (`#E53935`) — `rm -rf /`, `DROP DATABASE`, `mkfs`, `dd if=`
- 🟠 **HIGH** (`#FB8C00`) — `kubectl delete`, `terraform destroy`, `terraform apply`
- 🟡 **MEDIUM** (`#FDD835`) — `docker system prune`, `git push --force`, `git reset --hard`, `chmod 777`
- 🟢 **LOW** (`#66BB6A`) — less destructive but still noteworthy commands

#### 🛡️ 23 Built-in Dangerous Command Patterns
Out-of-the-box protection covering:
- **Filesystem** — `rm -rf /`, `rm -rf ~`, `rm -rf *`
- **Database** — `DROP TABLE`, `DROP DATABASE`, `TRUNCATE`
- **System** — `mkfs`, `dd if=`, `systemctl stop`, `service stop`
- **Kubernetes** — `kubectl delete`, `kubectl apply --force`
- **Terraform** — `terraform apply`, `terraform destroy`
- **Docker** — `docker system prune`, `docker rm -f`
- **Git** — `git push --force`, `git push -f`, `git reset --hard`
- **Permissions** — `chmod 777`

#### ✏️ Custom Rules
Add your own dangerous command patterns with configurable risk level, category, description, and regex/glob matching.

#### 🔍 Context-Aware Detection
Smarter matching based on environment — automatic risk elevation when kubectl context is production, terraform workspace is prod, or git branch is main/master.

#### 📜 Audit Log
Track all intercepted commands with timestamps and actions (ALLOWED / BLOCKED / CANCELLED). Full history of every dangerous command encountered.

#### 📊 Statistics Dashboard
Visualize interception analytics and identify the most frequently triggered rules.

#### 👥 Team-Shared Rules
Place a `.terminal-safety-rules.yaml` file in your project root to share safety rules across the team. Rules are loaded automatically and merged with built-in and custom rules.

---

### 💚 Free & open source

All features are free for everyone — no license needed. Source code under Apache-2.0: https://github.com/JirakJ/terminal-safety-rails

---

### 🎯 Perfect For

- **DevOps Engineers** working with production Kubernetes clusters and Terraform infrastructure
- **Backend Developers** who run database commands and system operations from the terminal
- **SREs** who need guardrails against accidental production changes
- **Teams** who want organization-wide command safety policies
- **Anyone** who has ever typed a destructive command in the wrong terminal tab

---

### ⚙️ Requirements

| Requirement | Value |
|-------------|-------|
| **IDE Version** | IntelliJ IDEA 2024.3+ (build 232+) |
| **Java** | 17+ |
| **Dependencies** | Terminal plugin (bundled with JetBrains IDEs) |

---

### 🚀 Quick Start

1. **Install** — Settings → Plugins → Marketplace → Search "Terminal Safety Rails"
2. **Use the terminal** — Open the built-in IDE terminal and type commands as usual
3. **See it in action** — Type `rm -rf /tmp/*` and observe the color-coded confirmation dialog
4. **Decide** — Click **"Execute Anyway"** to proceed or **"Cancel"** to block
5. **Customize** — Add custom rules in Settings → Tools → Terminal Safety Rails

---

## 📸 Screenshots & Media

| # | Screenshot | Description |
|---|-----------|-------------|
| 1 | `critical-confirmation-dialog.png` | Color-coded CRITICAL-risk confirmation dialog for `rm -rf /` with risk level, command details, and Execute/Cancel buttons |
| 2 | `high-risk-kubectl.png` | HIGH-risk dialog for `kubectl delete namespace production` with orange warning |
| 3 | `medium-risk-git-force.png` | MEDIUM-risk dialog for `git push --force` with yellow warning |
| 4 | `settings-built-in-rules.png` | Settings panel displaying all 23 built-in dangerous command patterns organized by risk level |
| 5 | `custom-rules-editor.png` | Custom rules editor with pattern, risk level, category, description, and regex toggle fields |
| 6 | `context-aware-detection.png` | Context-aware detection showing elevated risk when kubectl context is production |
| 7 | `audit-log.png` | Audit log showing interception history with timestamps, commands, and ALLOWED/BLOCKED status |
| 8 | `team-rules-yaml.png` | Example `.terminal-safety-rules.yaml` file loaded in the IDE for team-shared rules |

---

## 🏷️ Marketplace Categories

| Type | Category |
|------|----------|
| **Primary** | DevOps |
| **Secondary** | Terminal, Developer Productivity |

---

## 🔍 Keywords (SEO)

`terminal safety`, `dangerous commands`, `command protection`, `rm -rf`, `kubectl delete`, `terraform destroy`, `docker prune`, `git force push`, `production safety`, `command interception`, `devops safety`, `terminal guard`, `destructive commands`, `confirmation dialog`, `risk level`, `audit log`, `command blocker`, `safety rails`, `sre`, `incident prevention`

---

## 📋 Compatibility

| IDE | Supported |
|-----|-----------|
| IntelliJ IDEA Community | ✅ |
| IntelliJ IDEA Ultimate | ✅ |
| WebStorm | ✅ |
| PhpStorm | ✅ |
| PyCharm | ✅ |
| GoLand | ✅ |
| RubyMine | ✅ |
| CLion | ✅ |
| Rider | ✅ |
| Android Studio | ✅ |

| Requirement | Value |
|-------------|-------|
| **Minimum IDE Build** | 232 (2023.2) |
| **Java Version** | 17+ |
| **OS Support** | macOS, Linux, Windows |

---

## 📞 Vendor Information

| Field | Value |
|-------|-------|
| **Vendor** | Jakub Jirak |
| **Email** | jirak@jakubjirak.com |
| **Website** | https://jakubjirak.com |
| **Support** | support@jakubjirak.com |
| **Source Code** | https://github.com/JirakJ/terminal-safety-rails |

---

## ✅ Pre-Launch Checklist

- [ ] Plugin icon (40×40 and 80×80 SVG/PNG) included in repository
- [ ] Plugin description under 255 characters for short description field
- [ ] Full description formatted in HTML for Marketplace submission
- [ ] Screenshots captured at 1280×800 minimum resolution (especially confirmation dialogs)
- [ ] Plugin JAR/ZIP tested on IntelliJ IDEA 2024.3 (build 232)
- [ ] Plugin JAR/ZIP tested on latest IntelliJ IDEA version
- [ ] All 23 built-in rules verified and working
- [ ] Change notes prepared for current version
- [ ] Vendor information verified and support email tested
- [ ] Plugin ID (`com.jakubjirak.terminalsafety`) registered on Marketplace
- [ ] Part of DevOps Safety Kit cross-promotion configured
