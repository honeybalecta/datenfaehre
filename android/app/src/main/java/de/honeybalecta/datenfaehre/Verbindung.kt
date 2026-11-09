package de.honeybalecta.datenfaehre

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import org.apache.commons.net.ProtocolCommandEvent
import org.apache.commons.net.ProtocolCommandListener
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Zertifikat oder Serverschlüssel ist noch nicht bestätigt (oder hat sich geändert). */
class UnbekannterSchluessel(
    val art: String,
    val fingerabdruck: String,
    val details: String,
    val geaendert: Boolean
) : IOException("$art des Servers ist unbekannt")

/** Der Server lehnt explizites TLS ab. */
class KeinTls : IOException("Der Server unterstützt kein FTP über TLS")

interface Verbindung {
    /** Baut die Verbindung auf und liefert das Startverzeichnis. */
    fun verbinden(): String
    fun trennen()
    /** Schließt die Leitung sofort, auch aus einem anderen Thread – für den Abbruch laufender Übertragungen. */
    fun hartTrennen()
    fun lebt(): Boolean
    fun liste(pfad: String): List<Eintrag>
    /** Größe der Datei oder -1, wenn es sie nicht gibt. */
    fun groesse(pfad: String): Long
    /** Legt das Verzeichnis an; ist es schon da, passiert nichts. */
    fun ordnerAnlegen(pfad: String)
    fun loeschen(pfad: String, ordner: Boolean)
    fun umbenennen(von: String, nach: String)
    fun herunterladen(fern: String, ab: Long, aus: OutputStream)
    fun hochladen(ein: InputStream, fern: String, anhaengen: Boolean)
}

fun neueVerbindung(profil: Serverprofil, laut: Boolean, log: (LogArt, String) -> Unit): Verbindung =
    if (profil.protokoll == Protokoll.SFTP) SftpVerbindung(profil, log) else FtpVerbindung(profil, laut, log)

private fun sha256(daten: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(daten)

// ───────────────────────────── FTP und FTP über TLS ─────────────────────────────

/**
 * Prüft das Serverzertifikat: gültig für den Host laut Systemspeicher oder von der Person bestätigt.
 * Alles andere wird abgelehnt und als [UnbekannterSchluessel] zur Bestätigung vorgelegt.
 */
private class Vertrauen(private val host: String, private val bestaetigt: String) : X509TrustManager {
    @Volatile var unbekannt: UnbekannterSchluessel? = null

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val zert = chain.firstOrNull() ?: throw CertificateException("Kein Zertifikat")
        val abdruck = "SHA256:" + sha256(zert.encoded).joinToString(":") { "%02X".format(it) }
        if (abdruck == bestaetigt) return
        val system = systemVertrauen
        if (system != null && passtZumHost(zert, host)) {
            try {
                system.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                // nicht vom System beglaubigt – unten nachfragen
            }
        }
        val details = buildString {
            append("Ausgestellt für: ").append(zert.subjectX500Principal.name).append('\n')
            append("Ausgestellt von: ").append(zert.issuerX500Principal.name).append('\n')
            append("Gültig bis: ").append(datumText(zert.notAfter.time))
        }
        val u = UnbekannterSchluessel("Zertifikat", abdruck, details, bestaetigt.isNotEmpty())
        unbekannt = u
        throw CertificateException(u.message)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        val systemVertrauen: X509TrustManager? by lazy {
            try {
                val fabrik = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                fabrik.init(null as KeyStore?)
                fabrik.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
            } catch (e: Exception) {
                null
            }
        }

        fun passtZumHost(zert: X509Certificate, host: String): Boolean {
            val namen = try {
                zert.subjectAlternativeNames
            } catch (e: Exception) {
                null
            } ?: return false
            val h = host.lowercase()
            for (eintrag in namen) {
                val typ = eintrag.getOrNull(0) as? Int ?: continue
                val wert = (eintrag.getOrNull(1) as? String)?.lowercase() ?: continue
                if (typ == 7 && wert == h) return true
                if (typ == 2) {
                    if (wert == h) return true
                    if (wert.startsWith("*.") && h.contains('.') && h.substringAfter('.') == wert.substring(2)) return true
                }
            }
            return false
        }
    }
}

