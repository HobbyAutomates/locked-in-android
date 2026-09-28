package com.sohum.bandlog.ui.profile

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Covers

/*
 * v2.16 profile covers. #01 (plates light) is today's WeightPlatesCover, untouched; the other 33
 * are the CoverPresets board's own SVG (CoverSvgs.kt), drawn by the small renderer below: rect,
 * circle, ellipse, line, path (PathParser), text, <g transform>, linear / radial gradients and the
 * one blur (soft chalk clouds, drawn as a radial fade). viewBox 390 × 250, "xMidYMid slice".
 */

// ---------------------------------------------------------------- tiny SVG model + parser

internal sealed class SvgNode {
    class Group(val transform: String?, val children: List<SvgNode>) : SvgNode()
    class Shape(val tag: String, val a: Map<String, String>, val text: String? = null) : SvgNode()
}

internal class Gradient(val radial: Boolean, val a: Map<String, String>, val stops: List<Pair<Float, Color>>)

internal class SvgDoc(val nodes: List<SvgNode>, val gradients: Map<String, Gradient>, val blurred: Set<String>)

private val TAG = Regex("""<(/?)([a-zA-Z]+)((?:\s+[a-zA-Z0-9:-]+="[^"]*")*)\s*(/?)>|([^<]+)""")
private val ATTR = Regex("""([a-zA-Z0-9:-]+)="([^"]*)"""")

private fun parseColor(v: String?, opacity: Float = 1f): Color? {
    if (v == null || v == "none") return null
    val c = when {
        v.startsWith("#") && v.length == 7 -> Color(("FF" + v.substring(1)).toLong(16))
        v.startsWith("#") && v.length == 4 -> Color(("FF" + v.substring(1).map { "$it$it" }.joinToString("")).toLong(16))
        v == "white" -> Color.White
        v == "black" -> Color.Black
        else -> return null
    }
    return c.copy(alpha = c.alpha * opacity)
}

internal fun parseSvg(src: String): SvgDoc {
    val gradients = HashMap<String, Gradient>()
    val blurred = HashSet<String>()
    val stack = ArrayList<Pair<String?, MutableList<SvgNode>>>() // (transform, children) for <g>
    val root = mutableListOf<SvgNode>()
    stack.add(null to root)
    var grad: Triple<Boolean, Map<String, String>, MutableList<Pair<Float, Color>>>? = null
    var pendingText: Map<String, String>? = null
    var inDefs = 0
    var filterId: String? = null
    for (m in TAG.findAll(src)) {
        val text = m.groupValues[5]
        if (text.isNotEmpty()) { pendingText?.let { a -> stack.last().second.add(SvgNode.Shape("text", a, text.trim())); pendingText = null }; continue }
        val closing = m.groupValues[1] == "/"
        val tag = m.groupValues[2]
        val attrs = ATTR.findAll(m.groupValues[3]).associate { it.groupValues[1] to it.groupValues[2] }
        val selfClosing = m.groupValues[4] == "/"
        when {
            closing && tag == "g" -> { val (t, kids) = stack.removeAt(stack.size - 1); stack.last().second.add(SvgNode.Group(t, kids)) }
            closing && (tag == "linearGradient" || tag == "radialGradient") -> { grad?.let { (r, a, s) -> gradients[a["id"] ?: ""] = Gradient(r, a, s) }; grad = null }
            closing && tag == "defs" -> inDefs--
            closing && tag == "filter" -> filterId = null
            closing -> {}
            tag == "defs" -> if (!selfClosing) inDefs++
            tag == "g" -> if (!selfClosing) stack.add(attrs["transform"] to mutableListOf())
            tag == "linearGradient" || tag == "radialGradient" -> grad = Triple(tag == "radialGradient", attrs, mutableListOf())
            tag == "stop" -> grad?.third?.add((attrs["offset"]?.toFloatOrNull() ?: 0f) to (parseColor(attrs["stop-color"], attrs["stop-opacity"]?.toFloatOrNull() ?: 1f) ?: Color.Black))
            tag == "filter" -> { filterId = attrs["id"]; filterId?.let { blurred.add(it) } }
            tag == "feGaussianBlur" -> {}
            tag == "text" -> pendingText = attrs
            inDefs > 0 -> {}
            else -> stack.last().second.add(SvgNode.Shape(tag, attrs))
        }
    }
    return SvgDoc(root, gradients, blurred)
}

private val docs = HashMap<Int, SvgDoc>()
private fun docFor(index: Int): SvgDoc = docs.getOrPut(index) { parseSvg(COVER_SVGS[index]) }
/** v2.18 D11: the gold edition = the dark cover with every colour mapped onto the gold ramp. */
private val goldDocs = HashMap<Int, SvgDoc>()
private fun goldDocFor(index: Int): SvgDoc = goldDocs.getOrPut(index) { parseSvg(com.sohum.bandlog.util.Packs.goldifySvg(COVER_SVGS[index])) }

// ---------------------------------------------------------------- rendering

private fun Map<String, String>.f(k: String, d: Float = 0f) = this[k]?.toFloatOrNull() ?: d
private val URL = Regex("""url\(#([^)]+)\)""")
private val TRANSFORM = Regex("""(translate|rotate|scale)\(([^)]*)\)""")

private fun DrawScope.applyTransform(t: String?, body: DrawScope.() -> Unit) {
    if (t.isNullOrBlank()) { body(); return }
    withTransform({
        for (m in TRANSFORM.findAll(t)) {
            val n = m.groupValues[2].split(Regex("[ ,]+")).filter { it.isNotBlank() }.map { it.toFloat() }
            when (m.groupValues[1]) {
                "translate" -> translate(n.getOrElse(0) { 0f }, n.getOrElse(1) { 0f })
                "rotate" -> rotate(n[0], if (n.size >= 3) Offset(n[1], n[2]) else Offset.Zero)
                "scale" -> scale(n[0], n.getOrElse(1) { n[0] }, Offset.Zero)
            }
        }
    }, body)
}

private fun brushFor(doc: SvgDoc, paint: String?, opacity: Float, bounds: Rect): Brush? {
    if (paint == null || paint == "none") return null
    val id = URL.find(paint)?.groupValues?.get(1)
    if (id != null) {
        val g = doc.gradients[id] ?: return null
        val stops = g.stops.map { (o, c) -> o to c.copy(alpha = c.alpha * opacity) }.toTypedArray()
        if (stops.isEmpty()) return null
        return if (g.radial) {
            val cx = bounds.left + bounds.width * g.a.f("cx", 0.5f)
            val cy = bounds.top + bounds.height * g.a.f("cy", 0.5f)
            Brush.radialGradient(*stops, center = Offset(cx, cy), radius = maxOf(bounds.width, bounds.height) * g.a.f("r", 0.5f))
        } else {
            Brush.linearGradient(
                *stops,
                start = Offset(bounds.left + bounds.width * g.a.f("x1", 0f), bounds.top + bounds.height * g.a.f("y1", 0f)),
                end = Offset(bounds.left + bounds.width * g.a.f("x2", 1f), bounds.top + bounds.height * g.a.f("y2", 0f)),
            )
        }
    }
    return parseColor(paint, opacity)?.let { androidx.compose.ui.graphics.SolidColor(it) }
}

private fun strokeOf(a: Map<String, String>): Stroke? {
    if (a["stroke"] == null || a["stroke"] == "none") return null
    val dash = a["stroke-dasharray"]?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }
    return Stroke(
        width = a.f("stroke-width", 1f),
        cap = when (a["stroke-linecap"]) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
        join = when (a["stroke-linejoin"]) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
        pathEffect = dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) },
    )
}

