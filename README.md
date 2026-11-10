# Datenfähre

Dateien zwischen Android-Gerät und Server übertragen – per FTP, FTP über TLS (explizit und implizit) und SFTP.
Aufbau wie ein klassischer Desktop-Client: Werkzeugleiste, Schnellverbindung, Nachrichtenprotokoll,
lokale Ansicht und Server-Ansicht, darunter die Warteschlange.

## Bedienung

- **Antippen** wählt Einträge aus (mehrere möglich), **Doppeltippen** öffnet ein Verzeichnis oder überträgt die Datei,
  **langes Drücken** öffnet das Kontextmenü (Hochladen/Herunterladen, Zur Warteschlange hinzufügen, Verzeichnis erstellen,
  Umbenennen, Löschen, Aktualisieren).
- **Schnellverbindung**: `sftp://host`, `ftp://host` (unverschlüsselt), `ftps://host` (implizites TLS), `ftpes://host` (explizites TLS).
  Ohne Angabe: Port 22 bedeutet SFTP, sonst FTP über TLS.
- **Gespeicherte Server**: Die Schnellverbindung legt Zugangsdaten nach erfolgreicher Anmeldung im Servermanager ab
  (abschaltbar über den Pfeil neben „Verbinden“); derselbe Pfeil listet die gespeicherten Server zum direkten Verbinden.
  Passwörter liegen verschlüsselt im Android-Schlüsselspeicher.
- **Warteschlange**: Reiter für offene, fehlgeschlagene und erfolgreiche Übertragungen; langes Drücken für Abarbeiten,
  Anhalten, Erneut versuchen und Leeren. Übertragungen laufen im Hintergrund weiter.
- Unbekannte Zertifikate und Serverschlüssel werden mit Fingerabdruck zur Bestätigung vorgelegt und danach gemerkt.

## Aufbau

```
android/app/src/main/java/de/honeybalecta/datenfaehre/
  Modell.kt       Datentypen, Pfad- und Formatierungshilfen, Schnellverbindung
  Verbindung.kt   FTP/FTPS (Apache Commons Net) und SFTP (JSch), Zertifikats- und Schlüsselprüfung
  Tresor.kt       Serverprofile, Passwörter verschlüsselt
  Werk.kt         Zustand und Abläufe: Verbindung, beide Dateiansichten, Warteschlange
  Dienst.kt       Vordergrunddienst für laufende Übertragungen
  ui/             Oberfläche (Compose)
```

## Bauen

Alles läuft über GitHub Actions; jede Änderung unter `android/` auf `main` baut neu und legt die APK im Release
`v<version.txt>` ab. Signiert wird mit dem festen Schlüssel `android/keystore/release.p12`; das Passwort steht im
Secret `ANDROID_KEYSTORE_PASSWORD`. Log jedes Laufs: Zweig `ci-log-android`.
