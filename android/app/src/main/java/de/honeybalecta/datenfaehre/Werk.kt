package de.honeybalecta.datenfaehre

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

/** Ein Eintrag der Warteschlange. Fortschritt und Status sind beobachtbar. */
class Auftrag(
    val profil: Serverprofil,
    val richtung: Richtung,
    val lokal: String,
    val fern: String,
    val ordner: Boolean,
    groesse: Long
) {
    var groesse by mutableLongStateOf(groesse)
    var uebertragen by mutableLongStateOf(0L)
    var status by mutableStateOf(Status.WARTET)
    var fehler by mutableStateOf("")
}

private class ZaehlEin(private val innen: InputStream, private val melde: (Int) -> Unit) : InputStream() {
    override fun read(): Int {
        val b = innen.read()
        if (b >= 0) melde(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = innen.read(b, off, len)
        if (n > 0) melde(n)
        return n
    }

    override fun close() = innen.close()
}

private class ZaehlAus(private val innen: OutputStream, private val melde: (Int) -> Unit) : OutputStream() {
    override fun write(b: Int) {
        innen.write(b)
        melde(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        innen.write(b, off, len)
        melde(len)
    }

    override fun flush() = innen.flush()
    override fun close() = innen.close()
}

/** Zustand und Abläufe der App: Verbindung, beide Dateiansichten, Warteschlange. */
object Werk {
    lateinit var app: Context
        private set

    private val haupt = Handler(Looper.getMainLooper())
    val bereich = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val log = mutableStateListOf<LogZeile>()
    var profile by mutableStateOf(listOf<Serverprofil>())
    var aktiv by mutableStateOf<Serverprofil?>(null)
    var verbunden by mutableStateOf(false)
    var laufend by mutableIntStateOf(0)

    var lokalPfad by mutableStateOf("")
    var lokal by mutableStateOf(listOf<Eintrag>())
    var fernPfad by mutableStateOf("")
    var fern by mutableStateOf(listOf<Eintrag>())
    val fernVerlauf = mutableStateListOf<String>()

    val schlange = mutableStateListOf<Auftrag>()
    var arbeitet by mutableStateOf(false)
    var tempo by mutableLongStateOf(0L)

    var schluesselFrage by mutableStateOf<UnbekannterSchluessel?>(null)
    var klartextFrage by mutableStateOf(false)
    private var wartend: Serverprofil? = null
    private var wartendSpeichern = false

    /** Schnellverbindung: Zugangsdaten nach erfolgreicher Anmeldung im Servermanager ablegen. */
    var schnellSpeichern by mutableStateOf(true)

    var vorhanden by mutableStateOf(Vorhanden.UEBERSCHREIBEN)
    var versteckt by mutableStateOf(false)
    var zeigeSchnell by mutableStateOf(true)
    var zeigeLog by mutableStateOf(true)
    var zeigeSchlange by mutableStateOf(true)

    private var verbindung: Verbindung? = null
    @Volatile private var transfer: Verbindung? = null
    @Volatile private var abbruch = false
    private val sperre = Mutex()
    private var arbeiter: Job? = null

    private val prefs get() = app.getSharedPreferences("werk", Context.MODE_PRIVATE)
    val speicherWurzel: String get() = Environment.getExternalStorageDirectory().path

    fun start(context: Context) {
        app = context.applicationContext
        profile = Tresor.laden(app)
        lokalPfad = prefs.getString("lokalPfad", null) ?: speicherWurzel
        vorhanden = runCatching { Vorhanden.valueOf(prefs.getString("vorhanden", "") ?: "") }.getOrDefault(Vorhanden.UEBERSCHREIBEN)
        versteckt = prefs.getBoolean("versteckt", false)
        schnellSpeichern = prefs.getBoolean("schnellSpeichern", true)
        zeigeSchnell = prefs.getBoolean("zeigeSchnell", true)
        zeigeLog = prefs.getBoolean("zeigeLog", true)
        zeigeSchlange = prefs.getBoolean("zeigeSchlange", true)
    }

