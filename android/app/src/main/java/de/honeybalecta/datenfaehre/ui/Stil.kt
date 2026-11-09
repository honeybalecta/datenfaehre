package de.honeybalecta.datenfaehre.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farben der klassischen Desktop-Oberfläche. */
object Fz {
    val Flaeche = Color(0xFFF0F0F0)
    val Kopf = Color(0xFFE9E9E9)
    val Rand = Color(0xFF9A9A9A)
    val RandHell = Color(0xFFD0D0D0)
    val Auswahl = Color(0xFF0078D7)
    val Text = Color(0xFF000000)
    val Grau = Color(0xFF6A6A6A)
    val Knopf = Color(0xFFE1E1E1)
    val KnopfRand = Color(0xFFADADAD)
    val LogBefehl = Color(0xFF0000C8)
    val LogAntwort = Color(0xFF007A00)
    val LogFehler = Color(0xFFD40000)
    val Ordner = Color(0xFFF0C040)
    val OrdnerRand = Color(0xFFB8860B)
    val Balken = Color(0xFF06B025)
    val Rot = Color(0xFFD40000)
}

typealias Punkt = Triple<String, Boolean, () -> Unit>

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    farbe: Color = Fz.Text,
    fett: Boolean = false,
    groesse: TextUnit = 13.sp,
    max: Int = 1,
    ausrichtung: TextAlign? = null
) {
    Text(
        text = text, modifier = modifier, color = farbe, fontSize = groesse,
        fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal,
        maxLines = max, overflow = TextOverflow.Ellipsis, textAlign = ausrichtung,
        lineHeight = groesse * 1.25f
    )
}

@Composable
fun Linie() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Fz.RandHell))
}

/** Schlichtes Eingabefeld mit Rahmen. */
@Composable
fun Feld(
    wert: String,
    onWert: (String) -> Unit,
    modifier: Modifier = Modifier,
    passwort: Boolean = false,
    typ: KeyboardType = KeyboardType.Uri,
    onLos: (() -> Unit)? = null
) {
    BasicTextField(
        value = wert,
        onValueChange = onWert,
        singleLine = true,
        textStyle = TextStyle(color = Fz.Text, fontSize = 13.sp),
        visualTransformation = if (passwort) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (passwort) KeyboardType.Password else typ,
            imeAction = if (onLos != null) ImeAction.Go else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onGo = { onLos?.invoke() }),
        cursorBrush = SolidColor(Fz.Text),
        modifier = modifier.height(30.dp).background(Color.White).border(1.dp, Fz.Rand).padding(horizontal = 5.dp),
        decorationBox = { innen ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { innen() }
        }
    )
}

@Composable
fun Knopf(text: String, modifier: Modifier = Modifier, aktiv: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(30.dp).background(Fz.Knopf).border(1.dp, Fz.KnopfRand)
            .clickable(enabled = aktiv, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, farbe = if (aktiv) Fz.Text else Fz.Grau)
    }
}

@Composable
fun Kontextmenue(offen: Boolean, onZu: () -> Unit, punkte: List<Punkt>) {
    DropdownMenu(expanded = offen, onDismissRequest = onZu) {
        for (punkt in punkte) {
            DropdownMenuItem(
                text = { T(punkt.first, farbe = if (punkt.second) Fz.Text else Fz.Grau, groesse = 14.sp) },
                onClick = {
                    onZu()
                    punkt.third()
                },
                enabled = punkt.second
            )
        }
    }
}

enum class Sym { ORDNER, DATEI, SERVER, PROTOKOLL, SCHLANGE, AKTUALISIEREN, START, ABBRECHEN, TRENNEN, VERBINDEN, MEHR, PFEIL_UNTEN }