private val pathCache = HashMap<String, Path>()

private fun DrawScope.drawNode(doc: SvgDoc, n: SvgNode) {
    when (n) {
        is SvgNode.Group -> applyTransform(n.transform) { n.children.forEach { drawNode(doc, it) } }
        is SvgNode.Shape -> applyTransform(n.a["transform"]) { drawShape(doc, n) }
    }
}

private fun DrawScope.drawShape(doc: SvgDoc, n: SvgNode.Shape) {
    val a = n.a
    val op = a.f("opacity", 1f)
    val fillAttr = a["fill"] ?: if (n.tag == "line") "none" else "#000000"
    val stroke = strokeOf(a)
    fun paintBoth(bounds: Rect, fill: (Brush) -> Unit, line: (Brush, Stroke) -> Unit) {
        brushFor(doc, fillAttr, op, bounds)?.let(fill)
        if (stroke != null) brushFor(doc, a["stroke"], op, bounds)?.let { line(it, stroke) }
    }
    when (n.tag) {
        "rect" -> {
            val x = a.f("x"); val y = a.f("y"); val w = a.f("width"); val h = a.f("height"); val rx = a.f("rx")
            val r = Rect(x, y, x + w, y + h)
            val cr = androidx.compose.ui.geometry.CornerRadius(rx)
            paintBoth(r, { drawRoundRect(it, r.topLeft, r.size, cr) }, { b, s -> drawRoundRect(b, r.topLeft, r.size, cr, style = s) })
        }
        "circle" -> {
            val c = Offset(a.f("cx"), a.f("cy")); val rad = a.f("r")
            val blurId = a["filter"]?.let { URL.find(it)?.groupValues?.get(1) }
            if (blurId != null && blurId in doc.blurred) {
                // feGaussianBlur(10) on a flat circle: a radial fade from the colour to clear.
                val col = parseColor(fillAttr, op) ?: return
                val outer = rad + 20f
                drawCircle(Brush.radialGradient(0f to col, (rad - 12f).coerceAtLeast(0f) / outer to col, 1f to col.copy(alpha = 0f), center = c, radius = outer), outer, c)
                return
            }
            val r = Rect(c, rad)
            paintBoth(r, { drawCircle(it, rad, c) }, { b, s -> drawCircle(b, rad, c, style = s) })
        }
        "ellipse" -> {
            val cx = a.f("cx"); val cy = a.f("cy"); val rx = a.f("rx"); val ry = a.f("ry")
            val r = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
            paintBoth(r, { drawOval(it, r.topLeft, r.size) }, { b, s -> drawOval(b, r.topLeft, r.size, style = s) })
        }
        "line" -> {
            val p1 = Offset(a.f("x1"), a.f("y1")); val p2 = Offset(a.f("x2"), a.f("y2"))
            val s = stroke ?: return
            val b = brushFor(doc, a["stroke"], op, Rect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x) + 1f, maxOf(p1.y, p2.y) + 1f)) ?: return
            drawLine(b, p1, p2, strokeWidth = s.width, cap = s.cap, pathEffect = s.pathEffect)
        }
        "path" -> {
            val d = a["d"] ?: return
            val path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }
            val r = path.getBounds()
            paintBoth(r, { drawPath(path, it, style = Fill) }, { b, s -> drawPath(path, b, style = s) })
        }
        "text" -> {
            val col = parseColor(a["fill"] ?: "#000000", op) ?: return
            val t = n.text ?: return
            drawIntoCanvas { c ->
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = col.toArgb(); textSize = a.f("font-size", 16f)
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if ((a["font-weight"]?.toIntOrNull() ?: 400) >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
                c.nativeCanvas.drawText(t, a.f("x"), a.f("y"), p)
            }
        }
    }
}

