# FOX Chords Light
Deine Songs aus Songbook Pro auf dem Light Phone III – Akkorde, Texte und Setlists, offline.

## Was es kann

- **Import vom iPad über WLAN:** Auf dem Telefon „Import“ öffnen, die angezeigte Adresse in Safari
  auf dem iPad aufrufen und das Songbook-Pro-Backup (`.sbpbackup`) auswählen. Kein Kabel, kein Konto,
  keine Cloud. ChordPro-Dateien (`.cho`, `.pro`, …, auch als Zip) gehen ebenfalls.
- **Songs:** alphabetische Liste mit Suche (Titel, Interpret und Liedtext, Umlaute egal).
- **Setlists:** die aus Songbook Pro (in deren Reihenfolge und Transpositionen) und **eigene**:
  In „Setlists“ mit `+` anlegen, mit dem Stift bearbeiten (`+ SONG`, Reihenfolge mit ↑/↓, ✕ entfernt,
  Name ändern, Setlist löschen). Im Song unten geht „ZU SETLIST HINZUFÜGEN“. Eigene Setlists bleiben
  bei einem neuen Backup erhalten. Im Song blätterst du mit ↑/↓ unten zum vorigen/nächsten Song.
- **Songs entfernen:** im Song unten „SONG ENTFERNEN“ (zweimal tippen). Entfernte Songs bleiben auch nach
  einem neuen Backup weg und lassen sich im Hauptmenü unter „ENTFERNT“ wiederherstellen.
- **Songanzeige:** Akkorde über dem Text, saubere Umbrüche zwischen Wörtern, Tabs und Grids in
  Festbreitenschrift (seitlich scrollbar).
- **Transponieren:** `−` / `+` neben der Tonart, Tippen auf die Tonart setzt zurück. Die Wahl bleibt
  pro Song gespeichert. Capo wird berücksichtigt („Capo 2 (A)“ = Griffe in A). Deutsche Schreibweise
  mit H wird erkannt und beibehalten.
- **Schriftgröße** mit `A−` / `A+`. **Auto-Scroll** mit ▶ unten oder Tippen auf „SCROLL“; das Tempo
  (Stufe 1–15) stellst du mit `−` / `+` daneben ein, es bleibt gespeichert.
- **Übe-Aufnahmen:** MP3s (auch M4A, WAV, OGG, FLAC) genauso über den WLAN-Import hochladen. Heißt die
  Datei wie der Song (`Africa.mp3`, `Toto - Africa.mp3`), wird sie automatisch zugeordnet; sonst unter
  dem Song „AUFNAHME ZUORDNEN“. Im Song: `AUDIO` spielt/pausiert, `‹10` springt 10 Sekunden zurück,
  `−` / `+` ändert das Tempo von 50 % bis 125 % ohne die Tonhöhe zu verändern. ▶ unten startet
  Aufnahme und Auto-Scroll zusammen. Songs mit Aufnahme tragen in den Listen ein kleines ♪ im Kreis.
  Unter „AUFNAHMEN“ im Hauptmenü lassen sich Dateien löschen.
- Hell/Dunkel umschaltbar.

## Was aus dem Backup übernommen wird

Songbook Pro speichert ein Backup als Zip mit einer Datei `dataFile.txt` (Versionszeile + JSON).
Das Tool liest daraus: Titel, Interpret, Inhalt (ChordPro), Tonart, Transposition, Capo, Tempo,
Notizen und die Setlists. Gelöschte Songs und PDF-Songs werden übersprungen. Die App-Einstellungen
im Backup (`settings.hive`, enthält z. B. das Sync-Konto) werden **nicht** gelesen.

Ein neues Backup ersetzt Songs und Setlists vollständig. Einzeln importierte ChordPro-Dateien bleiben.
Auf dem Telefon geänderte Transpositionen bleiben erhalten, solange der Song im neuen Backup dieselbe
Transposition hat wie vorher – hast du ihn auf dem iPad umgestellt, gewinnt das iPad.

Das Format ist nicht offiziell dokumentiert, sondern aus einem echten Backup (Songbook Pro 25.1)
abgeleitet. Ändert Songbook Pro es, muss der Import angepasst werden.

