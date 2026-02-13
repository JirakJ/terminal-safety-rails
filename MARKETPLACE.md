# Terminal Safety Rails — JetBrains Marketplace Listing

## Plugin Name
Terminal Safety Rails

## Tagline
Intercept dangerous terminal commands before they execute — protect prod, always.

## Short Description
Detect and block destructive terminal commands like rm -rf, kubectl delete, and terraform destroy with risk-level warnings and confirmation dialogs before execution.

## Full Description

```html
<p>
  <b>Catch dangerous commands before they cause damage.</b> Terminal Safety Rails intercepts destructive
  terminal commands — like <code>rm -rf /</code>, <code>kubectl delete</code>, and <code>terraform destroy</code> —
  and requires explicit confirmation before execution. It's the safety net every developer needs.
</p>

<h2>Key Features</h2>
<ul>
  <li><b>Pre-execution interception</b> — catches commands before they reach the shell</li>
  <li><b>4 risk levels</b> — 🔴 CRITICAL, 🟠 HIGH, 🟡 MEDIUM, 🟢 LOW — with color-coded confirmation dialogs</li>
  <li><b>23 built-in dangerous command patterns</b> — covering filesystem, Kubernetes, Terraform, Docker, Git, SQL, system services, and permissions</li>
  <li><b>Context-aware detection (Pro)</b> — understands kubectl context, terraform workspace, and git branch</li>
  <li><b>Team-shared rules</b> — commit <code>.terminal-safety-rules.yaml</code> to your repository</li>
  <li><b>Audit logging (Pro)</b> — track every interception with timestamps and decisions</li>
  <li><b>Command analytics (Pro)</b> — statistics dashboard for interception patterns</li>
</ul>

<h3>Built-in Protection Covers</h3>
<ul>
  <li><b>CRITICAL:</b> <code>rm -rf /</code>, <code>rm -rf ~</code>, <code>rm -rf *</code>, <code>mkfs</code>, <code>dd if=</code>, <code>DROP TABLE</code>, <code>DROP DATABASE</code>, <code>TRUNCATE</code></li>
  <li><b>HIGH:</b> <code>kubectl delete</code>, <code>kubectl apply --force</code>, <code>terraform apply</code>, <code>terraform destroy</code></li>
  <li><b>MEDIUM:</b> <code>docker system prune</code>, <code>docker rm -f</code>, <code>git push --force</code>, <code>git reset --hard</code>, <code>chmod 777</code>, <code>systemctl stop</code></li>
</ul>

<h2>Free Tier</h2>
<p>Comprehensive protection out of the box — no credit card required:</p>
<ul>
  <li>Command interception with pre-execution validation</li>
  <li>All 23 built-in dangerous command patterns</li>
  <li>4-level risk classification with color-coded dialogs</li>
  <li>Up to 5 custom rules</li>
  <li>Team-shared rules via <code>.terminal-safety-rules.yaml</code></li>
  <li>Enable/disable toggle</li>
</ul>

<h2>Pro Tier ($3.90/month)</h2>
<p>Designed for teams who work with production systems daily:</p>
<ul>
  <li><b>Unlimited custom rules</b> — define patterns specific to your infrastructure</li>
  <li><b>Context-aware detection</b> — automatic risk elevation based on kubectl context, terraform workspace, or git branch</li>
  <li><b>Audit log</b> — complete history of intercepted commands with ALLOWED/BLOCKED/CANCELLED status</li>
  <li><b>Analytics dashboard</b> — visualize interception statistics and patterns</li>
  <li><b>Command interception analytics</b> — identify the most frequently triggered rules</li>
</ul>

<h2>Team Tier ($7.90/user/month)</h2>
<p>Designed for teams who need centralized safety enforcement:</p>
<ul>
  <li>All Pro features included</li>
  <li><b>Team notifications</b> — webhook integration for critical interceptions</li>
  <li><b>Centralized policy enforcement</b> — manage rules across the organization</li>
  <li><b>Admin dashboard</b> — team-wide visibility into command safety</li>
</ul>

<h2>Getting Started</h2>
<ol>
  <li><b>Install</b> — search "Terminal Safety Rails" in <code>Settings → Plugins</code> and click Install</li>
  <li><b>Use the terminal</b> — type any command as usual; dangerous commands trigger a confirmation dialog</li>
  <li><b>Customize</b> — add custom rules in <code>Settings → Tools → Terminal Safety Rails</code></li>
</ol>

<h2>Compatibility</h2>
<ul>
  <li>IntelliJ IDEA 2024.3+ and all JetBrains IDEs</li>
  <li>macOS, Linux, and Windows</li>
  <li>Works with all shell types supported by the IDE terminal</li>
</ul>

<h2>Part of the Developer Productivity Suite</h2>
<p>
  Terminal Safety Rails is part of a comprehensive developer safety toolkit. Combine it with
  <b>Kubernetes Context Guard</b> (always know your kubectl context),
  <b>Local Secrets Tripwire</b> (prevent secret leaks in code),
  <b>Architecture Breadcrumbs</b> (embed architectural decisions in code), and
  <b>ADR Companion</b> (manage Architecture Decision Records)
  for complete development workflow protection.
</p>
```

## Tags
terminal, safety, devops, command-protection, production-safety

## Category
DevOps

## Pricing
| Tier | Price | Features |
|------|-------|----------|
| Free | $0 | 23 built-in patterns, 4 risk levels, color-coded dialogs, up to 5 custom rules, team-shared rules |
| Pro | $3.90/month | Unlimited custom rules, context-aware detection, audit log, analytics dashboard |
| Team | $7.90/user/month | All Pro features + team notifications, centralized policy enforcement, admin dashboard |

## Screenshots Needed
1. **Confirmation dialog** — color-coded modal showing a CRITICAL-risk command (e.g., `rm -rf /`) with risk level, command details, and Execute/Cancel buttons
2. **Built-in rules list** — the settings panel displaying all 23 dangerous command patterns organized by risk level
3. **Context-aware detection (Pro)** — showing elevated risk when kubectl context is production
4. **Audit log (Pro)** — the interception history view with timestamps, commands, and ALLOWED/BLOCKED status
5. **Team rules file** — example `.terminal-safety-rules.yaml` loaded in the IDE