    fun merke() {
        prefs.edit()
            .putString("lokalPfad", lokalPfad)
            .putString("vorhanden", vorhanden.name)
            .putBoolean("versteckt", versteckt)
            .putBoolean("schnellSpeichern", schnellSpeichern)
            .putBoolean("zeigeSchnell", zeigeSchnell)
            .putBoolean("zeigeLog", zeigeLog)
            .putBoolean("zeigeSchlange", zeigeSchlange)
            .apply()
    }

    // ───────────────────────────── Protokoll ─────────────────────────────

    fun meldung(art: LogArt, text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) anhaengen(art, text) else haupt.post { anhaengen(art, text) }
    }

    private fun anhaengen(art: LogArt, text: String) {
        log.add(LogZeile(art, text))
        if (log.size > 600) log.removeRange(0, 100)
    }

    fun logLeeren() = log.clear()

    private fun fehlertext(e: Throwable): String = when (e) {
        is UnknownHostException -> "Server „${e.message ?: ""}“ wurde nicht gefunden"
        is SocketTimeoutException -> "Zeitüberschreitung der Verbindung"
        is ConnectException -> "Verbindung abgelehnt oder Server nicht erreichbar"
        else -> e.message?.trim()?.ifEmpty { null } ?: e.javaClass.simpleName
    }

    // ───────────────────────────── Profile ─────────────────────────────

    fun profileSpeichern(neu: List<Serverprofil>) {
        profile = neu
        Tresor.speichern(app, neu)
    }

    /** Legt die Zugangsdaten einer Schnellverbindung als Eintrag an oder frischt den passenden Eintrag auf. */
    private fun profilMerken(p: Serverprofil): Serverprofil {
        val alt = profile.firstOrNull {
            it.host.equals(p.host, ignoreCase = true) && it.portOderStandard == p.portOderStandard &&
                it.benutzer == p.benutzer && it.protokoll == p.protokoll
        }
        if (alt != null) {
            val neu = alt.copy(passwort = p.passwort, fingerabdruck = p.fingerabdruck)
            if (neu != alt) {
                profileSpeichern(profile.map { if (it.id == alt.id) neu else it })
                meldung(LogArt.STATUS, "Gespeicherte Zugangsdaten für „${neu.name}“ aktualisiert")
            }
            return neu
        }
        val name = if (profile.none { it.name == p.host }) p.host else "${p.benutzer.ifBlank { "anonym" }}@${p.host}"
        val neu = p.copy(id = UUID.randomUUID().toString(), name = name)
        profileSpeichern(profile + neu)
        meldung(LogArt.STATUS, "Zugangsdaten als „$name“ im Servermanager gespeichert")
        return neu
    }

    private fun bekannt(p: Serverprofil): String = prefs.getString("fp_${p.host}:${p.portOderStandard}", "") ?: ""

    // ───────────────────────────── Verbindung ─────────────────────────────

    fun verbinden(roh: Serverprofil, speichern: Boolean = false) {
        if (roh.host.isBlank()) {
            meldung(LogArt.FEHLER, "Kein Server angegeben")
            return
        }
        val profil = if (roh.fingerabdruck.isEmpty()) roh.copy(fingerabdruck = bekannt(roh)) else roh
        if (verbindung != null) trennen()
        bereich.launch {
            laufend++
            meldung(LogArt.STATUS, "Verbinde mit ${profil.host}:${profil.portOderStandard}…")
            val v = neueVerbindung(profil, true) { art, text -> meldung(art, text) }
            try {
                val start = withContext(Dispatchers.IO) { sperre.withLock { v.verbinden() } }
                verbindung = v
                verbunden = true
                fernVerlauf.clear()
                meldung(LogArt.STATUS, "Angemeldet")
                val gemerkt = if (speichern && profil.id.isEmpty()) profilMerken(profil) else profil
                aktiv = gemerkt
                if (gemerkt.lokalesVerzeichnis.isNotBlank()) lokalOeffnen(gemerkt.lokalesVerzeichnis)
                fernOeffnen(gemerkt.startverzeichnis.ifBlank { start })
            } catch (e: UnbekannterSchluessel) {
                wartend = profil
                wartendSpeichern = speichern
                schluesselFrage = e
            } catch (e: KeinTls) {
                wartend = profil
                wartendSpeichern = speichern
                klartextFrage = true
                meldung(LogArt.FEHLER, fehlertext(e))
            } catch (e: Exception) {
                meldung(LogArt.FEHLER, fehlertext(e))
                meldung(LogArt.FEHLER, "Herstellen der Verbindung zum Server fehlgeschlagen")
                withContext(Dispatchers.IO) { runCatching { v.trennen() } }
            } finally {
                laufend--
            }
        }
    }

