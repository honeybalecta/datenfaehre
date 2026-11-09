@file:OptIn(ExperimentalFoundationApi::class)

package de.honeybalecta.datenfaehre.ui

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.honeybalecta.datenfaehre.Eintrag
import de.honeybalecta.datenfaehre.Pfad
import de.honeybalecta.datenfaehre.datumText
import de.honeybalecta.datenfaehre.groesseText

/**
 * Eine Dateiansicht (lokal oder Server): Pfadzeile, Spaltenkopf, Liste, Statuszeile.
 * Antippen wählt aus, Doppeltippen öffnet oder überträgt, langes Drücken öffnet das Kontextmenü.
 */
@Composable
fun Dateifenster(
    titel: String,
    pfad: String,
    eintraege: List<Eintrag>,
    orte: List<Pair<String, String>>,
    aktionTitel: String,
    bereit: Boolean,
    aktionBereit: Boolean,
    leerText: String,
    onPfad: (String) -> Unit,
    onAktion: (List<Eintrag>, Boolean) -> Unit,
    onOrdner: (String) -> Unit,
    onUmbenennen: (Eintrag, String) -> Unit,
    onLoeschen: (List<Eintrag>) -> Unit,
    onAktualisieren: () -> Unit,
    modifier: Modifier = Modifier
) {
    var auswahl by remember(pfad) { mutableStateOf(setOf<String>()) }
    var spalte by remember { mutableIntStateOf(0) }
    var absteigend by remember { mutableStateOf(false) }
    var menue by remember { mutableIntStateOf(KEIN_MENUE) }
    var dialog by remember { mutableStateOf("") }
    var orteOffen by remember { mutableStateOf(false) }
    var pfadText by remember(pfad) { mutableStateOf(pfad) }
    var tippName by remember { mutableStateOf("") }
    var tippZeit by remember { mutableLongStateOf(0L) }

    val sortiert = remember(eintraege, spalte, absteigend) {
        val innen: Comparator<Eintrag> = when (spalte) {
            1 -> compareBy<Eintrag> { it.groesse }
            2 -> compareBy<Eintrag> { it.geaendert }
            else -> compareBy<Eintrag> { it.name.lowercase() }
        }
        eintraege.sortedWith(compareByDescending<Eintrag> { it.ordner }.then(if (absteigend) innen.reversed() else innen))
    }
    val gewaehlt = sortiert.filter { it.name in auswahl }

    fun punkte(): List<Punkt> = listOf(
        Punkt(aktionTitel, aktionBereit && gewaehlt.isNotEmpty()) { onAktion(gewaehlt, true) },
        Punkt("Zur Warteschlange hinzufügen", aktionBereit && gewaehlt.isNotEmpty()) { onAktion(gewaehlt, false) },
        Punkt("Verzeichnis erstellen", bereit) { dialog = "ordner" },
        Punkt("Umbenennen", bereit && gewaehlt.size == 1) { dialog = "name" },
        Punkt("Löschen", bereit && gewaehlt.isNotEmpty()) { dialog = "loeschen" },
        Punkt("Aktualisieren", bereit) { onAktualisieren() },
        Punkt("Alles auswählen", sortiert.isNotEmpty()) { auswahl = sortiert.map { it.name }.toSet() },
        Punkt("Auswahl aufheben", auswahl.isNotEmpty()) { auswahl = emptySet() }
    )

    Column(modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
        // Pfadzeile
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            T(titel, Modifier.width(52.dp))
            Feld(pfadText, { pfadText = it }, Modifier.weight(1f), onLos = { onPfad(pfadText) })
            Box {
                Box(
                    Modifier.size(30.dp).background(Fz.Knopf).border(1.dp, Fz.KnopfRand).clickable { orteOffen = true },
                    contentAlignment = Alignment.Center
                ) { Symbol(Sym.PFEIL_UNTEN, Modifier.size(16.dp)) }
                Kontextmenue(orteOffen, { orteOffen = false }, orte.map { ort -> Punkt(ort.first, true) { onPfad(ort.second) } })
            }
        }
        Spacer(Modifier.height(2.dp))
        // Spaltenkopf
        Row(
            Modifier.fillMaxWidth().height(24.dp).background(Fz.Kopf).border(1.dp, Fz.RandHell),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pfeil = if (absteigend) " ▼" else " ▲"
            fun waehle(nr: Int) {
                if (spalte == nr) absteigend = !absteigend else {
                    spalte = nr
                    absteigend = false
                }
            }
            T("Dateiname" + if (spalte == 0) pfeil else "", Modifier.weight(1f).clickable { waehle(0) }.padding(start = 6.dp), groesse = 12.sp)
            T("Dateigröße" + if (spalte == 1) pfeil else "", Modifier.width(78.dp).clickable { waehle(1) }, groesse = 12.sp, ausrichtung = TextAlign.End)
            T("Zuletzt geändert" + if (spalte == 2) pfeil else "", Modifier.width(118.dp).clickable { waehle(2) }.padding(start = 10.dp), groesse = 12.sp)
        }
        // Liste
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color.White).border(1.dp, Fz.Rand)) {
            item {
                Box {
                    Row(
                        Modifier.fillMaxWidth().height(32.dp)
                            .combinedClickable(
                                onClick = {
                                    val jetzt = SystemClock.uptimeMillis()
                                    if (tippName == ".." && jetzt - tippZeit < DOPPELTIPP) {
                                        tippName = ""
                                        if (bereit) onPfad(Pfad.eltern(pfad))
                                    } else {
                                        tippName = ".."
                                        tippZeit = jetzt
                                    }
                                },
                                onLongClick = { menue = MENUE_OBEN }
                            )
                            .padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Symbol(Sym.ORDNER, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        T("..")
                    }
                    Kontextmenue(menue == MENUE_OBEN, { menue = KEIN_MENUE }, punkte())
                }
            }
            if (sortiert.isEmpty() && leerText.isNotEmpty()) {
                item { T(leerText, Modifier.fillMaxWidth().padding(12.dp), farbe = Fz.Grau, max = 3, ausrichtung = TextAlign.Center) }
            }
            itemsIndexed(sortiert) { index, e ->
                val markiert = e.name in auswahl
                val farbe = if (markiert) Color.White else Fz.Text
                Box {
                    Row(
                        Modifier.fillMaxWidth().height(32.dp)
                            .background(if (markiert) Fz.Auswahl else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    val jetzt = SystemClock.uptimeMillis()
                                    if (tippName == e.name && jetzt - tippZeit < DOPPELTIPP) {
                                        tippName = ""
                                        if (e.ordner) {
                                            onPfad(Pfad.verbinde(pfad, e.name))
                                        } else if (aktionBereit) {
                                            auswahl = auswahl + e.name
                                            onAktion(listOf(e), true)
                                        }
                                    } else {
                                        tippName = e.name
                                        tippZeit = jetzt
                                        auswahl = if (markiert) auswahl - e.name else auswahl + e.name
                                    }
                                },
                                onLongClick = {
                                    if (!markiert) auswahl = auswahl + e.name
                                    menue = index
                                }
                            )
                            .padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Symbol(if (e.ordner) Sym.ORDNER else Sym.DATEI, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        T(e.name, Modifier.weight(1f), farbe = farbe)
                        T(if (e.ordner) "" else groesseText(e.groesse), Modifier.width(72.dp), farbe = farbe, groesse = 12.sp, ausrichtung = TextAlign.End)
                        T(datumText(e.geaendert), Modifier.width(112.dp).padding(start = 10.dp), farbe = farbe, groesse = 12.sp)
                    }
                    Kontextmenue(menue == index, { menue = KEIN_MENUE }, punkte())
                }
            }
        }
        // Statuszeile
        val quelle = if (gewaehlt.isEmpty()) sortiert else gewaehlt
        val dateien = quelle.count { !it.ordner }
        val ordner = quelle.count { it.ordner }
        val summe = quelle.filter { !it.ordner }.sumOf { it.groesse }
        val statustext = buildString {
            if (gewaehlt.isNotEmpty()) append("Ausgewählt: ")
            append(dateien).append(if (dateien == 1) " Datei" else " Dateien")
            append(" und ").append(ordner).append(if (ordner == 1) " Verzeichnis" else " Verzeichnisse")
            append(". Gesamtgröße: ").append(groesseText(summe))
        }
        T(statustext, Modifier.fillMaxWidth().height(20.dp).padding(start = 4.dp, top = 2.dp), farbe = Fz.Grau, groesse = 12.sp)
    }

    when (dialog) {
        "ordner" -> Eingabe(
            "Verzeichnis erstellen", "Bitte den Namen des neuen Verzeichnisses eingeben:", "Neues Verzeichnis",
            onOk = { onOrdner(it) }, onZu = { dialog = "" }
        )
        "name" -> {
            val e = gewaehlt.firstOrNull()
            if (e == null) dialog = "" else Eingabe(
                "Umbenennen", "Neuer Name für „${e.name}“:", e.name,
                onOk = { if (it != e.name) onUmbenennen(e, it) }, onZu = { dialog = "" }
            )
        }
        "loeschen" -> {
            val text = if (gewaehlt.size == 1) "„${gewaehlt[0].name}“ wirklich löschen?" else "${gewaehlt.size} Objekte wirklich löschen?"
            Bestaetigung(
                "Löschen bestätigen", text + if (gewaehlt.any { it.ordner }) "\nVerzeichnisse werden mit ihrem gesamten Inhalt gelöscht." else "",
                "Löschen", onJa = { onLoeschen(gewaehlt) }, onZu = { dialog = "" }
            )
        }
    }
}

