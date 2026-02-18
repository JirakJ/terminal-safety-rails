# Screenshot Plan — Terminal Safety Rails

## Screenshots

### 1. Critical Risk Dialog
**Title:** CRITICAL Risk — Command Blocked with Red Warning
**What to capture:** The confirmation dialog for a CRITICAL command (e.g., `rm -rf /`). Show the red color-coded dialog with the command text, "CRITICAL" badge, category "FILESYSTEM", and explanation text. Include the "Execute Anyway" and "Cancel" buttons.

### 2. High Risk Dialog
**Title:** HIGH Risk — Kubernetes Delete Intercepted
**What to capture:** The confirmation dialog for `kubectl delete namespace production` showing the orange HIGH risk dialog with the Kubernetes category and explanation.

### 3. Medium Risk Dialog
**Title:** MEDIUM Risk — Git Force Push Warning
**What to capture:** The confirmation dialog for `git push --force` showing the yellow MEDIUM risk dialog with the Git category.

### 4. Settings Page
**Title:** Configure Custom Rules and Preferences
**What to capture:** The Settings → Tools → Terminal Safety Rails page showing the enable/disable toggle and the custom rules table with columns for Pattern, Risk Level, Category, Description, and Is Regex.

### 5. Custom Rule Creation
**Title:** Add Organization-Specific Dangerous Command Patterns
**What to capture:** The custom rule creation row in the settings table, filled with an example pattern like `deploy --prod --force` at CRITICAL level with a description.

### 6. Team-Shared Rules File
**Title:** Share Safety Rules with Your Team via YAML
**What to capture:** A `.terminal-safety-rules.yaml` file open in the editor showing a sample team configuration with custom rules, demonstrating the YAML format.

### 7. Terminal in Action
**Title:** Normal Terminal Usage — Zero Overhead
**What to capture:** The IDE terminal with a series of safe commands (`ls`, `cd`, `git status`) executed normally, followed by a dangerous command triggering the dialog. Show the contrast between smooth normal usage and the safety interception.

### 8. Audit Log (Pro)
**Title:** Track Every Intercepted Command
**What to capture:** The audit log view showing a list of intercepted commands with timestamps, risk levels, matched rules, and ALLOWED/BLOCKED status.

---

## Video Demo Outline (40 seconds)

| Time | Scene | Description |
|------|-------|-------------|
| 0:00–0:05 | Title card | "Terminal Safety Rails — Stop Dangerous Commands" |
| 0:05–0:12 | Safe commands | Type safe commands in the terminal (ls, cd, git status) — they execute instantly |
| 0:12–0:20 | Dangerous command | Type `rm -rf /tmp/*` — show the red CRITICAL confirmation dialog |
| 0:20–0:25 | Cancel | Click Cancel to block the command |
| 0:25–0:30 | Kubernetes | Type `kubectl delete pod` — show the orange HIGH dialog |
| 0:30–0:35 | Custom rules | Quick view of the settings page with custom rules |
| 0:35–0:40 | End card | "Install free from JetBrains Marketplace" with link |