    fun schluesselBestaetigen(ja: Boolean) {
        val frage = schluesselFrage
        val p = wartend
        schluesselFrage = null
        wartend = null
        if (frage == null || p == null) return
        if (!ja) {
            meldung(LogArt.FEHLER, "${frage.art} nicht bestätigt – Verbindung abgebrochen")
            return
        }
        prefs.edit().putString("fp_${p.host}:${p.portOderStandard}", frage.fingerabdruck).apply()
        val neu = p.copy(fingerabdruck = frage.fingerabdruck)
        if (neu.id.isNotEmpty() && profile.any { it.id == neu.id }) {
            profileSpeichern(profile.map { if (it.id == neu.id) it.copy(fingerabdruck = neu.fingerabdruck) else it })
        }
        verbinden(neu, wartendSpeichern)
    }

    fun klartextBestaetigen(ja: Boolean) {
        val p = wartend
        klartextFrage = false
        wartend = null
        if (p == null) return
        if (!ja) {
            meldung(LogArt.FEHLER, "Herstellen der Verbindung zum Server fehlgeschlagen")
            return
        }
        verbinden(p.copy(protokoll = Protokoll.FTP), wartendSpeichern)
    }

    fun trennen() {
        val v = verbindung ?: return
        verbindung = null
        verbunden = false
        aktiv = null
        fern = emptyList()
        fernPfad = ""
        bereich.launch(Dispatchers.IO) { sperre.withLock { runCatching { v.trennen() } } }
        meldung(LogArt.STATUS, "Verbindung zum Server getrennt")
    }

    fun neuVerbinden() {
        val p = aktiv ?: return
        verbinden(p)
    }