private class FtpVerbindung(
    private val p: Serverprofil,
    private val laut: Boolean,
    private val log: (LogArt, String) -> Unit
) : Verbindung {
    @Volatile private var aktuell: FTPClient? = null
    private var mlsd = false

    private fun klient(): FTPClient = aktuell ?: throw IOException("Nicht verbunden")

    private fun antwort(c: FTPClient): String =
        c.replyString?.trim()?.ifEmpty { null } ?: "Unbekannter Fehler"

    private fun fehler(c: FTPClient): Nothing = throw IOException(antwort(c))

    override fun verbinden(): String {
        val tls = p.protokoll == Protokoll.FTPS || p.protokoll == Protokoll.FTPS_IMPLIZIT
        val trau = Vertrauen(p.host, p.fingerabdruck)
        val c: FTPClient = if (tls) {
            FTPSClient("TLS", p.protokoll == Protokoll.FTPS_IMPLIZIT).also { it.setTrustManager(trau) }
        } else {
            FTPClient()
        }
        c.setConnectTimeout(15000)
        c.setDefaultTimeout(30000)
        c.setDataTimeout(Duration.ofSeconds(60))
        c.setControlEncoding("UTF-8")
        c.setAutodetectUTF8(true)
        c.setBufferSize(1 shl 16)
        if (laut) {
            c.addProtocolCommandListener(object : ProtocolCommandListener {
                override fun protocolCommandSent(event: ProtocolCommandEvent) {
                    val text = event.message?.trim() ?: return
                    log(LogArt.BEFEHL, if (text.startsWith("PASS", ignoreCase = true)) "PASS ********" else text)
                }

                override fun protocolReplyReceived(event: ProtocolCommandEvent) {
                    val text = event.message?.trim() ?: return
                    for (zeile in text.lines()) if (zeile.isNotBlank()) log(LogArt.ANTWORT, zeile)
                }
            })
        }
        try {
            c.connect(p.host, p.portOderStandard)
        } catch (e: IOException) {
            val code = c.replyCode
            runCatching { c.disconnect() }
            trau.unbekannt?.let { throw it }
            if (p.protokoll == Protokoll.FTPS && e is SSLException && e !is SSLHandshakeException && code >= 500) throw KeinTls()
            throw e
        }
        if (!FTPReply.isPositiveCompletion(c.replyCode)) {
            val text = antwort(c)
            runCatching { c.disconnect() }
            throw IOException(text)
        }
        aktuell = c
        c.setSoTimeout(60000)
        val anonym = p.benutzer.isBlank()
        if (!c.login(if (anonym) "anonymous" else p.benutzer, if (anonym) "anonymous@example.com" else p.passwort)) {
            val text = antwort(c)
            trennen()
            throw IOException("Anmeldung fehlgeschlagen: $text")
        }
        if (c is FTPSClient) {
            log(LogArt.STATUS, "TLS-Verbindung hergestellt.")
            c.execPBSZ(0)
            c.execPROT("P")
        } else {
            log(LogArt.STATUS, "Unverschlüsselter Server – Zugangsdaten und Dateien werden im Klartext übertragen.")
        }
        c.setFileType(FTP.BINARY_FILE_TYPE)
        if (p.passiv) c.enterLocalPassiveMode() else c.enterLocalActiveMode()
        c.setControlKeepAliveTimeout(Duration.ofSeconds(20))
        mlsd = try {
            c.hasFeature("MLSD")
        } catch (e: IOException) {
            false
        }
        return c.printWorkingDirectory() ?: "/"
    }

    override fun trennen() {
        val c = aktuell ?: return
        aktuell = null
        runCatching { if (c.isConnected) c.logout() }
        runCatching { c.disconnect() }
    }

    override fun hartTrennen() {
        runCatching { aktuell?.disconnect() }
    }

    override fun lebt(): Boolean {
        val c = aktuell ?: return false
        return try {
            c.isConnected && c.sendNoOp()
        } catch (e: IOException) {
            false
        }
    }

    override fun liste(pfad: String): List<Eintrag> {
        val c = klient()
        val roh: Array<out FTPFile?> = if (mlsd) {
            c.mlistDir(pfad)
        } else {
            if (!c.changeWorkingDirectory(pfad)) fehler(c)
            c.setListHiddenFiles(true)
            var l = c.listFiles()
            if (!FTPReply.isPositiveCompletion(c.replyCode)) {
                c.setListHiddenFiles(false)
                l = c.listFiles()
            }
            l
        }
        if (!FTPReply.isPositiveCompletion(c.replyCode)) fehler(c)
        val ergebnis = ArrayList<Eintrag>()
        for (f in roh) {
            if (f == null) continue
            val name = f.name ?: continue
            if (name == "." || name == ".." || name.isEmpty() || name.contains('/')) continue
            val zeile = f.rawListing?.lowercase() ?: ""
            if (zeile.contains("type=cdir") || zeile.contains("type=pdir")) continue
            // Verknüpfungen wie Verzeichnisse behandeln: Öffnen klappt, wenn das Ziel eines ist
            val ordner = f.isDirectory || f.isSymbolicLink
            ergebnis.add(Eintrag(name, ordner, if (ordner) 0 else f.size, f.timestamp?.timeInMillis ?: 0))
        }
        return ergebnis
    }

    override fun groesse(pfad: String): Long {
        val c = klient()
        val direkt = c.getSize(pfad)?.trim()?.toLongOrNull()
        if (direkt != null) return direkt
        // 550: Datei gibt es nicht. Andere Antworten: Server kennt SIZE nicht – dann im Verzeichnis nachsehen.
        if (c.replyCode == 550) return -1
        val name = Pfad.name(pfad)
        return try {
            liste(Pfad.eltern(pfad)).firstOrNull { it.name == name && !it.ordner }?.groesse ?: -1
        } catch (e: IOException) {
            -1
        }
    }

    override fun ordnerAnlegen(pfad: String) {
        val c = klient()
        if (c.makeDirectory(pfad)) return
        val text = antwort(c)
        if (c.changeWorkingDirectory(pfad)) return
        throw IOException(text)
    }

    override fun loeschen(pfad: String, ordner: Boolean) {
        val c = klient()
        if (!ordner) {
            if (!c.deleteFile(pfad)) fehler(c)
            return
        }
        val kinder = try {
            liste(pfad)
        } catch (e: IOException) {
            // Verknüpfung auf eine Datei
            if (c.deleteFile(pfad)) return
            throw e
        }
        for (kind in kinder) loeschen(Pfad.verbinde(pfad, kind.name), kind.ordner)
        c.changeWorkingDirectory(Pfad.eltern(pfad))
        if (!c.removeDirectory(pfad) && !c.deleteFile(pfad)) fehler(c)
    }

    override fun umbenennen(von: String, nach: String) {
        val c = klient()
        if (!c.rename(von, nach)) fehler(c)
    }

    override fun herunterladen(fern: String, ab: Long, aus: OutputStream) {
        val c = klient()
        c.setRestartOffset(ab)
        if (!c.retrieveFile(fern, aus)) fehler(c)
    }

    override fun hochladen(ein: InputStream, fern: String, anhaengen: Boolean) {
        val c = klient()
        val ok = if (anhaengen) c.appendFile(fern, ein) else c.storeFile(fern, ein)
        if (!ok) fehler(c)
    }
}

