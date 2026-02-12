# Terminal Safety Rails — Implementační plán

**Verze:** 2026.2.1
**Status:** Připraveno k implementaci

---

## Fáze 1: Projekt scaffolding

- [ ] Inicializovat IntelliJ Platform Plugin projekt (Gradle Kotlin DSL)
  - [ ] `build.gradle.kts` — plugin metadata, IntelliJ Platform 2024.3+, JVM 17
  - [ ] `settings.gradle.kts`
  - [ ] `gradle.properties` — verze pluginu `2026.2.1`, platform verze
  - [ ] Gradle wrapper (8.x)
- [ ] `src/main/resources/META-INF/plugin.xml`
  - [ ] Plugin ID: `com.jirak.terminalsafety`
  - [ ] Název, popis, vendor, závislost na `com.intellij.modules.platform`
- [ ] Balíčková struktura: `com.jirak.terminalsafety.*`
  - [ ] `.model` — datové třídy
  - [ ] `.core` — pattern matching engine
  - [ ] `.settings` — konfigurace
  - [ ] `.ui` — dialogy, tool window, status bar
  - [ ] `.terminal` — terminal interceptor
- [ ] `src/main/resources/messages/TerminalSafetyBundle.properties` — i18n stringy
- [ ] `.gitignore` (build/, .idea/, .gradle/)
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

## Fáze 10: Polish & Release

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