    private fun fernAufgabe(block: suspend (Verbindung) -> Unit) {
        val v = verbindung
        if (v == null) {
            meldung(LogArt.FEHLER, "Nicht mit einem Server verbunden")
            return
        }
        bereich.launch {
            laufend++
            try {
                withContext(Dispatchers.IO) {
                    sperre.withLock {
                        if (!v.lebt()) {
                            meldung(LogArt.STATUS, "Verbindung zum Server getrennt – verbinde neu…")
                            runCatching { v.trennen() }
                            v.verbinden()
                        }
                        block(v)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                meldung(LogArt.FEHLER, fehlertext(e))
            } finally {
                laufend--
            }
        }
    }

    private suspend fun fernLaden(v: Verbindung, pfad: String) {
        val liste = v.liste(pfad)
        withContext(Dispatchers.Main) {
            fernPfad = pfad
            fern = liste
            fernVerlauf.remove(pfad)
            fernVerlauf.add(0, pfad)
            if (fernVerlauf.size > 12) fernVerlauf.removeAt(fernVerlauf.size - 1)
            meldung(LogArt.STATUS, "Anzeigen des Verzeichnisinhalts für „$pfad“ abgeschlossen")
        }
    }

    fun fernOeffnen(pfad: String) {
        val ziel = pfad.trim().ifEmpty { "/" }
        fernAufgabe { v -> fernLaden(v, ziel) }
    }

    fun fernAktualisieren() {
        if (verbunden) fernOeffnen(fernPfad)
    }

    fun fernOrdner(name: String) {
        val basis = fernPfad
        fernAufgabe { v ->
            v.ordnerAnlegen(Pfad.verbinde(basis, name))
            fernLaden(v, basis)
        }
    }

    fun fernUmbenennen(e: Eintrag, neu: String) {
        val basis = fernPfad
        fernAufgabe { v ->
            v.umbenennen(Pfad.verbinde(basis, e.name), Pfad.verbinde(basis, neu))
            fernLaden(v, basis)
        }
    }

    fun fernLoeschen(eintraege: List<Eintrag>) {
        val basis = fernPfad
        fernAufgabe { v ->
            try {
                for (e in eintraege) {
                    meldung(LogArt.STATUS, "Lösche „${Pfad.verbinde(basis, e.name)}“")
                    v.loeschen(Pfad.verbinde(basis, e.name), e.ordner)
                }
            } finally {
                fernLaden(v, basis)
            }
        }
    }

    // ───────────────────────────── Lokale Dateien ─────────────────────────────

    fun lokaleOrte(): List<Pair<String, String>> {
        val wurzel = speicherWurzel
        val orte = mutableListOf("Interner Speicher" to wurzel)
        for ((titel, ordner) in listOf(
            "Downloads" to Environment.DIRECTORY_DOWNLOADS, "Kamera (DCIM)" to Environment.DIRECTORY_DCIM,
            "Videos" to Environment.DIRECTORY_MOVIES, "Bilder" to Environment.DIRECTORY_PICTURES,
            "Dokumente" to Environment.DIRECTORY_DOCUMENTS, "Musik" to Environment.DIRECTORY_MUSIC
        )) {
            val pfad = "$wurzel/$ordner"
            if (File(pfad).isDirectory) orte.add(titel to pfad)
        }
        for (dir in app.getExternalFilesDirs(null)) {
            val pfad = dir?.path ?: continue
            if (pfad.startsWith(wurzel)) continue
            val karte = pfad.substringBefore("/Android/", "")
            if (karte.isNotEmpty()) orte.add("SD-Karte" to karte)
        }
        return orte
    }

    fun lokalOeffnen(pfad: String) {
        val ziel = File(pfad.trim().ifEmpty { speicherWurzel })
        val alles = versteckt
        bereich.launch {
            val liste = withContext(Dispatchers.IO) {
                ziel.listFiles()?.filter { alles || !it.name.startsWith(".") }?.map {
                    val ordner = it.isDirectory
                    Eintrag(it.name, ordner, if (ordner) 0 else it.length(), it.lastModified())
                }
            }
            if (liste == null) {
                meldung(LogArt.FEHLER, "Lokales Verzeichnis „${ziel.path}“ lässt sich nicht lesen")
                return@launch
            }
            lokalPfad = ziel.path
            lokal = liste
            merke()
        }
    }

    fun lokalAktualisieren() = lokalOeffnen(lokalPfad)

    private fun lokalAufgabe(block: () -> Unit) {
        bereich.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                meldung(LogArt.FEHLER, fehlertext(e))
            }
            lokalAktualisieren()
        }
    }

    fun lokalOrdner(name: String) {
        val ziel = File(lokalPfad, name)
        lokalAufgabe { if (!ziel.mkdirs() && !ziel.isDirectory) throw IOException("Verzeichnis „$name“ lässt sich nicht anlegen") }
    }

    fun lokalUmbenennen(e: Eintrag, neu: String) {
        val von = File(lokalPfad, e.name)
        val nach = File(lokalPfad, neu)
        lokalAufgabe {
            if (nach.exists()) throw IOException("„$neu“ gibt es bereits")
            if (!von.renameTo(nach)) throw IOException("„${e.name}“ lässt sich nicht umbenennen")
        }
    }

    fun lokalLoeschen(eintraege: List<Eintrag>) {
        val dateien = eintraege.map { File(lokalPfad, it.name) }
        lokalAufgabe {
            for (f in dateien) if (!f.deleteRecursively()) throw IOException("„${f.name}“ lässt sich nicht löschen")
        }
    }

