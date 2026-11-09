@file:OptIn(ExperimentalFoundationApi::class)

package de.honeybalecta.datenfaehre.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.honeybalecta.datenfaehre.Auftrag
import de.honeybalecta.datenfaehre.LogArt
import de.honeybalecta.datenfaehre.Pfad
import de.honeybalecta.datenfaehre.Richtung
import de.honeybalecta.datenfaehre.Status
import de.honeybalecta.datenfaehre.Werk
import de.honeybalecta.datenfaehre.groesseText
import de.honeybalecta.datenfaehre.schnellprofil

@Composable
fun Oberflaeche(zugriff: Boolean, onZugriff: () -> Unit) {
    var manager by remember { mutableStateOf(false) }
    var einstellungen by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Fz.Auswahl, background = Fz.Flaeche, surface = Fz.Flaeche, onSurface = Fz.Text
        )
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Fz.Flaeche).windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val breit = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize()) {
                Werkzeugleiste(onManager = { manager = true }, onEinstellungen = { einstellungen = true })
                Linie()
                if (Werk.zeigeSchnell) {
                    Schnellverbindung(breit)
                    Linie()
                }
                if (Werk.zeigeLog) Nachrichten(Modifier.fillMaxWidth().height(if (breit) 64.dp else 78.dp))
                if (breit) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        LokalFenster(zugriff, onZugriff, Modifier.weight(1f).fillMaxHeight())
                        FernFenster(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    LokalFenster(zugriff, onZugriff, Modifier.weight(1f).fillMaxWidth())
                    FernFenster(Modifier.weight(1f).fillMaxWidth())
                }
                if (Werk.zeigeSchlange) Warteschlange(Modifier.fillMaxWidth().height(if (breit) 104.dp else 128.dp))
                Linie()
                Statuszeile()
            }
        }

        if (manager) Servermanager(onZu = { manager = false })
        if (einstellungen) Einstellungen(onZu = { einstellungen = false })

        val frage = Werk.schluesselFrage
        if (frage != null) {
            val warnung = if (frage.geaendert) {
                "ACHTUNG: ${frage.art} des Servers hat sich seit der letzten Verbindung geändert. " +
                    "Das kann auf einen Angriff hindeuten.\n\n"
            } else {
                "${frage.art} dieses Servers ist unbekannt. Bitte prüfen, ob es der richtige Server ist.\n\n"
            }
            Bestaetigung(
                titel = if (frage.art == "Zertifikat") "Unbekanntes Zertifikat" else "Unbekannter Serverschlüssel",
                text = warnung + frage.details + "\n\nFingerabdruck:\n" + frage.fingerabdruck +
                    "\n\nDiesem Server vertrauen und die Verbindung herstellen?",
                ja = "Vertrauen",
                onJa = { Werk.schluesselBestaetigen(true) },
                onZu = { if (Werk.schluesselFrage != null) Werk.schluesselBestaetigen(false) }
            )
        }
        if (Werk.klartextFrage) {
            Bestaetigung(
                titel = "Unsichere FTP-Verbindung",
                text = "Dieser Server unterstützt kein FTP über TLS.\n\nBei einer unverschlüsselten Verbindung " +
                    "werden Passwort und Dateien im Klartext übertragen und lassen sich mitlesen.\n\n" +
                    "Trotzdem unverschlüsselt verbinden?",
                ja = "Unverschlüsselt verbinden",
                onJa = { Werk.klartextBestaetigen(true) },
                onZu = { if (Werk.klartextFrage) Werk.klartextBestaetigen(false) }
            )
        }
    }
}

// ───────────────────────────── Werkzeugleiste ─────────────────────────────

@Composable
private fun Werkzeug(art: Sym, beschreibung: String, aktiv: Boolean = true, gedrueckt: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 1.dp).size(38.dp)
            .background(if (gedrueckt) Color(0xFFCCE4F7) else Color.Transparent)
            .border(1.dp, if (gedrueckt) Color(0xFF99C9EF) else Color.Transparent)
            .clickable(enabled = aktiv, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center
    ) { Symbol(art, Modifier.size(22.dp), aktiv) }
}

@Composable
private fun Trennstrich() {
    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(26.dp).background(Fz.RandHell))
}

