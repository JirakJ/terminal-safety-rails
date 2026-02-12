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
- Balíčky: `com.jakubjirak.terminalsafety.*`
- Extension points registrované v `plugin.xml` pod `<extensions defaultExtensionNs="com.jakubjirak.terminalsafety">`
- Pravidla severity: `CRITICAL` (vyžaduje přepsání textu), `WARNING` (confirm dialog), `CAUTION` (dismiss notifikace)
- Všechny UI texty v `messages/TerminalSafetyBundle.properties` (i18n ready)
- Verzování: `2026.2.X` (rok.měsíc.pořadí_releasu)

## Production-grade požadavky
- Všechny I/O operace na background threadech (`Task.Backgroundable`, coroutines)
- Read/Write actions správně (`ReadAction.run {}`, `WriteAction.run {}`)
- `Disposable` pattern pro listeners a services
- Exception handling: žádné unhandled exceptions, graceful degradation
- Logging: `Logger.getInstance(MyClass::class.java)` v každé třídě
- EDT nesmí být blokován — žádné síťové/git/file operace na EDT
- Smart mode check (`DumbService.isDumb()`) kde potřeba

## Freemium architektura
- `FreemiumGate` centrální guard: `FreemiumGate.isPaidFeatureAvailable("feature.id")`
- JetBrains Marketplace Licensing API přes `<product-descriptor>` v `plugin.xml`
- Paid UI elementy: skryté nebo disabled ve free tieru (ne crash/error)
- Trial: 30 dní plný přístup, pak downgrade na free tier
- Upgrade prompt: max 1× denně, non-intrusive balloon

## CI/CD
- GitHub Actions: `./gradlew check`, `verifyPlugin`, `runPluginVerifier`
- Detekt pro statickou analýzu Kotlin kódu
- Tag-based release → `publishPlugin` na Marketplace
- Kotlin coding conventions (JetBrains official style)

## Ikona
- `src/main/resources/META-INF/pluginIcon.svg` (40×40, light)
- `src/main/resources/META-INF/pluginIcon_dark.svg` (40×40, dark)
- Action ikony: 16×16 + 13×13 SVG (light + dark varianty)

## Freemium hranice
- Free: 15 built-in pravidel, max 5 custom, základní dialog
- Paid: neomezená pravidla, context-aware detekce, audit log, statistiky