    // ───────────────────────────── Warteschlange ─────────────────────────────

    fun einreihen(richtung: Richtung, eintraege: List<Eintrag>, sofort: Boolean) {
        val p = aktiv
        if (p == null || !verbunden) {
            meldung(LogArt.FEHLER, "Nicht mit einem Server verbunden")
            return
        }
        for (e in eintraege) {
            schlange.add(
                Auftrag(
                    p, richtung, Pfad.verbinde(lokalPfad, e.name), Pfad.verbinde(fernPfad, e.name),
                    e.ordner, if (e.ordner) -1 else e.groesse
                )
            )
        }
        if (sofort) schlangeStarten()
    }

    fun schlangeEntfernen(vararg status: Status) {
        schlange.removeAll { it.status in status && it.status != Status.LAEUFT }
    }

    fun erneutVersuchen() {
        for (a in schlange) if (a.status == Status.FEHLER) {
            a.status = Status.WARTET
            a.fehler = ""
        }
        schlangeStarten()
    }

    fun abbrechen() {
        val job = arbeiter
        if (job == null || !job.isActive) return
        abbruch = true
        job.cancel()
        val t = transfer
        bereich.launch(Dispatchers.IO) { runCatching { t?.hartTrennen() } }
        meldung(LogArt.STATUS, "Übertragung angehalten")
    }

    fun schlangeStarten() {
        if (arbeiter?.isActive == true) return
        if (schlange.none { it.status == Status.WARTET }) return
        abbruch = false
        arbeitet = true
        Dienst.starten(app)
        arbeiter = bereich.launch {
            var profil: Serverprofil? = null
            try {
                while (true) {
                    val a = schlange.firstOrNull { it.status == Status.WARTET } ?: break
                    a.status = Status.LAEUFT
                    a.fehler = ""
                    try {
                        val kinder = withContext(Dispatchers.IO) {
                            val alt = transfer
                            val leitung: Verbindung
                            if (alt != null && profil == a.profil) {
                                leitung = alt
                            } else {
                                runCatching { alt?.trennen() }
                                transfer = null
                                leitung = neueVerbindung(a.profil, false) { art, text -> meldung(art, text) }
                                leitung.verbinden()
                                transfer = leitung
                                profil = a.profil
                            }
                            bearbeite(leitung, a)
                        }
                        if (kinder.isNotEmpty()) schlange.addAll(schlange.indexOf(a) + 1, kinder)
                        if (a.status == Status.LAEUFT) a.status = Status.FERTIG
                    } catch (e: CancellationException) {
                        a.status = Status.WARTET
                        throw e
                    } catch (e: Exception) {
                        a.status = Status.FEHLER
                        a.fehler = fehlertext(e)
                        meldung(LogArt.FEHLER, "${Pfad.name(a.fern)}: ${a.fehler}")
                        val alt = transfer
                        transfer = null
                        profil = null
                        withContext(Dispatchers.IO) { runCatching { alt?.hartTrennen() } }
                    }
                }
                if (schlange.any { it.status == Status.FEHLER }) {
                    meldung(LogArt.FEHLER, "Warteschlange abgearbeitet – ${schlange.count { it.status == Status.FEHLER }} Übertragung(en) fehlgeschlagen")
                } else {
                    meldung(LogArt.STATUS, "Warteschlange abgearbeitet")
                }
            } finally {
                val alt = transfer
                transfer = null
                arbeitet = false
                tempo = 0
                Dienst.stoppen()
                withContext(NonCancellable + Dispatchers.IO) { runCatching { alt?.trennen() } }
                lokalAktualisieren()
                fernAktualisieren()
            }
        }
    }