@Composable
private fun Werkzeugleiste(onManager: () -> Unit, onEinstellungen: () -> Unit) {
    var mehr by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Werkzeug(Sym.SERVER, "Servermanager öffnen", onClick = onManager)
        Trennstrich()
        Werkzeug(Sym.PROTOKOLL, "Nachrichtenprotokoll ein- oder ausblenden", gedrueckt = Werk.zeigeLog) {
            Werk.zeigeLog = !Werk.zeigeLog
            Werk.merke()
        }
        Werkzeug(Sym.SCHLANGE, "Warteschlange ein- oder ausblenden", gedrueckt = Werk.zeigeSchlange) {
            Werk.zeigeSchlange = !Werk.zeigeSchlange
            Werk.merke()
        }
        Trennstrich()
        Werkzeug(Sym.AKTUALISIEREN, "Dateilisten aktualisieren") {
            Werk.lokalAktualisieren()
            Werk.fernAktualisieren()
        }
        Werkzeug(Sym.START, "Warteschlange abarbeiten", aktiv = !Werk.arbeitet, gedrueckt = Werk.arbeitet) { Werk.schlangeStarten() }
        Werkzeug(Sym.ABBRECHEN, "Laufende Übertragung abbrechen", aktiv = Werk.arbeitet) { Werk.abbrechen() }
        Trennstrich()
        Werkzeug(Sym.TRENNEN, "Verbindung zum Server trennen", aktiv = Werk.verbunden) { Werk.trennen() }
        Werkzeug(Sym.VERBINDEN, "Verbindung zum Server neu herstellen", aktiv = Werk.aktiv != null) { Werk.neuVerbinden() }
        Spacer(Modifier.weight(1f))
        Box {
            Werkzeug(Sym.MEHR, "Weitere Funktionen") { mehr = true }
            Kontextmenue(
                mehr, { mehr = false },
                listOf(
                    Punkt(if (Werk.zeigeSchnell) "Schnellverbindung ausblenden" else "Schnellverbindung einblenden", true) {
                        Werk.zeigeSchnell = !Werk.zeigeSchnell
                        Werk.merke()
                    },
                    Punkt("Einstellungen", true) { onEinstellungen() }
                )
            )
        }
    }
}

// ───────────────────────────── Schnellverbindung ─────────────────────────────

@Composable
private fun Schnellverbindung(breit: Boolean) {
    var host by remember { mutableStateOf("") }
    var benutzer by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    val los = {
        val p = schnellprofil(host, benutzer, passwort, port)
        if (p == null) {
            Werk.meldung(LogArt.FEHLER, "Serveradresse fehlt oder ist ungültig (erlaubt: host, sftp://, ftp://, ftps://, ftpes://)")
        } else {
            Werk.verbinden(p)
        }
    }
    if (breit) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Server:", Modifier.padding(end = 4.dp))
            Feld(host, { host = it }, Modifier.weight(1.4f))
            T("Benutzername:", Modifier.padding(start = 8.dp, end = 4.dp))
            Feld(benutzer, { benutzer = it }, Modifier.weight(1f), typ = KeyboardType.Email)
            T("Passwort:", Modifier.padding(start = 8.dp, end = 4.dp))
            Feld(passwort, { passwort = it }, Modifier.weight(1f), passwort = true)
            T("Port:", Modifier.padding(start = 8.dp, end = 4.dp))
            Feld(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(56.dp), typ = KeyboardType.Number, onLos = los)
            Spacer(Modifier.width(8.dp))
            Knopf("Verbinden", onClick = los)
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Server:", Modifier.width(52.dp))
                Feld(host, { host = it }, Modifier.weight(1.2f))
                T("Benutzer:", Modifier.padding(start = 6.dp, end = 4.dp))
                Feld(benutzer, { benutzer = it }, Modifier.weight(1f), typ = KeyboardType.Email)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Passwort:", Modifier.width(52.dp), groesse = 12.sp)
                Feld(passwort, { passwort = it }, Modifier.weight(1f), passwort = true)
                T("Port:", Modifier.padding(start = 6.dp, end = 4.dp))
                Feld(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(52.dp), typ = KeyboardType.Number, onLos = los)
                Spacer(Modifier.width(6.dp))
                Knopf("Verbinden", onClick = los)
            }
        }
    }
}

// ───────────────────────────── Nachrichtenprotokoll ─────────────────────────────

@Composable
private fun Nachrichten(modifier: Modifier) {
    val zustand = rememberLazyListState()
    val zeilen = Werk.log
    val ablage = LocalClipboardManager.current
    var menue by remember { mutableStateOf(false) }
    LaunchedEffect(zeilen.size) {
        if (zeilen.isNotEmpty()) zustand.scrollToItem(zeilen.size - 1)
    }
    Box(modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
        LazyColumn(
            Modifier.fillMaxSize().background(Color.White).border(1.dp, Fz.Rand)
                .combinedClickable(onClick = {}, onLongClick = { menue = true }).padding(horizontal = 4.dp),
            state = zustand
        ) {
            items(zeilen) { z ->
                val farbe = when (z.art) {
                    LogArt.STATUS -> Fz.Text
                    LogArt.BEFEHL -> Fz.LogBefehl
                    LogArt.ANTWORT -> Fz.LogAntwort
                    LogArt.FEHLER -> Fz.LogFehler
                }
                Row {
                    T(z.art.titel, Modifier.width(56.dp), farbe = farbe, groesse = 12.sp)
                    T(z.text, Modifier.weight(1f), farbe = farbe, groesse = 12.sp, max = 4)
                }
            }
        }
        Kontextmenue(
            menue, { menue = false },
            listOf(
                Punkt("In die Zwischenablage kopieren", zeilen.isNotEmpty()) {
                    ablage.setText(AnnotatedString(zeilen.joinToString("\n") { "${it.art.titel}\t${it.text}" }))
                },
                Punkt("Alles löschen", zeilen.isNotEmpty()) { Werk.logLeeren() }
            )
        )
    }
}

