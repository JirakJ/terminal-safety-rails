# Copilot Instructions — Terminal Safety Rails

## Projekt
JetBrains IDE plugin (IntelliJ Platform) — detekuje nebezpečné příkazy v IDE terminálu a zobrazuje potvrzovací dialog.

## Technický stack
- **Jazyk:** Kotlin (JVM 17+)
- **Build:** Gradle (Kotlin DSL) s `org.jetbrains.intellij.platform` plugin 2.x
- **Platform:** IntelliJ Platform SDK 2024.3+
- **Testování:** JUnit 5 + IntelliJ test framework (`BasePlatformTestCase`)

## Build a testy
```bash
./gradlew build                    # Build + testy
./gradlew test                     # Pouze testy
./gradlew test --tests "*.TerminalSafetyRailsTest.testRmDetection"  # Jeden test
./gradlew runIde                   # Spuštění IDE s pluginem
./gradlew verifyPlugin             # Ověření kompatibility
./gradlew buildPlugin              # Sestavení distribučního ZIP
```

## Architektura
- **Core:** Pattern matching engine vyhodnocující příkazy oproti sadě `SafetyRule` (regex-based)
- **Terminal integration:** `ShellCommandHandler` EP pro interceptování příkazů před odesláním do shellu
- **UI:** `DialogWrapper` pro potvrzovací dialog, `Configurable` pro settings page
- **Persistence:** `PersistentStateComponent` pro uživatelská pravidla, `.terminal-safety-rules.yaml` pro team-shared pravidla
- **Freemium:** Licence kontrola přes JetBrains Marketplace licensing API

## Konvence
- Balíčky: `com.jirak.terminalsafety.*`
- Extension points registrované v `plugin.xml` pod `<extensions defaultExtensionNs="com.jirak.terminalsafety">`
- Pravidla severity: `CRITICAL` (vyžaduje přepsání textu), `WARNING` (confirm dialog), `CAUTION` (dismiss notifikace)
- Všechny UI texty v `messages/TerminalSafetyBundle.properties` (i18n ready)
- Verzování: `2026.2.X` (rok.měsíc.pořadí_releasu)

## Freemium hranice
- Free: 15 built-in pravidel, max 5 custom, základní dialog
- Paid: neomezená pravidla, context-aware detekce, audit log, statistiky