    private fun zaehler(a: Auftrag, start: Long): (Int) -> Unit {
        var summe = start
        var zuletzt = SystemClock.elapsedRealtime()
        var seitdem = 0L
        a.uebertragen = start
        return { n ->
            if (abbruch) throw IOException("Abgebrochen")
            summe += n
            seitdem += n
            val jetzt = SystemClock.elapsedRealtime()
            if (jetzt - zuletzt >= 400) {
                a.uebertragen = summe
                tempo = seitdem * 1000 / (jetzt - zuletzt)
                zuletzt = jetzt
                seitdem = 0
            }
        }
    }

    private fun ueberspringe(strom: InputStream, bytes: Long) {
        var rest = bytes
        while (rest > 0) {
            val n = strom.skip(rest)
            if (n <= 0) throw IOException("Datei lässt sich nicht fortsetzen")
            rest -= n
        }
    }

    /** Überträgt eine Datei oder löst ein Verzeichnis in einzelne Aufträge auf (Rückgabe). */
    private fun bearbeite(v: Verbindung, a: Auftrag): List<Auftrag> {
        val beginn = SystemClock.elapsedRealtime()
        val regel = vorhanden
        var ab = 0L
        if (a.richtung == Richtung.HOCH) {
            val datei = File(a.lokal)
            if (datei.isDirectory) {
                v.ordnerAnlegen(a.fern)
                return (datei.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }.map {
                    val ordner = it.isDirectory
                    Auftrag(a.profil, Richtung.HOCH, it.path, Pfad.verbinde(a.fern, it.name), ordner, if (ordner) -1 else it.length())
                }
            }
            if (!datei.isFile) throw IOException("Lokale Datei nicht gefunden")
            val gesamt = datei.length()
            a.groesse = gesamt
            val da = v.groesse(a.fern)
            if (da >= 0) {
                if (regel == Vorhanden.UEBERSPRINGEN || (regel == Vorhanden.FORTSETZEN && da == gesamt)) {
                    a.status = Status.UEBERSPRUNGEN
                    return emptyList()
                }
                if (regel == Vorhanden.FORTSETZEN && da < gesamt) ab = da
            }
            meldung(LogArt.STATUS, "Starte Upload von ${a.lokal}")
            FileInputStream(datei).use { roh ->
                if (ab > 0) ueberspringe(roh, ab)
                v.hochladen(ZaehlEin(roh, zaehler(a, ab)), a.fern, ab > 0)
            }
        } else {
            val ziel = File(a.lokal)
            if (a.ordner) {
                if (!ziel.mkdirs() && !ziel.isDirectory) throw IOException("Lokales Verzeichnis lässt sich nicht anlegen")
                return v.liste(a.fern).sortedBy { it.name.lowercase() }.map {
                    Auftrag(a.profil, Richtung.RUNTER, Pfad.verbinde(a.lokal, it.name), Pfad.verbinde(a.fern, it.name), it.ordner, if (it.ordner) -1 else it.groesse)
                }
            }
            ziel.parentFile?.mkdirs()
            if (ziel.exists()) {
                val da = ziel.length()
                if (regel == Vorhanden.UEBERSPRINGEN || (regel == Vorhanden.FORTSETZEN && a.groesse >= 0 && da == a.groesse)) {
                    a.status = Status.UEBERSPRUNGEN
                    return emptyList()
                }
                if (regel == Vorhanden.FORTSETZEN && da < a.groesse) ab = da
            }
            meldung(LogArt.STATUS, "Starte Download von ${a.fern}")
            FileOutputStream(ziel, ab > 0).use { roh ->
                v.herunterladen(a.fern, ab, ZaehlAus(roh, zaehler(a, ab)))
            }
            a.groesse = ziel.length()
            MediaScannerConnection.scanFile(app, arrayOf(ziel.path), null, null)
        }
        a.uebertragen = a.groesse
        val sekunden = ((SystemClock.elapsedRealtime() - beginn + 999) / 1000).coerceAtLeast(1)
        meldung(LogArt.STATUS, "Dateiübertragung erfolgreich, ${groesseText(a.groesse - ab)} in $sekunden Sekunde${if (sekunden == 1L) "" else "n"} übertragen")
        return emptyList()
    }
}
