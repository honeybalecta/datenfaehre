# Hinweise für Claude

- Oberfläche bewusst im klassischen Desktop-Aufbau (Werkzeugleiste, Schnellverbindung, Nachrichtenprotokoll, lokal/Server,
  Warteschlange mit drei Reitern), helle graue Flächen, blaue Auswahl – Raphaels Wunsch. Seine Standardfarben
  (Anthrazit, Creme, Rot) gelten hier nur für das App-Symbol.
- Sprache der Oberfläche: Deutsch, gegendert mit Doppelpunkt.
- Builds laufen nur über GitHub Actions (Log im Zweig `ci-log-android`). `versionCode = github.run_number`, fester
  Signaturschlüssel – sonst scheitern Updates.
- Änderungen sammeln und gebündelt pushen (Actions-Minuten).
- Aufnahme in „Honeys Werkstatt“ (`katalog.json` dort) nur nach Rückfrage. Paket: `de.honeybalecta.datenfaehre`,
  APK-Name `Datenfaehre-<version>.apk`.
