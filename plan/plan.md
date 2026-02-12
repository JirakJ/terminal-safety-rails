# Terminal Safety Rails — Implementační plán

**Verze:** 2026.2.1
**Status:** Připraveno k implementaci

---

## Fáze 0: Plugin Ikona & Branding

- [ ] Navrhnout plugin ikonu (SVG + PNG)
  - [ ] Koncept: 🛡️ Štít se závorou/railem
  - [ ] Hlavní ikona: 40×40 SVG pro Marketplace listing
  - [ ] Toolbar ikona: 16×16 + 13×13 SVG (light + dark varianta)
  - [ ] Tool window ikona: 13×13 SVG
  - [ ] `src/main/resources/META-INF/pluginIcon.svg` (40×40, light theme)
  - [ ] `src/main/resources/META-INF/pluginIcon_dark.svg` (40×40, dark theme)
  - [ ] Action ikony pro toolbar/menu (16×16 SVG, light + dark)
- [ ] Barevná paleta konzistentní s DevOps Safety Kit brandem
- [ ] Marketplace banner obrázek (1280×800)
- [ ] Screenshoty pro Marketplace listing (min 3, 1280×800 PNG)

## Fáze 1: Projekt scaffolding

- [ ] Inicializovat IntelliJ Platform Plugin projekt (Gradle Kotlin DSL)
  - [ ] `build.gradle.kts` — plugin metadata, IntelliJ Platform 2024.3+, JVM 17
  - [ ] `settings.gradle.kts`
  - [ ] `gradle.properties` — verze pluginu `2026.2.1`, platform verze
  - [ ] Gradle wrapper (8.x)
- [ ] `src/main/resources/META-INF/plugin.xml`
  - [ ] Plugin ID: `com.jakubjirak.terminalsafety`
  - [ ] Název, popis, vendor, závislost na `com.intellij.modules.platform`
- [ ] Balíčková struktura: `com.jakubjirak.terminalsafety.*`
  - [ ] `.model` — datové třídy
  - [ ] `.core` — pattern matching engine
  - [ ] `.settings` — konfigurace
  - [ ] `.ui` — dialogy, tool window, status bar
  - [ ] `.terminal` — terminal interceptor
- [ ] `src/main/resources/messages/TerminalSafetyBundle.properties` — i18n stringy
- [ ] `.gitignore` (build/, .idea/, .gradle/, .kotlin/, out/)
- [ ] `gradle/libs.versions.toml` — version catalog
- [ ] `LICENSE` (Apache-2.0)
- [ ] `.editorconfig` (Kotlin style conventions)
- [ ] Detekt konfigurace (`detekt.yml`)
- [ ] GitHub Actions CI workflow (`.github/workflows/build.yml`)
- [ ] Ověřit `./gradlew build` projde

## Fáze 2: Datový model

- [ ] `SafetyRule.kt` — data class
  - [ ] `id: String`, `name: String`, `pattern: String` (regex)
  - [ ] `severity: Severity` (CRITICAL, WARNING, CAUTION)
  - [ ] `description: String`, `contextCondition: String?`
  - [ ] `enabled: Boolean`, `source: RuleSource` (BUILTIN, USER, TEAM)
- [ ] `Severity.kt` — enum s barvami a ikonami
- [ ] `RuleSource.kt` — enum
- [ ] `InterceptionEvent.kt` — data class pro audit log
- [ ] `MatchResult.kt` — výsledek pattern match (rule, command, groups)
- [ ] Unit testy pro datové třídy

## Fáze 3: Pattern Matching Engine

- [ ] `PatternMatcher.kt` — vyhodnocení příkazu oproti sadě pravidel
  - [ ] `fun match(command: String, rules: List<SafetyRule>): List<MatchResult>`
  - [ ] Regex kompilace s cache (compiled patterns)
  - [ ] Podpora pipe chains: `cmd1 | cmd2` — kontrolovat každý segment
  - [ ] Podpora `&&`, `||`, `;` — kontrolovat každý sub-příkaz
- [ ] `BuiltinRules.kt` — 15 předdefinovaných pravidel:
  - [ ] `rm -rf /`, `rm -rf ~`, `rm -rf *` (CRITICAL)
  - [ ] `kubectl delete` (WARNING), `kubectl delete namespace` (CRITICAL)
  - [ ] `kubectl apply` — na prod kontextu (WARNING)
  - [ ] `terraform destroy` (CRITICAL), `terraform apply` bez `-auto-approve` (WARNING)
  - [ ] `docker system prune -a` (WARNING)
  - [ ] `git push --force` / `git push -f` na main/master/prod (WARNING)
  - [ ] `DROP DATABASE`, `DROP TABLE`, `TRUNCATE TABLE` (CRITICAL)
  - [ ] `chmod 777` (CAUTION)
  - [ ] `mkfs` / `dd if=` (CRITICAL)
  - [ ] `> /dev/sda` (CRITICAL)