## Datenschutz

| Was | Wohin | Wozu |
|---|---|---|
| Backup / ChordPro-Dateien / Aufnahmen | nur vom iPad direkt ins lokale WLAN zum Telefon | Import |

- Das Tool baut **keine** Verbindung ins Internet auf. Die Berechtigung `INTERNET` braucht Android,
  damit das Telefon im WLAN eine Upload-Seite anbieten kann.
- Die Upload-Seite läuft nur, solange der Import-Bildschirm offen ist, und ihre Adresse enthält
  einen zufälligen Code.
- Die Songs liegen im privaten Speicher des Tools auf dem Telefon.

## APK bauen lassen

Bei jedem Push auf `main` baut **Actions → „APK bauen“** die App, führt die Tests aus und legt
unter **Releases** die Datei `FOX-Chords-Light.apk` ab.

Lokal geht es auch mit Android Studio oder:

```
./gradlew :tool:assembleDebug -DlightSdk.toolOnly=true
```

## Aufs Light Phone bringen

**Variante A – Tool Manager über WLAN (ohne Kabel)**

1. Im [Light-Dashboard](https://dashboard.thelightphone.com) den Entwicklermodus einschalten.
2. Telefon und Computer ins selbe WLAN, auf dem Telefon in den Einstellungen den Tool Manager
   starten und die angezeigte Adresse im Browser am Computer öffnen.
3. Unter `Developer` → `Developer Keys` die Datei
   [`docs/sideloading/light_debug_signing_key.txt`](docs/sideloading/light_debug_signing_key.txt)
   hochladen (falls noch nicht geschehen).
4. Unter `Upload Tools` die `FOX-Chords-Light.apk` hochladen. Danach erscheint „Chords“ in der Toolbox.

**Variante B – per Kabel mit adb:** `adb install FOX-Chords-Light.apk`

## Songs vom iPad holen

1. In Songbook Pro auf dem iPad: **Einstellungen → Backup & Sync → „Backup Library“** und die Datei
   in „Dateien“ speichern.
2. Auf dem Light Phone: Chords → **Import**. Das Telefon zeigt eine Adresse wie
   `http://192.168.178.23:8080/k7mq`.
3. Auf dem iPad (im selben WLAN) diese Adresse in Safari öffnen, „Datei auswählen“ tippen und das
   Backup wählen. Nach ein paar Sekunden steht dort z. B. „121 Songs, 1 Setlist importiert“.

## Aufbau

Der Code liegt in [`tool/src/main/kotlin/io/github/lightsongbook`](tool/src/main/kotlin/io/github/lightsongbook):

| Datei | Inhalt |
|---|---|
| `core/Importer.kt` | liest `.sbpbackup` (Zip + JSON) und ChordPro-Dateien |
| `core/ChordPro.kt` | zerlegt Songtexte in Abschnitte, Wörter und Akkorde |
| `core/Chords.kt` | Akkorde erkennen und transponieren (inkl. H/B) |
| `core/Model.kt` | Songs, Setlists, Suche, Tonart/Capo |
| `core/SongRendering.kt` | wendet die Transposition an, hält Akkordzeilen in ihren Spalten |
| `core/LibraryStore.kt` | Ablage im privaten Speicher des Tools |
| `core/UploadServer.kt` | kleine Upload-Seite fürs WLAN (ohne Bibliothek, nur `java.net`) |
| `ui/…Screen.kt` | Oberfläche mit den Bausteinen der Light SDK |

`core/` kommt ohne Android aus; die Tests dazu liegen in `tool/src/test`.

Der Rest des Repos ist die [Light SDK](https://github.com/lightphone/light-sdk) (Stand Oktober 2026),
deren ursprüngliche README in `README.light-sdk.md` liegt.

## Für die offizielle Tool-Bibliothek

Das Tool arbeitet komplett lokal, ohne Konto und ohne Tracking. Offene Frage für eine Einreichung
wäre höchstens das Einlesen des Songbook-Pro-Formats. Für den Eigengebrauch per Sideload spielt das
keine Rolle.
