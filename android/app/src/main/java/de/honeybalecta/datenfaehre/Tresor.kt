package de.honeybalecta.datenfaehre

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Serverprofile; Passwörter liegen verschlüsselt mit einem Schlüssel aus dem Android-Schlüsselspeicher. */
object Tresor {
    private const val ALIAS = "datenfaehre_profile"

    private fun schluessel(): SecretKey {
        val speicher = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (speicher.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val erzeuger = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        erzeuger.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return erzeuger.generateKey()
    }

    private fun verschluesseln(klar: String): String {
        if (klar.isEmpty()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, schluessel())
            Base64.encodeToString(c.iv + c.doFinal(klar.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    private fun entschluesseln(geheim: String): String {
        if (geheim.isEmpty()) return ""
        return try {
            val roh = Base64.decode(geheim, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, roh, 0, 12))
            String(c.doFinal(roh, 12, roh.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun laden(context: Context): List<Serverprofil> {
        val text = context.getSharedPreferences("profile", Context.MODE_PRIVATE).getString("liste", null) ?: return emptyList()
        return try {
            val feld = JSONArray(text)
            (0 until feld.length()).map { i ->
                val o = feld.getJSONObject(i)
                Serverprofil(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    protokoll = runCatching { Protokoll.valueOf(o.optString("protokoll")) }.getOrDefault(Protokoll.FTPS),
                    host = o.optString("host"),
                    port = o.optInt("port"),
                    benutzer = o.optString("benutzer"),
                    passwort = entschluesseln(o.optString("passwort")),
                    passiv = o.optBoolean("passiv", true),
                    startverzeichnis = o.optString("startverzeichnis"),
                    lokalesVerzeichnis = o.optString("lokalesVerzeichnis"),
                    fingerabdruck = o.optString("fingerabdruck")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun speichern(context: Context, profile: List<Serverprofil>) {
        val feld = JSONArray()
        for (p in profile) {
            feld.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("protokoll", p.protokoll.name)
                    .put("host", p.host).put("port", p.port).put("benutzer", p.benutzer)
                    .put("passwort", verschluesseln(p.passwort)).put("passiv", p.passiv)
                    .put("startverzeichnis", p.startverzeichnis).put("lokalesVerzeichnis", p.lokalesVerzeichnis)
                    .put("fingerabdruck", p.fingerabdruck)
            )
        }
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putString("liste", feld.toString()).apply()
    }
}