/** Draws board cover #[n] (1-based) filling this scope, "xMidYMid slice". */
private fun DrawScope.drawSvgCover(n: Int, gold: Boolean = false) {
    val i = (n - 1).coerceIn(0, COVER_SVGS.lastIndex)
    val doc = if (gold) goldDocFor(i) else docFor(i)
    val s = maxOf(size.width / 390f, size.height / 250f)
    val dx = (size.width - 390f * s) / 2f
    val dy = (size.height - 250f * s) / 2f
    clipRect {
        withTransform({ translate(dx, dy); scale(s, s, Offset.Zero) }) { doc.nodes.forEach { drawNode(doc, it) } }
    }
}

/** The profile cover for [id]: #01 is the existing plates cover (unchanged), the rest the board's vectors. */
@Composable
fun CoverView(id: String, modifier: Modifier = Modifier) {
    com.sohum.bandlog.util.Packs.goldCover(id)?.let { g -> Canvas(modifier.semantics { contentDescription = "Cover: ${g.name} · gold" }) { drawSvgCover(g.darkIndex + 1, gold = true) }; return }
    val preset = Covers.of(id)
    if (preset.id == Covers.DEFAULT) { WeightPlatesCover(modifier); return }
    Canvas(modifier.semantics { contentDescription = "Cover: ${preset.label}" }) { drawSvgCover(preset.n) }
}

