package com.bookscrolling.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt

val ALPHABET: List<Char> = ('A'..'Z').toList() + '#'

/** Index letter for a title: first letter without accents, or '#' for digits and symbols. */
fun indexLetter(title: String, ignoreArticles: Boolean = false): Char {
    val key = if (ignoreArticles) sortTitle(title) else title
    val c = Normalizer.normalize(key.trimStart { !it.isLetterOrDigit() }.take(1), Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Title used for alphabetical order of books: "The Time Machine" sorts under T for "Time". */
fun sortTitle(title: String): String {
    val t = title.trimStart { !it.isLetterOrDigit() }
    for (article in listOf("the ", "a ", "an ")) {
        if (t.length > article.length && t.startsWith(article, ignoreCase = true)) return t.substring(article.length)
    }
    return t
}

/**
 * A–Z index along the right edge. Touch or drag over it to jump to the first item for that
 * letter; letters with no items stop at the next letter that has some.
 */
@Composable
fun BoxScope.AlphabetIndex(
    listState: LazyListState,
    letters: List<Char>,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 15) return
    val firstIndex = remember(letters) {
        HashMap<Char, Int>().apply { letters.forEachIndexed { i, c -> putIfAbsent(c, i) } }
    }
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf<Char?>(null) }

    LaunchedEffect(listState, firstIndex) {
        snapshotFlow { active }.collectLatest { letter ->
            if (letter == null) return@collectLatest
            val target = ALPHABET.drop(ALPHABET.indexOf(letter)).firstNotNullOfOrNull { firstIndex[it] }
                ?: (letters.size - 1)
            listState.scrollToItem(target)
        }
    }

    Column(
        modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun letterAt(y: Float): Char =
                        ALPHABET[((y / size.height) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.size - 1)]
                    fun select(y: Float) {
                        val c = letterAt(y)
                        if (c != active) {
                            active = c
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    down.consume()
                    select(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        select(change.position.y)
                    }
                    active = null
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (c in ALPHABET) {
            val present = c in firstIndex
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    c.toString(),
                    fontSize = 10.sp,
                    fontWeight = if (c == active) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        c == active -> MaterialTheme.colorScheme.onBackground
                        present -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = active != null,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(250)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (active ?: ' ').toString(), fontSize = 36.sp, fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.background,
            )
        }
    }
}

/** State shared by [Modifier.fastScroll] (touch handling) and [FastScroller] (drawing). */
class FastScrollState(val listState: LazyListState, val itemCount: Int) {
    var dragFraction by mutableStateOf<Float?>(null)
    var recentlyScrolled by mutableStateOf(false)

    val listFraction by derivedStateOf {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        val within = if (first != null && first.size > 0) -first.offset.toFloat() / first.size else 0f
        ((listState.firstVisibleItemIndex + within.coerceIn(0f, 1f)) / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val fraction: Float get() = dragFraction ?: listFraction
    val visible: Boolean get() = itemCount > 1 && (dragFraction != null || recentlyScrolled)
    val targetIndex: Int get() = (fraction * (itemCount - 1)).roundToInt()
}

private val SCROLLER_PADDING = 4.dp
private val THUMB_HEIGHT = 44.dp

@Composable
fun rememberFastScrollState(listState: LazyListState, itemCount: Int): FastScrollState {
    val state = remember(listState, itemCount) { FastScrollState(listState, itemCount) }
    LaunchedEffect(state) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) state.recentlyScrolled = true else { delay(1500); state.recentlyScrolled = false }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.dragFraction }.collectLatest { f ->
            if (f != null) listState.scrollToItem((f * (itemCount - 1)).roundToInt())
        }
    }
    return state
}

/**
 * Put on the Box that holds the list. Sees touches before the list does and takes over only
 * when the visible scroll handle itself is grabbed; everything else scrolls the list as usual.
 */
fun Modifier.fastScroll(state: FastScrollState): Modifier = pointerInput(state) {
    val edge = 40.dp.toPx()
    val grab = 40.dp.toPx()
    val pad = SCROLLER_PADDING.toPx()
    val thumb = THUMB_HEIGHT.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val track = size.height - 2 * pad - thumb
        val thumbCenter = pad + state.fraction * track + thumb / 2
        if (!state.visible || track <= 0f || down.position.x < size.width - edge ||
            abs(down.position.y - thumbCenter) > grab
        ) return@awaitEachGesture
        down.consume()
        val startY = down.position.y
        val startFraction = state.fraction
        state.dragFraction = startFraction
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            state.dragFraction = (startFraction + (change.position.y - startY) / track).coerceIn(0f, 1f)
        }
        state.dragFraction = null
    }
}

/** The handle along the right edge (and a position bubble while dragging). [label] describes an item index. */
@Composable
fun BoxScope.FastScroller(state: FastScrollState, label: (Int) -> String) {
    if (state.itemCount < 2) return
    val haptics = LocalHapticFeedback.current
    val dragging = state.dragFraction != null
    LaunchedEffect(dragging) { if (dragging) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val alpha by animateFloatAsState(
        if (state.visible) 1f else 0f, tween(if (state.visible) 100 else 400), label = "scroller",
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp).padding(vertical = SCROLLER_PADDING),
    ) {
        val track = constraints.maxHeight - with(density) { THUMB_HEIGHT.toPx() }
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, (state.fraction * track).roundToInt()) }
                .padding(end = 4.dp)
                .alpha(alpha)
                .size(width = if (dragging) 6.dp else 4.dp, height = THUMB_HEIGHT)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }

    if (dragging) {
        Box(
            Modifier.align(Alignment.Center)
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(state.fraction * 100).roundToInt()}%", fontSize = 22.sp, fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.background,
                )
                val text = label(state.targetIndex)
                if (text.isNotBlank()) Text(
                    text, fontSize = 13.sp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