- [ ] Unit testy pro PatternMatcher — každý built-in pravidlo
- [ ] Unit testy pro edge cases (quoted strings, escaped chars, subshells)

## Fáze 4: Terminal Integration

- [ ] `TerminalCommandInterceptor.kt`
  - [ ] Implementovat `ShellCommandHandler` EP (nebo `TerminalCommandHandler`)
  - [ ] Prozkoumat dostupné Terminal API v 2024.3+
  - [ ] Fallback: `LocalTerminalCustomizer` pro shell wrapper přístup
- [ ] Interceptovat příkaz PŘED odesláním do shellu
- [ ] Při matchi → zavolat UI vrstvu (dialog)
- [ ] Při no-match → příkaz prochází bez zpoždění
- [ ] Integrační test s mock terminálem

## Fáze 5: Potvrzovací dialog

- [ ] `SafetyConfirmationDialog.kt` extends `DialogWrapper`
  - [ ] Zobrazit: příkaz (syntax highlighted), matched rule, severity, popis rizika
  - [ ] Tlačítka: „Spustit přesto" (⚠️) / „Zrušit" (default focus)
  - [ ] Pro CRITICAL: textové pole — uživatel musí napsat potvrzovací frázi
  - [ ] Checkbox: „Neupozorňovat na toto pravidlo pro tento příkaz" (whitelist)
- [ ] Barvy dle severity (červená/oranžová/žlutá header)
- [ ] Ikona dle severity
- [ ] UI test

## Fáze 6: Settings Page

- [ ] `TerminalSafetyConfigurable.kt` implements `Configurable`
  - [ ] Registrace v `plugin.xml`: `<applicationConfigurable>`
  - [ ] Umístění: Settings → Tools → Terminal Safety Rails
- [ ] `TerminalSafetySettings.kt` implements `PersistentStateComponent`
  - [ ] Uložení: enabled/disabled rules, custom rules, global enable
  - [ ] Storage: `terminalSafetySettings.xml`
- [ ] Settings UI:
  - [ ] Tabulka pravidel (name, pattern, severity, enabled toggle)
  - [ ] „Add Rule" / „Edit Rule" / „Remove Rule" tlačítka
  - [ ] „Reset to Defaults" tlačítko
  - [ ] Checkbox: globální zapnutí/vypnutí
  - [ ] Checkbox: zobrazit notifikaci místo dialogu pro CAUTION
- [ ] `RuleEditorDialog.kt` — dialog pro přidání/editaci custom pravidla
- [ ] Unit testy pro PersistentStateComponent (serialize/deserialize)

## Fáze 7: Team-shared Rules

- [ ] `TeamRulesLoader.kt`
  - [ ] Načtení `.terminal-safety-rules.yaml` z projekt root
  - [ ] YAML parsing (SnakeYAML / Jackson YAML)
  - [ ] Merge: BUILTIN + USER + TEAM pravidla
  - [ ] File watcher pro automatický reload
- [ ] YAML schema definice
- [ ] Ukázkový `.terminal-safety-rules.yaml` v docs/examples
- [ ] Unit testy pro YAML parsing

## Fáze 8: Status Bar Widget

- [ ] `SafetyStatusBarWidget.kt` implements `StatusBarWidgetFactory`
  - [ ] Registrace v `plugin.xml`
  - [ ] Zobrazení: „🛡️ Safety: ON" / „⚠️ Safety: OFF"
  - [ ] Klik: toggle enable/disable
  - [ ] Tooltip: počet aktivních pravidel, poslední interception
- [ ] Ikona v status baru

## Fáze 9: Notifications

- [ ] `SafetyNotificationGroup.kt`
  - [ ] Registrace notification group v `plugin.xml`
  - [ ] Notifikace při:
    - [ ] Plugin prvním spuštění (welcome)
    - [ ] CAUTION level match (balloon notification místo dialogu)
    - [ ] Team rules loaded/updated

## Fáze 10: Freemium & Licensing Infrastructure

- [ ] Integrace JetBrains Marketplace Licensing API
  - [ ] `LicenseChecker.kt` — ověření licence přes `com.intellij.ide.plugins.PluginManager` a Marketplace API
  - [ ] `LicenseStatus.kt` — enum: FREE, TRIAL, PAID, EXPIRED
  - [ ] `FreemiumGate.kt` — centrální guard pro paid features
    - [ ] `fun isPaidFeatureAvailable(featureId: String): Boolean`
    - [ ] Graceful degradation — paid UI elementy skryté/disabled ve free tieru
  - [ ] Trial period: 30 dní plný přístup
