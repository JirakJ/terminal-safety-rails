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
  <li><b>Pre-execution interception</b> — catches commands typed in the IDE terminal before they reach the shell</li>
  <li><b>33 built-in rules</b> — filesystem, Kubernetes, Terraform, Docker, AWS/GCP/Azure, Git, SQL, system services and permissions</li>
  <li><b>13 heuristic detectors</b> — a 0–100 risk score independent of the rules (privilege escalation, remote code execution, production targets such as <code>kubectl --context=prod</code>, unscoped SQL mutations, …)</li>
  <li><b>4 risk levels</b> — 🔴 CRITICAL, 🟠 HIGH, 🟡 MEDIUM, 🟢 LOW — with color-coded confirmation dialogs</li>
  <li><b>Command explainer &amp; safer alternatives</b> — plain-English explanation and copy-pasteable safer equivalents in the dialog</li>
  <li><b>Enforcement modes</b> — Warn only / Confirm / Block critical / Lockdown</li>
  <li><b>Custom &amp; team rules</b> — your own glob/regex rules in Settings, plus team rules from a committed <code>.terminal-safety-rules.yaml</code></li>
  <li><b>Incident audit log</b> — every interception with timestamp, rule and decision; CSV/JSON export</li>
  <li><b>Rule effectiveness analytics</b> — per-rule hit/block/allow counters with CSV export</li>
  <li><b>Shell history risk audit</b> — score your <code>~/.zsh_history</code> / <code>~/.bash_history</code> (MD/JSON/HTML export, headless CLI)</li>
</ul>

<h2>Getting Started</h2>
<ol>
  <li><b>Install</b> — search "Terminal Safety Rails" in <code>Settings → Plugins</code> and click Install</li>
  <li><b>Use the terminal</b> — type any command as usual; dangerous commands trigger a confirmation dialog</li>
  <li><b>Customize</b> — add custom rules in <code>Settings → Tools → Terminal Safety Rails</code></li>
</ol>

<h2>Compatibility</h2>
<ul>
  <li>IntelliJ IDEA 2025.1+ and other IntelliJ-based IDEs with the Terminal plugin</li>
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
All features are free for everyone — no license needed. Source code under Apache-2.0: https://github.com/JirakJ/terminal-safety-rails

## Screenshots Needed
1. **Confirmation dialog** — color-coded modal showing a CRITICAL-risk command (e.g., `rm -rf /`) with risk level, command details, and Execute/Cancel buttons
2. **Settings** — enforcement mode and custom rules
3. **Risk alert** — heuristic risk score for a `kubectl --context=prod …` command
4. **Audit log** — the interception history view with timestamps, commands, and ALLOWED/BLOCKED status
5. **Team rules file** — example `.terminal-safety-rules.yaml` loaded in the IDE