// ───────────────────────────────────── SFTP ─────────────────────────────────────

private class SftpVerbindung(
    private val p: Serverprofil,
    private val log: (LogArt, String) -> Unit
) : Verbindung {
    @Volatile private var sitzung: Session? = null
    @Volatile private var kanal: ChannelSftp? = null

    private fun k(): ChannelSftp = kanal ?: throw IOException("Nicht verbunden")

    private inline fun <T> sftp(block: () -> T): T = try {
        block()
    } catch (e: SftpException) {
        throw IOException(e.message?.ifBlank { null } ?: "SFTP-Fehler ${e.id}")
    }

    private fun abdruck(schluessel: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(sha256(schluessel))

    private fun typ(schluessel: ByteArray): String = try {
        val n = ((schluessel[0].toInt() and 0xff) shl 24) or ((schluessel[1].toInt() and 0xff) shl 16) or
            ((schluessel[2].toInt() and 0xff) shl 8) or (schluessel[3].toInt() and 0xff)
        String(schluessel, 4, n, Charsets.US_ASCII)
    } catch (e: Exception) {
        "unbekannt"
    }

    override fun verbinden(): String {
        val jsch = JSch()
        var gesehen: ByteArray? = null
        jsch.setHostKeyRepository(object : HostKeyRepository {
            override fun check(host: String?, key: ByteArray?): Int {
                if (key == null) return HostKeyRepository.NOT_INCLUDED
                gesehen = key
                return when {
                    abdruck(key) == p.fingerabdruck -> HostKeyRepository.OK
                    p.fingerabdruck.isEmpty() -> HostKeyRepository.NOT_INCLUDED
                    else -> HostKeyRepository.CHANGED
                }
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "datenfaehre"
            override fun getHostKey(): Array<HostKey> = arrayOf()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = arrayOf()
        })
        val s = jsch.getSession(p.benutzer, p.host, p.portOderStandard)
        s.setPassword(p.passwort)
        s.setConfig("StrictHostKeyChecking", "yes")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.setUserInfo(object : UserInfo, UIKeyboardInteractive {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = p.passwort
            override fun promptPassword(message: String?): Boolean = true
            override fun promptPassphrase(message: String?): Boolean = false
            override fun promptYesNo(message: String?): Boolean = false
            override fun showMessage(message: String?) {}
            override fun promptKeyboardInteractive(
                destination: String?, name: String?, instruction: String?,
                prompt: Array<String>?, echo: BooleanArray?
            ): Array<String>? = Array(prompt?.size ?: 0) { p.passwort }
        })
        s.setServerAliveInterval(20000)
        try {
            s.connect(15000)
        } catch (e: JSchException) {
            val key = gesehen
            if (key != null && abdruck(key) != p.fingerabdruck) {
                throw UnbekannterSchluessel(
                    "Serverschlüssel", abdruck(key),
                    "Server: ${p.host}:${p.portOderStandard}\nSchlüsseltyp: ${typ(key)}",
                    p.fingerabdruck.isNotEmpty()
                )
            }
            val text = e.message ?: "Verbindung fehlgeschlagen"
            throw IOException(if (text.contains("Auth fail", ignoreCase = true)) "Anmeldung fehlgeschlagen: Benutzername oder Passwort falsch" else text)
        }
        sitzung = s
        try {
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(15000)
            kanal = ch
            log(LogArt.STATUS, "SSH-Verbindung hergestellt (${s.serverVersion}).")
            return sftp { ch.pwd() } ?: "/"
        } catch (e: JSchException) {
            trennen()
            throw IOException(e.message ?: "SFTP-Kanal ließ sich nicht öffnen")
        }
    }

    override fun trennen() {
        runCatching { kanal?.disconnect() }
        runCatching { sitzung?.disconnect() }
        kanal = null
        sitzung = null
    }

    override fun hartTrennen() {
        runCatching { sitzung?.disconnect() }
    }

    override fun lebt(): Boolean = sitzung?.isConnected == true && kanal?.isConnected == true

    override fun liste(pfad: String): List<Eintrag> = sftp {
        val ch = k()
        val ergebnis = ArrayList<Eintrag>()
        for (roh in ch.ls(pfad)) {
            val e = roh as? ChannelSftp.LsEntry ?: continue
            val name = e.filename ?: continue
            if (name == "." || name == "..") continue
            val a = e.attrs
            var ordner = a.isDir
            if (a.isLink) {
                ordner = try {
                    ch.stat(Pfad.verbinde(pfad, name)).isDir
                } catch (x: SftpException) {
                    false
                }
            }
            ergebnis.add(Eintrag(name, ordner, if (ordner) 0 else a.size, a.mTime.toLong() * 1000, a.permissionsString ?: ""))
        }
        ergebnis
    }

    override fun groesse(pfad: String): Long = try {
        k().stat(pfad).size
    } catch (e: SftpException) {
        -1
    }

    override fun ordnerAnlegen(pfad: String) {
        val ch = k()
        val da = try {
            ch.stat(pfad).isDir
        } catch (e: SftpException) {
            false
        }
        if (!da) sftp { ch.mkdir(pfad) }
    }

    override fun loeschen(pfad: String, ordner: Boolean) {
        val ch = k()
        val echterOrdner = ordner && try {
            ch.lstat(pfad).isDir
        } catch (e: SftpException) {
            true
        }
        if (!echterOrdner) {
            sftp { ch.rm(pfad) }
            return
        }
        for (kind in liste(pfad)) loeschen(Pfad.verbinde(pfad, kind.name), kind.ordner)
        sftp { ch.rmdir(pfad) }
    }

    override fun umbenennen(von: String, nach: String) = sftp { k().rename(von, nach) }

    override fun herunterladen(fern: String, ab: Long, aus: OutputStream) = sftp {
        k().get(fern, aus, null, if (ab > 0) ChannelSftp.RESUME else ChannelSftp.OVERWRITE, ab)
    }

    override fun hochladen(ein: InputStream, fern: String, anhaengen: Boolean) = sftp {
        k().put(ein, fern, null, if (anhaengen) ChannelSftp.APPEND else ChannelSftp.OVERWRITE)
    }
}
