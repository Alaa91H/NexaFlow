package com.nexaflow.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nexaflow.feature.builder.R
import kotlin.math.roundToInt

/**
 * Fallback height (pixels) for a row that has not been measured yet, used
 * only while the very first drag is about to start.
 */
private const val TASK_DRAG_FALLBACK_HEIGHT_PX = 96

/**
 * Mutable drag-reorder state for one list of rows (execution tasks,
 * triggers or constraints). Kept separate per list so dragging in one
 * section never disturbs the others.
 */
internal class TaskDragState<T> {
    var draggedIndex by mutableIntStateOf(-1)
    var dragOffset by mutableFloatStateOf(0f)
    val itemHeights = mutableStateMapOf<T, Int>()
}

/**
 * Index the dragged row currently hovers over, computed from the measured
 * row heights and the accumulated vertical drag offset. The dragged item's
 * center decides the target: the first item whose span [top, top + height)
 * contains that center. Variable row heights (collapsed vs expanded) are
 * handled by measuring every row into [heights].
 */
internal fun <T> computeDragTarget(
    items: List<T>,
    heights: Map<T, Int>,
    draggedIndex: Int,
    draggedOffset: Float
): Int {
    if (items.size < 2) return draggedIndex
    fun h(i: Int) = heights[items[i]] ?: TASK_DRAG_FALLBACK_HEIGHT_PX
    val tops = IntArray(items.size)
    var acc = 0
    for (i in items.indices) {
        tops[i] = acc
        acc += h(i)
    }
    val draggedTop = tops[draggedIndex] + draggedOffset
    val draggedCenter = draggedTop + h(draggedIndex) / 2f
    for (i in items.indices) {
        if (draggedCenter >= tops[i] && draggedCenter < tops[i] + h(i)) return i
    }
    // Center dragged past the ends: clamp to the nearest edge.
    return if (draggedCenter < tops[0]) 0 else items.lastIndex
}

/**
 * Offset correction so the dragged row stays under the finger after the
 * list reorders. Moving down shrinks the stack above the row (negative),
 * moving up grows it (positive).
 */
internal fun <T> dragOffsetCorrection(
    items: List<T>,
    heights: Map<T, Int>,
    from: Int,
    to: Int
): Float {
    if (from == to) return 0f
    fun h(i: Int) = heights[items[i]] ?: TASK_DRAG_FALLBACK_HEIGHT_PX
    var sum = 0
    if (from < to) {
        for (i in from + 1..to) sum += h(i)
        return -sum.toFloat()
    }
    for (i in to until from) sum += h(i)
    return sum.toFloat()
}

/** Starts a drag on [state] for the row at [index]. */
internal fun <T> startDrag(state: TaskDragState<T>, index: Int) {
    state.draggedIndex = index
    state.dragOffset = 0f
}

/**
 * Applies a vertical drag delta to [state], reordering [items] live via
 * [onMove] whenever the dragged row crosses a neighbor's midpoint.
 */
internal fun <T> dragBy(
    state: TaskDragState<T>,
    items: List<T>,
    deltaY: Float,
    onMove: (from: Int, to: Int) -> Unit
) {
    if (state.draggedIndex < 0 || items.isEmpty()) return
    state.dragOffset += deltaY
    val target = computeDragTarget(items, state.itemHeights, state.draggedIndex, state.dragOffset)
    if (target != state.draggedIndex) {
        val correction = dragOffsetCorrection(items, state.itemHeights, state.draggedIndex, target)
        onMove(state.draggedIndex, target)
        state.draggedIndex = target
        state.dragOffset += correction
    }
}

/**
 * Number badge pinned to the end of a builder row ("1", "2", ...) so the
 * task's position is visible at a glance and rows are easy to tell apart.
 * Used by the execution, trigger and constraint lists alike.
 */
@Composable
internal fun TaskNumberBadge(
    number: Int,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

/** Ends the drag on [state] and snaps the row back into place. */
internal fun <T> endDrag(state: TaskDragState<T>) {
    state.draggedIndex = -1
    state.dragOffset = 0f
}

/**
 * Row placement while drag-reordering: measures the row into [state],
 * lifts it above its neighbors while dragged, and offsets it under the
 * finger (with a matching shadow).
 */
@Composable
internal fun <T> Modifier.taskDragOffset(
    state: TaskDragState<T>,
    item: T,
    isDragging: Boolean
): Modifier = this
    .onSizeChanged { state.itemHeights[item] = it.height }
    .zIndex(if (isDragging) 1f else 0f)
    .offset {
        IntOffset(
            0,
            if (isDragging) state.dragOffset.roundToInt() else 0
        )
    }
    .shadow(
        elevation = if (isDragging) 8.dp else 0.dp,
        shape = MaterialTheme.shapes.large,
        clip = false
    )

/**
 * The unified ↕️ reorder handle used by every builder row: long-press the
 * arrows and drag to reorder, or tap an arrow to move one step (kept as a
 * secondary option). The handle dims while its row is being dragged.
 */
@Composable
internal fun TaskRowHandle(
    index: Int,
    total: Int,
    isDragging: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDragDelta(amount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .graphicsLayer { alpha = if (isDragging) 0.6f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.move_up),
            modifier = Modifier
                .size(20.dp)
                .clickable(enabled = index > 0, onClick = onMoveUp),
            tint = if (index > 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.move_down),
            modifier = Modifier
                .size(20.dp)
                .clickable(enabled = index < total - 1, onClick = onMoveDown),
            tint = if (index < total - 1) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    }
}