// ───────────────────────────── Dateifenster ─────────────────────────────

@Composable
private fun LokalFenster(zugriff: Boolean, onZugriff: () -> Unit, modifier: Modifier) {
    if (!zugriff) {
        Column(
            modifier.padding(4.dp).background(Color.White).border(1.dp, Fz.Rand).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            T(
                "Für die lokale Dateiansicht braucht die App Zugriff auf die Dateien dieses Geräts.",
                max = 4, ausrichtung = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Knopf("Zugriff auf Dateien erlauben", onClick = onZugriff)
        }
        return
    }
    val orte = remember { Werk.lokaleOrte() }
    Dateifenster(
        titel = "Lokal:",
        pfad = Werk.lokalPfad,
        eintraege = Werk.lokal,
        orte = orte,
        aktionTitel = "Hochladen",
        bereit = true,
        aktionBereit = Werk.verbunden,
        leerText = "Leere Verzeichnisliste",
        onPfad = { Werk.lokalOeffnen(it) },
        onAktion = { liste, sofort -> Werk.einreihen(Richtung.HOCH, liste, sofort) },
        onOrdner = { Werk.lokalOrdner(it) },
        onUmbenennen = { e, neu -> Werk.lokalUmbenennen(e, neu) },
        onLoeschen = { Werk.lokalLoeschen(it) },
        onAktualisieren = { Werk.lokalAktualisieren() },
        modifier = modifier
    )
}

@Composable
private fun FernFenster(modifier: Modifier) {
    val orte = listOf("/" to "/") + Werk.fernVerlauf.filter { it != "/" }.map { it to it }
    Dateifenster(
        titel = "Server:",
        pfad = Werk.fernPfad,
        eintraege = Werk.fern,
        orte = orte,
        aktionTitel = "Herunterladen",
        bereit = Werk.verbunden,
        aktionBereit = Werk.verbunden,
        leerText = if (Werk.verbunden) "Leere Verzeichnisliste" else "Mit keinem Server verbunden",
        onPfad = { Werk.fernOeffnen(it) },
        onAktion = { liste, sofort -> Werk.einreihen(Richtung.RUNTER, liste, sofort) },
        onOrdner = { Werk.fernOrdner(it) },
        onUmbenennen = { e, neu -> Werk.fernUmbenennen(e, neu) },
        onLoeschen = { Werk.fernLoeschen(it) },
        onAktualisieren = { Werk.fernAktualisieren() },
        modifier = modifier
    )
}

// ───────────────────────────── Warteschlange ─────────────────────────────

@Composable
private fun Reiter(text: String, aktiv: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(24.dp).background(if (aktiv) Color.White else Fz.Kopf).border(1.dp, Fz.Rand)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { T(text, groesse = 12.sp, fett = aktiv) }
}

@Composable
private fun Warteschlange(modifier: Modifier) {
    var reiter by remember { mutableIntStateOf(0) }
    var menue by remember { mutableStateOf(false) }
    val alle = Werk.schlange
    val offen = alle.filter { it.status == Status.WARTET || it.status == Status.LAEUFT }
    val fehl = alle.filter { it.status == Status.FEHLER }
    val fertig = alle.filter { it.status == Status.FERTIG || it.status == Status.UEBERSPRUNGEN }
    val sicht = when (reiter) {
        0 -> offen
        1 -> fehl
        else -> fertig
    }
    Column(modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(Fz.Kopf).border(1.dp, Fz.RandHell),
            verticalAlignment = Alignment.CenterVertically
        ) {
            T("Lokale Datei", Modifier.weight(1f).padding(start = 6.dp), groesse = 12.sp)
            T("Richtung", Modifier.width(54.dp), groesse = 12.sp, ausrichtung = TextAlign.Center)
            if (reiter == 1) {
                T("Grund", Modifier.weight(1f), groesse = 12.sp)
            } else {
                T("Größe", Modifier.width(64.dp), groesse = 12.sp, ausrichtung = TextAlign.End)
                T("Status", Modifier.width(92.dp).padding(start = 8.dp), groesse = 12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().background(Color.White).border(1.dp, Fz.Rand)
                    .combinedClickable(onClick = {}, onLongClick = { menue = true })
            ) {
                items(sicht) { a -> Auftragszeile(a, reiter == 1) }
            }
            Kontextmenue(
                menue, { menue = false },
                listOf(
                    Punkt("Warteschlange abarbeiten", !Werk.arbeitet && offen.isNotEmpty()) { Werk.schlangeStarten() },
                    Punkt("Anhalten", Werk.arbeitet) { Werk.abbrechen() },
                    Punkt("Fehlgeschlagene erneut versuchen", fehl.isNotEmpty()) {
                        reiter = 0
                        Werk.erneutVersuchen()
                    },
                    Punkt("Abgeschlossene entfernen", fertig.isNotEmpty() || fehl.isNotEmpty()) {
                        Werk.schlangeEntfernen(Status.FERTIG, Status.UEBERSPRUNGEN, Status.FEHLER)
                    },
                    Punkt("Alle entfernen", alle.isNotEmpty()) {
                        Werk.abbrechen()
                        Werk.schlangeEntfernen(Status.WARTET, Status.FERTIG, Status.UEBERSPRUNGEN, Status.FEHLER)
                    }
                )
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Reiter("Zu übertragende Dateien" + if (offen.isNotEmpty()) " (${offen.size})" else "", reiter == 0) { reiter = 0 }
            Reiter("Fehlgeschlagene Übertragungen" + if (fehl.isNotEmpty()) " (${fehl.size})" else "", reiter == 1) { reiter = 1 }
            Reiter("Erfolgreiche Übertragungen" + if (fertig.isNotEmpty()) " (${fertig.size})" else "", reiter == 2) { reiter = 2 }
        }
    }
}

@Composable
private fun Auftragszeile(a: Auftrag, mitGrund: Boolean) {
    Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(Pfad.name(a.lokal), Modifier.weight(1f), groesse = 12.sp)
        T(if (a.richtung == Richtung.HOCH) "-->>" else "<<--", Modifier.width(54.dp), groesse = 12.sp, ausrichtung = TextAlign.Center)
        if (mitGrund) {
            T(a.fehler, Modifier.weight(1f), farbe = Fz.LogFehler, groesse = 12.sp)
        } else {
            Fortschritt(a)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Fortschritt(a: Auftrag) {
    run {
        T(if (a.ordner) "" else groesseText(a.groesse), Modifier.width(64.dp), groesse = 12.sp, ausrichtung = TextAlign.End)
        Box(Modifier.width(92.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            when (a.status) {
                Status.LAEUFT -> {
                    val anteil = if (a.groesse > 0) (a.uebertragen.toFloat() / a.groesse).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth().height(16.dp).background(Color(0xFFE6E6E6)).border(1.dp, Fz.KnopfRand)) {
                        Box(Modifier.fillMaxWidth(anteil).fillMaxHeight().background(Fz.Balken))
                        T(
                            if (a.ordner) "Verzeichnis" else "${(anteil * 100).toInt()} %",
                            Modifier.fillMaxWidth(), groesse = 11.sp, ausrichtung = TextAlign.Center
                        )
                    }
                }
                Status.WARTET -> T("Wartet", groesse = 12.sp, farbe = Fz.Grau)
                Status.FERTIG -> T("Übertragen", groesse = 12.sp)
                Status.UEBERSPRUNGEN -> T("Übersprungen", groesse = 12.sp, farbe = Fz.Grau)
                Status.FEHLER -> T("Fehler", groesse = 12.sp, farbe = Fz.LogFehler)
            }
        }
    }
}

// ───────────────────────────── Statuszeile ─────────────────────────────

@Composable
private fun Statuszeile() {
    val p = Werk.aktiv
    val links = when {
        Werk.laufend > 0 -> "Bitte warten…"
        Werk.verbunden && p != null -> "Verbunden mit ${p.host}"
        else -> "Nicht verbunden"
    }
    val offen = Werk.schlange.filter { it.status == Status.WARTET || it.status == Status.LAEUFT }
    val rest = offen.sumOf { (it.groesse - it.uebertragen).coerceAtLeast(0L) }
    val rechts = when {
        offen.isEmpty() -> "Warteschlange: leer"
        Werk.arbeitet && Werk.tempo > 0 -> "Warteschlange: ${groesseText(rest)} · ${groesseText(Werk.tempo)}/s"
        else -> "Warteschlange: ${groesseText(rest)}"
    }
    Row(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(links, Modifier.weight(1f), groesse = 12.sp)
        T(rechts, groesse = 12.sp)
    }
}