/** A thumbnail for the picker (always the board art, #01 included, so the grid reads as the board). */
@Composable
private fun CoverThumb(preset: Covers.Preset, modifier: Modifier) {
    if (preset.id == Covers.DEFAULT) WeightPlatesCover(modifier) else Canvas(modifier) { drawSvgCover(preset.n) }
}

// ---------------------------------------------------------------- choice storage

/**
 * Where the choice lives: `profiles.cover_preset` once schema_v40 is applied, and always on the
 * device (the fallback until then, and a fast first paint after).
 */
object CoverStore {
    private const val PREFS = "cover_v216"
    fun local(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("preset", null)
    fun saveLocal(ctx: Context, id: String) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("preset", id).apply() }
}

// ---------------------------------------------------------------- the picker (board CoverPicker)

@Composable
fun CoverPickerSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = palette
    var cat by remember { mutableStateOf<Covers.Category?>(null) }
    val ember = Color(0xFFFF5B1F)
    BottomSheet(
        title = "Choose a cover", subtitle = "Your photo and name stay the same. Only the background changes.", onDismiss = onDismiss,
        trailing = {
            Text("Done", fontSize = 14.sp, fontWeight = FontWeight(600), color = Color(0xFFFF8B5E), modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onDismiss).padding(horizontal = 6.dp, vertical = 14.dp))
        },
    ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf<Covers.Category?>(null) + Covers.Category.entries).forEach { c ->
                val sel = c == cat
                Box(
                    Modifier.height(32.dp).background(if (sel) p.ink else p.card2, CircleShape).clip(CircleShape).clickable { cat = c }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(c?.label ?: "All ${Covers.ALL.size}", fontSize = 13.sp, fontWeight = FontWeight(600), color = if (sel) p.bg else p.muted, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Covers.inCategory(cat).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val sel = preset.id == current
                    Column(Modifier.weight(1f).clickable(onClickLabel = "Use ${preset.label}") { onPick(preset.id) }) {
                        Box {
                            val shape = RoundedCornerShape(14.dp)
                            CoverThumb(
                                preset,
                                Modifier.fillMaxWidth().height(62.dp)
                                    .then(if (sel) Modifier.shadow(10.dp, shape, ambientColor = ember, spotColor = ember) else Modifier)
                                    .clip(shape)
                                    .border(if (sel) 2.5.dp else 1.dp, if (sel) ember else p.hair, shape),
                            )
                            if (sel) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(ember, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(CheckIcon, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Text(
                            preset.label + if (sel) " · current" else "", fontSize = 10.5.sp, fontWeight = FontWeight(500), color = if (sel) p.ink else p.muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        com.sohum.bandlog.ui.social.GoldCoverRow(current, onPick) // v2.18 D11 (once the pack is unlocked)
    }
}

/** The small "Cover" pill on the cover (board CoverPicker). */
@Composable
internal fun CoverPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).clip(CircleShape)
            .clickable(onClickLabel = "Change cover", onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(com.sohum.bandlog.ui.components.PencilIcon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text("Cover", fontSize = 13.sp, fontWeight = FontWeight(600), color = Color.White)
    }
}