- [ ] `plugin.xml` — `<product-descriptor>` element pro Marketplace licensing
  - [ ] `code` atribut (unique product code)
  - [ ] `release-date`, `release-version`
- [ ] Nag screen / upgrade prompt
  - [ ] Non-intrusive balloon notification při pokusu o paid feature
  - [ ] „Upgrade to Pro" action → Marketplace stránka
  - [ ] Max 1× denně (ne spam)
- [ ] Feature flags
  - [ ] `FeatureRegistry.kt` — definice free vs. paid features
  - [ ] Každý paid feature má `@PaidFeature("feature.id")` anotaci nebo guard check
- [ ] Unit testy pro licensing logic (mock Marketplace API)

## Fáze 11: Production-Grade Quality

- [ ] Error Handling & Resilience
  - [ ] Globální exception handler (`ErrorReportSubmitter` implementace)
  - [ ] Graceful degradation — plugin nesmí crashnout IDE
  - [ ] Try-catch na všech EP entry pointech
  - [ ] Timeout na všech I/O operacích (network, git, file system)
  - [ ] Rate limiting na API volání
- [ ] Logging
  - [ ] `Logger` instance v každé třídě (IntelliJ `Logger.getInstance()`)
  - [ ] Structured logging: DEBUG pro vývoj, INFO pro operace, WARN/ERROR pro problémy
  - [ ] Žádné citlivé údaje v logech
- [ ] Telemetrie (opt-in, anonymní)
  - [ ] `PluginUsageTracker.kt` — FUS (Feature Usage Statistics) integrace
  - [ ] Metriky: aktivace featury, error rate, latence
  - [ ] Registrace FUS group v `plugin.xml`
- [ ] Performance
  - [ ] Profiling na velkých projektech (10k+ souborů)
  - [ ] Background thread pro všechny I/O operace (nesmí blokovat EDT)
  - [ ] Read/write action správně (ReadAction, WriteAction, smart mode check)
  - [ ] Disposal pattern: `Disposable` pro všechny listeners a services
  - [ ] Memory leak check (WeakReference kde potřeba)
- [ ] Testing — production úroveň
  - [ ] Unit testy: ≥80 % coverage core logiky
  - [ ] Integrační testy: `BasePlatformTestCase` pro PSI, inspections, EP
  - [ ] UI testy: `HeavyPlatformTestCase` pro tool windows, dialogy
  - [ ] Performance testy: benchmark na velkých souborech
- [ ] CI/CD Pipeline
  - [ ] GitHub Actions workflow:
    - [ ] `./gradlew check` — kompilace + testy
    - [ ] `./gradlew verifyPlugin` — plugin strukturální validace
    - [ ] `./gradlew verifyPluginProjectConfiguration` — metadata validace
    - [ ] `./gradlew runPluginVerifier` — binární kompatibilita s target IDE verzemi
    - [ ] Qodana scan (JetBrains code quality)
  - [ ] Release workflow:
    - [ ] Tag-based release (push tag → build → publish)
    - [ ] `./gradlew publishPlugin` — automatický upload na Marketplace
    - [ ] Changelog generace z git commitů
  - [ ] Dependabot / Renovate pro dependency updates
- [ ] Kódová kvalita
  - [ ] Kotlin coding conventions (official JetBrains style)
  - [ ] Detekt nebo ktlint pro statickou analýzu
  - [ ] Žádné deprecated API (IntelliJ Platform compatibility)
  - [ ] `@ApiStatus.Experimental` / `@ApiStatus.Internal` anotace
- [ ] Dokumentace
  - [ ] KDoc pro všechny public API
  - [ ] README.md: instalace, konfigurace, screenshoty, FAQ
  - [ ] CHANGELOG.md (Keep a Changelog format)
  - [ ] CONTRIBUTING.md (pokud open source)

## Fáze 12: Polish & Release

- [ ] `plugin.xml` — finální popis, changelog, screenshots reference
- [ ] README.md — instalace, konfigurace, screenshots
- [ ] `./gradlew verifyPlugin` — bez chyb
- [ ] `./gradlew buildPlugin` — ZIP artefakt
- [ ] Manuální testování v IntelliJ IDEA
- [ ] Testování v PyCharm, WebStorm (cross-IDE)
- [ ] Marketplace listing připravit (logo, screenshoty, popis)
- [ ] Git tag `v2026.2.1`

---

## Budoucí fáze (Paid features — po MVP)

- [ ] Context-aware detekce (kubectl context, terraform workspace, git branch)
- [ ] Audit log s exportem (CSV/JSON)
- [ ] Statistiky dashboard (tool window)
- [ ] Team notifikace (webhook integrace)
- [ ] Marketplace licensing API integrace
