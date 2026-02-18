# Terminal Safety Rails — Stop Dangerous Commands Before They Execute

> **A single destructive command can ruin your day. This plugin makes sure it does not.**

---

## The Problem

A mistyped `rm -rf /`, an accidental `kubectl delete namespace production`, or an unintended `terraform destroy` can cause hours or days of recovery — sometimes with permanent data loss. These mistakes happen to experienced developers too, especially during late-night incidents or high-pressure debugging sessions. There is no undo button in the terminal.

---

## Key Features

### 1. Command Interception
Catches dangerous commands before they execute in the IDE terminal. The command is paused and a confirmation dialog is shown — nothing runs until you explicitly approve.

### 2. 4 Risk Levels
Every intercepted command is classified as CRITICAL (red), HIGH (orange), MEDIUM (yellow), or LOW (green). The color-coded dialog makes the severity instantly clear.

### 3. 23 Built-in Rules
Pre-configured patterns covering 8 categories: filesystem (`rm -rf`, `mkfs`, `dd`), Kubernetes (`kubectl delete`, `kubectl apply --force`), Terraform (`terraform destroy`, `terraform apply`), Docker (`docker system prune`, `docker rm -f`), Git (`git push --force`, `git reset --hard`), SQL (`DROP TABLE`, `DROP DATABASE`, `TRUNCATE`), system services (`systemctl stop`, `service stop`), and permissions (`chmod 777`).

### 4. Custom Rules
Add your own dangerous command patterns with glob or regex matching. Choose the risk level, category, and description shown in the warning dialog.

### 5. Color-Coded Confirmation Dialog
A modal dialog shows the matched command, risk level, category, and a human-readable explanation of why the command is dangerous. Choose "Execute Anyway" or "Cancel".

### 6. Team-Shared Rules
Place a `.terminal-safety-rules.yaml` file in your project root and commit it to Git. Team rules are loaded automatically and merged with built-in and personal custom rules.

### 7. Context-Aware Detection (Pro)
Smarter matching that considers the current environment and working directory for more accurate interception.

### 8. Audit Log (Pro)
Track all intercepted commands with timestamps, risk levels, and whether they were allowed or blocked. Useful for compliance and post-incident review.

---

## How It Works

1. **Install and type** — Install the plugin and use the IDE terminal as normal. Safe commands execute without any interruption.
2. **Dangerous command detected** — When you type a dangerous command, a color-coded confirmation dialog appears before execution.
3. **Review and decide** — The dialog shows the matched command, risk level, and explanation. Choose to execute or cancel.
4. **Customize your rules** — Add organization-specific patterns in Settings or share rules via a YAML file in your project.

---

## Pricing

| Feature | Free | Pro ($3.90/mo) |
|---------|:----:|:--------------:|
| Command interception | ✅ | ✅ |
| 4 risk levels | ✅ | ✅ |
| 23 built-in rules | ✅ | ✅ |
| Custom rules | Up to 5 | Unlimited |
| Color-coded dialogs | ✅ | ✅ |
| Enable/disable toggle | ✅ | ✅ |
| Team-shared rules | — | ✅ |
| Context-aware detection | — | ✅ |
| Audit log | — | ✅ |
| Command statistics | — | ✅ |

---

## Testimonials

> *"This plugin saved us from an accidental terraform destroy on our production environment. Paid for itself in the first week."*
> — **DevOps Engineer, Cloud Infrastructure Company**

> *"We mandated Terminal Safety Rails for all developers after a junior engineer ran rm -rf on a shared mount. Zero productivity cost for safe commands."*
> — **Engineering Director, Media Company**

> *"The team-shared rules feature is great. We added our internal deployment scripts as CRITICAL patterns and everyone gets the same protection."*
> — **Platform Engineer, E-Commerce Platform**

---

## FAQ

**Q: Does this slow down normal terminal usage?**
A: No. Safe commands execute instantly without any interruption. The confirmation dialog only appears when a dangerous pattern is matched.

**Q: Can I disable the plugin temporarily?**
A: Yes. There is an enable/disable toggle in Settings → Tools → Terminal Safety Rails.

**Q: Does it work with all terminal shells?**
A: It works with the built-in IDE terminal. It intercepts commands at the IDE level before they reach the shell.

**Q: Can I add rules for my company's internal tools?**
A: Yes. Add custom rules in Settings or create a `.terminal-safety-rules.yaml` file in your project root to share rules with your team.

**Q: Which JetBrains IDEs are supported?**
A: All JetBrains IDEs based on IntelliJ Platform 2024.3+ with the bundled Terminal plugin.

---

## Get Started

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com) and start typing. The plugin protects you from day one with 23 built-in rules.
