package de.honeybalecta.datenfaehre.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.honeybalecta.datenfaehre.Protokoll
import de.honeybalecta.datenfaehre.Serverprofil
import de.honeybalecta.datenfaehre.Vorhanden
import de.honeybalecta.datenfaehre.Werk
import java.util.UUID

@Composable
private fun Zeile(beschriftung: String, inhalt: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        T(beschriftung, Modifier.width(112.dp))
        Box(Modifier.weight(1f)) { inhalt() }
    }
}

@Composable
fun Servermanager(onZu: () -> Unit) {
    var liste by remember { mutableStateOf(Werk.profile) }
    var gewaehlt by remember { mutableStateOf(Werk.profile.firstOrNull()?.id ?: "") }
    var protokollOffen by remember { mutableStateOf(false) }
    val p = liste.firstOrNull { it.id == gewaehlt }

    fun aendere(neu: Serverprofil) {
        liste = liste.map { if (it.id == neu.id) neu else it }
    }

    Dialog(onDismissRequest = onZu, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f).background(Fz.Flaeche).border(1.dp, Fz.Rand).padding(10.dp)
        ) {
            T("Servermanager", fett = true, groesse = 16.sp)
            Spacer(Modifier.height(6.dp))
            T("Eintrag auswählen:")
            LazyColumn(Modifier.fillMaxWidth().height(116.dp).background(Color.White).border(1.dp, Fz.Rand)) {
                items(liste) { eintrag ->
                    val markiert = eintrag.id == gewaehlt
                    Row(
                        Modifier.fillMaxWidth().height(30.dp).background(if (markiert) Fz.Auswahl else Color.Transparent)
                            .clickable { gewaehlt = eintrag.id }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Symbol(Sym.SERVER, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        T(eintrag.name.ifBlank { eintrag.host.ifBlank { "Neuer Server" } }, farbe = if (markiert) Color.White else Fz.Text)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knopf("Neuer Server") {
                    val neu = Serverprofil(
                        id = UUID.randomUUID().toString(), name = "Neuer Server", protokoll = Protokoll.FTPS,
                        host = "", port = 21, benutzer = "", passwort = ""
                    )
                    liste = liste + neu
                    gewaehlt = neu.id
                }
                Knopf("Löschen", aktiv = p != null) {
                    liste = liste.filter { it.id != gewaehlt }
                    gewaehlt = liste.firstOrNull()?.id ?: ""
                }
            }
            Spacer(Modifier.height(8.dp))
            Linie()
            if (p == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    T("Über „Neuer Server“ einen Eintrag anlegen.", farbe = Fz.Grau, max = 2)
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    Zeile("Name:") { Feld(p.name, { aendere(p.copy(name = it)) }, Modifier.fillMaxWidth(), typ = KeyboardType.Text) }
                    Zeile("Protokoll:") {
                        Box {
                            Row(
                                Modifier.fillMaxWidth().height(30.dp).background(Color.White).border(1.dp, Fz.Rand)
                                    .clickable { protokollOffen = true }.padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                T(p.protokoll.titel, Modifier.weight(1f))
                                Symbol(Sym.PFEIL_UNTEN, Modifier.size(14.dp))
                            }
                            Kontextmenue(
                                protokollOffen, { protokollOffen = false },
                                Protokoll.entries.map { art ->
                                    Punkt(art.titel, true) {
                                        val port = if (p.port == p.protokoll.standardPort || p.port == 0) art.standardPort else p.port
                                        aendere(p.copy(protokoll = art, port = port, fingerabdruck = ""))
                                    }
                                }
                            )
                        }
                    }
                    Zeile("Server:") { Feld(p.host, { aendere(p.copy(host = it.trim(), fingerabdruck = "")) }, Modifier.fillMaxWidth()) }
                    Zeile("Port:") {
                        Feld(
                            if (p.port == 0) "" else p.port.toString(),
                            { aendere(p.copy(port = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0)) },
                            Modifier.width(80.dp), typ = KeyboardType.Number
                        )
                    }
                    Zeile("Benutzer:") { Feld(p.benutzer, { aendere(p.copy(benutzer = it)) }, Modifier.fillMaxWidth(), typ = KeyboardType.Email) }
                    Zeile("Passwort:") { Feld(p.passwort, { aendere(p.copy(passwort = it)) }, Modifier.fillMaxWidth(), passwort = true) }
                    if (p.protokoll != Protokoll.SFTP) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = p.passiv, onCheckedChange = { aendere(p.copy(passiv = it)) })
                            T("Passiver Übertragungsmodus (empfohlen)")
                        }
                    }
                    Zeile("Lokales Verz.:") { Feld(p.lokalesVerzeichnis, { aendere(p.copy(lokalesVerzeichnis = it)) }, Modifier.fillMaxWidth()) }
                    Zeile("Server-Verz.:") { Feld(p.startverzeichnis, { aendere(p.copy(startverzeichnis = it)) }, Modifier.fillMaxWidth()) }
                    if (p.protokoll == Protokoll.FTP) {
                        T(
                            "Hinweis: Bei unverschlüsseltem FTP werden Passwort und Dateien im Klartext übertragen.",
                            Modifier.padding(top = 6.dp), farbe = Fz.Rot, groesse = 12.sp, max = 3
                        )
                    }
                }
            }
            Linie()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                Knopf("Verbinden", aktiv = p != null && p.host.isNotBlank()) {
                    Werk.profileSpeichern(liste)
                    if (p != null) Werk.verbinden(p)
                    onZu()
                }
                Knopf("OK") {
                    Werk.profileSpeichern(liste)
                    onZu()
                }
                Knopf("Abbrechen", onClick = onZu)
            }
        }
    }
}

@Composable
fun Einstellungen(onZu: () -> Unit) {
    Dialog(onDismissRequest = onZu, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.92f).background(Fz.Flaeche).border(1.dp, Fz.Rand).padding(12.dp)) {
            T("Einstellungen", fett = true, groesse = 16.sp)
            Spacer(Modifier.height(10.dp))
            T("Wenn die Zieldatei bereits existiert:", fett = true)
            for (regel in Vorhanden.entries) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Werk.vorhanden = regel
                        Werk.merke()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = Werk.vorhanden == regel, onClick = {
                        Werk.vorhanden = regel
                        Werk.merke()
                    })
                    T(regel.titel)
                }
            }
            Spacer(Modifier.height(6.dp))
            T("Lokale Dateiansicht:", fett = true)
            Row(
                Modifier.fillMaxWidth().clickable {
                    Werk.versteckt = !Werk.versteckt
                    Werk.merke()
                    Werk.lokalAktualisieren()
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = Werk.versteckt, onCheckedChange = {
                    Werk.versteckt = it
                    Werk.merke()
                    Werk.lokalAktualisieren()
                })
                T("Versteckte Dateien anzeigen")
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Knopf("Schließen", onClick = onZu)
            }
        }
    }
}
