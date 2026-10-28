package com.jiotv.androidtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jiotv.androidtv.data.model.EpgProgram
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.theme.JtvFonts
import com.jiotv.androidtv.ui.theme.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────── Time ─────────────────────────

@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            kotlinx.coroutines.delay(60_000 - t % 60_000 + 50)
        }
    }
    return now
}

val LocalNow = staticCompositionLocalOf { 0L }

private val hhmm = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.US) }
private val hm12 = ThreadLocal.withInitial { SimpleDateFormat("h:mm", Locale.US) }
private val ampm = ThreadLocal.withInitial { SimpleDateFormat("a", Locale.US) }
private val dayFmt = ThreadLocal.withInitial { SimpleDateFormat("EEE, d MMM", Locale.US) }

fun formatTime(ms: Long): String = hhmm.get()!!.format(Date(ms))
fun formatTimeShort(ms: Long): String = hm12.get()!!.format(Date(ms))
fun formatAmPm(ms: Long): String = ampm.get()!!.format(Date(ms))
fun formatDay(ms: Long): String = dayFmt.get()!!.format(Date(ms))

@Immutable
data class NowNext(val now: EpgProgram?, val next: EpgProgram?, val later: List<EpgProgram>)

fun nowNext(programs: List<EpgProgram>, now: Long): NowNext {
    if (programs.isEmpty()) return NowNext(null, null, emptyList())
    val i = programs.indexOfFirst { it.startMs <= now && now < it.stopMs }
    return if (i >= 0) NowNext(programs[i], programs.getOrNull(i + 1), programs.drop(i + 1).take(4))
    else {
        val fut = programs.filter { it.startMs > now }
        NowNext(null, fut.firstOrNull(), fut.take(4))
    }
}

fun EpgProgram.progress(now: Long): Float {
    val total = (stopMs - startMs).coerceAtLeast(1L)
    return ((now - startMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun EpgProgram.minutesLeft(now: Long): Int = ((stopMs - now) / 60_000L).toInt().coerceAtLeast(0)

// ───────────────────────── Text helpers ─────────────────────────

fun numberStyle(size: TextUnit) = TextStyle(
    fontFamily = JtvFonts.wide,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    fontFeatureSettings = "tnum",
    lineHeight = size * 1.1f,
)

fun textStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = JtvFonts.text,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * 1.3f,
)

@Composable
fun JText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Jtv.colors.tx,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = textStyle(size, weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ───────────────────────── Clickable surface ─────────────────────────

@Composable
fun JtvClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    container: Color = Color.Transparent,
    focusedContainer: Color = Jtv.colors.inv,
    focusedScale: Float = 1.02f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val c = Jtv.colors
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = c.tx,
            focusedContainerColor = focusedContainer,
            focusedContentColor = c.invTx,
            pressedContainerColor = focusedContainer,
            pressedContentColor = c.invTx,
        ),
        border = ClickableSurfaceDefaults.border(),
        glow = ClickableSurfaceDefaults.glow(),
        interactionSource = interactionSource,
    ) { content(focused) }
}

// ───────────────────────── Channel bits ─────────────────────────

private val PlateShape = RoundedCornerShape(6.dp)

@Composable
fun ChannelPlate(logoUrl: String, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { width.roundToPx() to height.roundToPx() }
    val request = remember(logoUrl, px) {
        ImageRequest.Builder(ctx).data(logoUrl).size(px.first, px.second).crossfade(false).build()
    }
    Box(
        modifier.size(width, height).background(Jtv.colors.plate, PlateShape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.84f).fillMaxHeight(0.78f),
        )
    }
}

@Composable
fun NumberBlock(number: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp) {
    Box(
        modifier = modifier
            .background(Jtv.colors.acc, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (number > 0) number.toString() else "–",
            style = numberStyle(fontSize),
            color = Jtv.colors.accTx,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun JtvProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Jtv.colors.acc,
    track: Color = Jtv.colors.line,
    height: Dp = 4.dp,
) {
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
    )
}

@Composable
fun JtvClock(
    now: Long,
    modifier: Modifier = Modifier,
    size: TextUnit = 22.sp,
    color: Color = Jtv.colors.tx,
    dateColor: Color = Jtv.colors.t2
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            "${formatTimeShort(now)} ${formatAmPm(now)}",
            style = textStyle(size, FontWeight.Bold),
            color = color,
            maxLines = 1
        )
        Text(
            formatDay(now),
            style = textStyle(size * 0.65f),
            color = dateColor,
            maxLines = 1
        )
    }
}

@Composable
fun KeyHint(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    keyColor: Color = Jtv.colors.t2,
    color: Color = Jtv.colors.t3
) {
    val text = remember(items, keyColor, color) {
        buildAnnotatedString {
            items.forEachIndexed { i, (k, v) ->
                if (i > 0) withStyle(SpanStyle(color = color)) { append("  ·  ") }
                withStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.Bold)) { append(k) }
                withStyle(SpanStyle(color = color)) { append(" $v") }
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = textStyle(13.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun JtvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    fontSize: TextUnit = 14.sp,
    minHeight: Dp = 44.dp,
    fillMaxWidth: Boolean = false,
) {
    val c = Jtv.colors
    JtvClickable(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        container = if (primary) c.acc else c.s2,
        focusedContainer = c.inv,
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Box(
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = minHeight)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (focused) c.invTx else if (primary) c.accTx else c.tx
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = textStyle(fontSize, FontWeight.Bold),
                    color = if (focused) c.invTx else if (primary) c.accTx else c.tx,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
