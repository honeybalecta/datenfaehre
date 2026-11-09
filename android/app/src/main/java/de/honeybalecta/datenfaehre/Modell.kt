package de.honeybalecta.datenfaehre

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Protokoll(val titel: String, val standardPort: Int) {
    FTP("FTP – unverschlüsselt", 21),
    FTPS("FTP über TLS (explizit)", 21),
    FTPS_IMPLIZIT("FTP über TLS (implizit)", 990),
    SFTP("SFTP – SSH-Dateiübertragung", 22)
}

data class Serverprofil(
    val id: String,
    val name: String,
    val protokoll: Protokoll,
    val host: String,
    val port: Int,
    val benutzer: String,
    val passwort: String,
    val passiv: Boolean = true,
    val startverzeichnis: String = "",
    val lokalesVerzeichnis: String = "",
    /** Bestätigter Fingerabdruck von Zertifikat (FTPS) oder Serverschlüssel (SFTP). */
    val fingerabdruck: String = ""
) {
    val portOderStandard: Int get() = if (port in 1..65535) port else protokoll.standardPort
}

data class Eintrag(
    val name: String,
    val ordner: Boolean,
    val groesse: Long,
    val geaendert: Long,
    val rechte: String = ""
)

enum class LogArt(val titel: String) {
    STATUS("Status:"), BEFEHL("Befehl:"), ANTWORT("Antwort:"), FEHLER("Fehler:")
}

data class LogZeile(val art: LogArt, val text: String)

enum class Richtung { HOCH, RUNTER }

enum class Status { WARTET, LAEUFT, FERTIG, FEHLER, UEBERSPRUNGEN }

enum class Vorhanden(val titel: String) {
    UEBERSCHREIBEN("Überschreiben"),
    UEBERSPRINGEN("Überspringen"),
    FORTSETZEN("Übertragung fortsetzen")
}

object Pfad {
    fun verbinde(basis: String, name: String): String =
        if (basis.endsWith("/")) basis + name else "$basis/$name"

    fun eltern(pfad: String): String {
        val s = pfad.trimEnd('/')
        val i = s.lastIndexOf('/')
        return if (i <= 0) "/" else s.substring(0, i)
    }

    fun name(pfad: String): String = pfad.trimEnd('/').substringAfterLast('/')
}

fun groesseText(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val einheiten = arrayOf("KB", "MB", "GB", "TB")
    var wert = bytes.toDouble() / 1024
    var i = 0
    while (wert >= 1024 && i < einheiten.size - 1) {
        wert /= 1024
        i++
    }
    return String.format(Locale.GERMANY, if (wert < 10) "%.1f %s" else "%.0f %s", wert, einheiten[i])
}

fun datumText(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(ms))

/**
 * Schnellverbindung wie am PC: „sftp://host“, „ftp://host“ (unverschlüsselt), „ftps://host“ (implizites TLS),
 * „ftpes://host“ (explizites TLS). Ohne Angabe: Port 22 bedeutet SFTP, sonst FTP über TLS.
 */
fun schnellprofil(hostRoh: String, benutzer: String, passwort: String, portRoh: String): Serverprofil? {
    val roh = hostRoh.trim()
    val treffer = Regex("^([a-zA-Z]+)://(.*)$").find(roh)
    val schema = treffer?.groupValues?.get(1)?.lowercase()
    val rest = (treffer?.groupValues?.get(2) ?: roh).trimEnd('/')
    val doppelpunkt = rest.lastIndexOf(':')
    val portImHost = if (doppelpunkt > 0 && rest.indexOf(':') == doppelpunkt) rest.substring(doppelpunkt + 1).toIntOrNull() else null
    val host = if (portImHost != null) rest.substring(0, doppelpunkt) else rest
    if (host.isBlank()) return null
    val port = portRoh.trim().toIntOrNull() ?: portImHost
    val protokoll = when (schema) {
        null -> if (port == 22) Protokoll.SFTP else Protokoll.FTPS
        "sftp" -> Protokoll.SFTP
        "ftp" -> Protokoll.FTP
        "ftps" -> Protokoll.FTPS_IMPLIZIT
        "ftpes" -> Protokoll.FTPS
        else -> return null
    }
    return Serverprofil(
        id = "", name = host, protokoll = protokoll, host = host,
        port = port ?: protokoll.standardPort, benutzer = benutzer.trim(), passwort = passwort
    )
}
