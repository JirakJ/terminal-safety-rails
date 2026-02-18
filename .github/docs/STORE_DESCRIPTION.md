# JetBrains Marketplace Store Description — Terminal Safety Rails

```html
<h2>Terminal Safety Rails — Intercept Dangerous Commands Before Execution</h2>

<p>
  A single destructive command can ruin your day. Terminal Safety Rails intercepts dangerous commands
  like <code>rm -rf</code>, <code>kubectl delete</code>, and <code>terraform destroy</code> before
  they execute — showing a risk-level confirmation dialog that requires explicit approval.
</p>

<h3>Command Interception</h3>
<ul>
  <li><b>23 Built-in Rules</b> — Covers filesystem, Kubernetes, Terraform, Docker, Git, SQL, system services, and permissions</li>
  <li><b>4 Risk Levels</b> — CRITICAL (red), HIGH (orange), MEDIUM (yellow), LOW (green) with color-coded dialogs</li>
  <li><b>Confirmation Dialog</b> — Shows matched command, risk level, category, and explanation before execution</li>
  <li><b>Zero overhead for safe commands</b> — Only dangerous patterns trigger the dialog</li>
</ul>

<h3>Customization</h3>
<ul>
  <li><b>Custom Rules</b> — Add your own patterns with glob or regex matching and configurable risk levels</li>
  <li><b>Team-Shared Rules</b> — Share rules via .terminal-safety-rules.yaml in your project root</li>
  <li><b>Enable/Disable Toggle</b> — Quickly turn protection on and off</li>
</ul>

<h3>Pro Features</h3>
<ul>
  <li><b>Unlimited Custom Rules</b> — No cap on custom command patterns</li>
  <li><b>Context-Aware Detection</b> — Smarter matching based on environment</li>
  <li><b>Audit Log</b> — Track all intercepted commands with timestamps and actions</li>
  <li><b>Command Statistics</b> — Analytics on intercepted commands and usage patterns</li>
</ul>

<h3>Pricing</h3>
<ul>
  <li><b>Free:</b> Command interception, 23 built-in rules, 4 risk levels, up to 5 custom rules, color-coded dialogs</li>
  <li><b>Pro ($3.90/month):</b> Unlimited custom rules, context-aware detection, audit log, team-shared rules, statistics</li>
</ul>

<h3>Requirements</h3>
<ul>
  <li>IntelliJ IDEA 2024.3+ (or any JetBrains IDE based on IntelliJ Platform)</li>
  <li>Terminal plugin (bundled with JetBrains IDEs)</li>
  <li>Java 17+</li>
</ul>

<h3>Support</h3>
<ul>
  <li><a href="https://github.com/JirakJ/terminal-safety-rails/issues">Issue Tracker</a></li>
  <li><a href="https://github.com/JirakJ/terminal-safety-rails">Documentation</a></li>
</ul>
```