private const val KEIN_MENUE = -2
private const val MENUE_OBEN = -1
private const val DOPPELTIPP = 450L

@Composable
fun Eingabe(titel: String, frage: String, start: String, onOk: (String) -> Unit, onZu: () -> Unit) {
    var text by remember { mutableStateOf(start) }
    val gueltig = text.isNotBlank() && !text.contains('/')
    AlertDialog(
        onDismissRequest = onZu,
        shape = RectangleShape,
        containerColor = Fz.Flaeche,
        title = { Text(titel, fontSize = 16.sp, color = Fz.Text) },
        text = {
            Column {
                T(frage, max = 4)
                Spacer(Modifier.height(8.dp))
                Feld(text, { text = it }, Modifier.fillMaxWidth(), typ = KeyboardType.Text)
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(text.trim()); onZu() }, enabled = gueltig) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onZu) { Text("Abbrechen") } }
    )
}

@Composable
fun Bestaetigung(titel: String, text: String, ja: String, onJa: () -> Unit, onZu: () -> Unit, nein: String = "Abbrechen") {
    AlertDialog(
        onDismissRequest = onZu,
        shape = RectangleShape,
        containerColor = Fz.Flaeche,
        title = { Text(titel, fontSize = 16.sp, color = Fz.Text) },
        text = { T(text, max = 14) },
        confirmButton = { TextButton(onClick = { onJa(); onZu() }) { Text(ja) } },
        dismissButton = { TextButton(onClick = onZu) { Text(nein) } }
    )
}