/** Kleine gezeichnete Symbole im Stil einer Desktop-Werkzeugleiste. */
@Composable
fun Symbol(art: Sym, modifier: Modifier = Modifier.size(20.dp), aktiv: Boolean = true) {
    Canvas(modifier) {
        val u = size.minDimension / 20f
        val dunkel = if (aktiv) Color(0xFF303030) else Color(0xFFA8A8A8)
        val blau = if (aktiv) Color(0xFF1E6BB8) else Color(0xFFA8A8A8)
        val rot = if (aktiv) Fz.Rot else Color(0xFFA8A8A8)
        val gruen = if (aktiv) Color(0xFF138A2E) else Color(0xFFA8A8A8)
        val strich = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (art) {
            Sym.ORDNER -> {
                val form = Path().apply {
                    moveTo(2 * u, 5 * u); lineTo(8 * u, 5 * u); lineTo(10 * u, 7 * u); lineTo(18 * u, 7 * u)
                    lineTo(18 * u, 16 * u); lineTo(2 * u, 16 * u); close()
                }
                drawPath(form, Fz.Ordner)
                drawPath(form, Fz.OrdnerRand, style = Stroke(width = 1f * u))
            }
            Sym.DATEI -> {
                val form = Path().apply {
                    moveTo(5 * u, 2 * u); lineTo(12 * u, 2 * u); lineTo(16 * u, 6 * u); lineTo(16 * u, 18 * u)
                    lineTo(5 * u, 18 * u); close()
                }
                drawPath(form, Color.White)
                drawPath(form, Color(0xFF7A7A7A), style = Stroke(width = 1f * u))
                drawLine(Color(0xFF7A7A7A), p(12f, 2f), p(12f, 6f), 1f * u)
                drawLine(Color(0xFF7A7A7A), p(12f, 6f), p(16f, 6f), 1f * u)
            }
            Sym.SERVER -> {
                for (i in 0..2) {
                    val y = 3f + i * 5f
                    drawRoundRect(blau, p(3f, y), Size(14 * u, 4 * u), CornerRadius(1 * u))
                    drawCircle(Color.White, 0.9f * u, p(6f, y + 2f))
                }
            }
            Sym.PROTOKOLL -> {
                drawRect(dunkel, p(3f, 3f), Size(14 * u, 14 * u), style = Stroke(width = 1.2f * u))
                drawLine(dunkel, p(6f, 7f), p(14f, 7f), 1.2f * u)
                drawLine(blau, p(6f, 10f), p(14f, 10f), 1.2f * u)
                drawLine(gruen, p(6f, 13f), p(12f, 13f), 1.2f * u)
            }
            Sym.SCHLANGE -> {
                drawLine(gruen, p(7f, 16f), p(7f, 5f), 1.8f * u, StrokeCap.Round)
                drawLine(gruen, p(7f, 4f), p(4f, 8f), 1.8f * u, StrokeCap.Round)
                drawLine(gruen, p(7f, 4f), p(10f, 8f), 1.8f * u, StrokeCap.Round)
                drawLine(blau, p(13f, 4f), p(13f, 15f), 1.8f * u, StrokeCap.Round)
                drawLine(blau, p(13f, 16f), p(10f, 12f), 1.8f * u, StrokeCap.Round)
                drawLine(blau, p(13f, 16f), p(16f, 12f), 1.8f * u, StrokeCap.Round)
            }
            Sym.AKTUALISIEREN -> {
                drawArc(gruen, 30f, 280f, false, p(4f, 4f), Size(12 * u, 12 * u), style = strich)
                val spitze = Path().apply {
                    moveTo(13 * u, 2 * u); lineTo(18 * u, 6.5f * u); lineTo(12 * u, 8 * u); close()
                }
                drawPath(spitze, gruen)
            }
            Sym.START -> {
                val form = Path().apply { moveTo(6 * u, 4 * u); lineTo(16 * u, 10 * u); lineTo(6 * u, 16 * u); close() }
                drawPath(form, gruen)
            }
            Sym.ABBRECHEN -> {
                drawCircle(rot, 8 * u, p(10f, 10f))
                drawLine(Color.White, p(7f, 7f), p(13f, 13f), 2f * u, StrokeCap.Round)
                drawLine(Color.White, p(13f, 7f), p(7f, 13f), 2f * u, StrokeCap.Round)
            }
            Sym.TRENNEN -> {
                drawRect(dunkel, p(2f, 7f), Size(5 * u, 6 * u))
                drawRect(dunkel, p(13f, 7f), Size(5 * u, 6 * u))
                drawLine(rot, p(8f, 7f), p(12f, 13f), 1.8f * u, StrokeCap.Round)
                drawLine(rot, p(12f, 7f), p(8f, 13f), 1.8f * u, StrokeCap.Round)
            }
            Sym.VERBINDEN -> {
                drawRect(dunkel, p(2f, 7f), Size(5 * u, 6 * u))
                drawRect(dunkel, p(13f, 7f), Size(5 * u, 6 * u))
                drawLine(gruen, p(7f, 10f), p(13f, 10f), 2f * u)
            }
            Sym.MEHR -> {
                for (i in 0..2) drawCircle(dunkel, 1.6f * u, p(10f, 4f + i * 6f))
            }
            Sym.PFEIL_UNTEN -> {
                val form = Path().apply { moveTo(5 * u, 8 * u); lineTo(15 * u, 8 * u); lineTo(10 * u, 13 * u); close() }
                drawPath(form, dunkel)
            }
        }
    }
}
