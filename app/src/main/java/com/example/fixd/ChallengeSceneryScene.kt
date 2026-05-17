package com.example.fixd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class ChallengeSceneObject(
    val id: String,
    val emoji: String,
    val name: String,
    val kind: ChallengeSceneObjectKind
)

enum class ChallengeSceneObjectKind {
    FIGURE,
    ACCESSORY
}

@Composable
fun ChallengeInteractiveScenery(
    scenery: ChallengeScenery,
    objects: List<ChallengeSceneObject>,
    placements: Map<String, ChallengeScenePlacement>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onPlacementsChanged: (Map<String, ChallengeScenePlacement>) -> Unit = {}
) {
    val localPlacements = remember { mutableStateMapOf<String, ChallengeScenePlacement>() }
    var selectedObjectId by remember(objects) { mutableStateOf(objects.firstOrNull()?.id) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(objects, placements) {
        localPlacements.clear()
        objects.forEachIndexed { index, sceneObject ->
            localPlacements[sceneObject.id] = placements[sceneObject.id] ?: defaultPlacement(index, objects.size, sceneObject.kind)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 520.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 320.dp else 440.dp)
                .clip(RoundedCornerShape(if (compact) 24.dp else 12.dp))
                .background(Color(scenery.bottomColor))
                .onSizeChanged { sceneSize = it }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSceneryBackdrop(scenery)
            }
            objects.forEach { sceneObject ->
                val placement = localPlacements[sceneObject.id] ?: ChallengeScenePlacement()
                SceneObjectChip(
                    sceneObject = sceneObject,
                    placement = placement,
                    selected = selectedObjectId == sceneObject.id,
                    editable = editable,
                    sceneSize = sceneSize,
                    onSelect = { selectedObjectId = sceneObject.id },
                    onMove = { next ->
                        localPlacements[sceneObject.id] = next
                    },
                    onMoveFinished = { onPlacementsChanged(localPlacements.toMap()) }
                )
            }
            if (objects.isEmpty()) {
                Text(
                    text = "Unlock figures and accessories to place them here.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {}, enabled = false, label = { Text(scenery.name) })
                if (editable) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Drag to arrange") })
                }
            }
        }
    }
}

@Composable
private fun SceneObjectChip(
    sceneObject: ChallengeSceneObject,
    placement: ChallengeScenePlacement,
    selected: Boolean,
    editable: Boolean,
    sceneSize: IntSize,
    onSelect: () -> Unit,
    onMove: (ChallengeScenePlacement) -> Unit,
    onMoveFinished: () -> Unit
) {
    val chipSize = if (sceneObject.kind == ChallengeSceneObjectKind.FIGURE) 72.dp else 58.dp
    val chipPx = if (sceneObject.kind == ChallengeSceneObjectKind.FIGURE) 72f else 58f
    val x = ((sceneSize.width - chipPx).coerceAtLeast(1f) * placement.x).roundToInt()
    val y = ((sceneSize.height - chipPx).coerceAtLeast(1f) * placement.y).roundToInt()
    Card(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .size(chipSize)
            .pointerInput(editable, sceneSize, placement) {
                var currentPlacement = placement
                detectDragGestures(
                    onDragStart = {
                        currentPlacement = placement
                        onSelect()
                    },
                    onDrag = { change, dragAmount ->
                        if (!editable || sceneSize.width == 0 || sceneSize.height == 0) return@detectDragGestures
                        change.consume()
                        val nextX = currentPlacement.x + (dragAmount.x / (sceneSize.width - chipPx).coerceAtLeast(1f))
                        val nextY = currentPlacement.y + (dragAmount.y / (sceneSize.height - chipPx).coerceAtLeast(1f))
                        currentPlacement = ChallengeScenePlacement(nextX.coerceIn(0f, 1f), nextY.coerceIn(0f, 1f))
                        onMove(currentPlacement)
                    },
                    onDragEnd = { onMoveFinished() },
                    onDragCancel = { onMoveFinished() }
                )
            },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected && editable) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected && editable) 8.dp else 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = sceneObject.emoji,
                style = if (sceneObject.kind == ChallengeSceneObjectKind.FIGURE) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                }
            )
            Text(
                text = sceneObject.name.take(10),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                color = if (selected && editable) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun DrawScope.drawSceneryBackdrop(scenery: ChallengeScenery) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(scenery.topColor), Color(scenery.bottomColor), Color(scenery.groundColor))
        )
    )
    val horizon = size.height * 0.58f
    drawCircle(
        color = Color.White.copy(alpha = 0.34f),
        radius = size.minDimension * 0.18f,
        center = Offset(size.width * 0.82f, size.height * 0.16f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.18f),
        radius = size.minDimension * 0.11f,
        center = Offset(size.width * 0.22f, size.height * 0.18f)
    )
    drawMountain(Color.White.copy(alpha = 0.32f), horizon, 0.08f, 0.5f, 0.95f)
    drawMountain(Color.Black.copy(alpha = 0.10f), horizon + 24f, -0.18f, 0.34f, 0.72f)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(scenery.groundColor).copy(alpha = 0.62f), Color(scenery.groundColor))
        ),
        topLeft = Offset(0f, horizon),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - horizon)
    )
    repeat(7) { index ->
        val x = size.width * (0.08f + index * 0.14f)
        val y = horizon + size.height * (0.08f + (index % 3) * 0.05f)
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = size.minDimension * (0.025f + (index % 2) * 0.012f),
            center = Offset(x, y)
        )
    }
    repeat(5) { index ->
        val x = size.width * (0.1f + index * 0.2f)
        val baseY = size.height * 0.86f
        drawLine(
            color = Color.Black.copy(alpha = 0.16f),
            start = Offset(x, baseY),
            end = Offset(x + size.width * 0.03f, baseY - size.height * 0.09f),
            strokeWidth = 3f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = size.minDimension * 0.035f,
            center = Offset(x + size.width * 0.035f, baseY - size.height * 0.1f)
        )
    }
    drawRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(0f, horizon - 2f),
        size = androidx.compose.ui.geometry.Size(size.width, 4f)
    )
}

private fun DrawScope.drawMountain(color: Color, horizon: Float, startX: Float, peakX: Float, endX: Float) {
    val path = Path().apply {
        moveTo(size.width * startX, horizon)
        lineTo(size.width * peakX, size.height * 0.26f)
        lineTo(size.width * endX, horizon)
        close()
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = Color.White.copy(alpha = 0.12f), style = Stroke(width = 2f))
}

private fun defaultPlacement(index: Int, count: Int, kind: ChallengeSceneObjectKind): ChallengeScenePlacement {
    if (kind == ChallengeSceneObjectKind.ACCESSORY) {
        val accessorySlots = listOf(
            ChallengeScenePlacement(0.08f, 0.66f),
            ChallengeScenePlacement(0.78f, 0.62f),
            ChallengeScenePlacement(0.46f, 0.74f),
            ChallengeScenePlacement(0.22f, 0.5f)
        )
        return accessorySlots[index % accessorySlots.size]
    }
    val spacing = 1f / (count + 1).coerceAtLeast(2)
    return ChallengeScenePlacement(
        x = (spacing * (index + 1)).coerceIn(0.08f, 0.86f),
        y = (0.58f + (index % 3) * 0.08f).coerceAtMost(0.86f)
    )
}

fun challengeSceneObjects(
    figures: List<ChallengeFigure>,
    accessories: List<ChallengeAccessory>
): List<ChallengeSceneObject> {
    return accessories.map {
        ChallengeSceneObject("accessory_${it.id}", it.emoji, it.name, ChallengeSceneObjectKind.ACCESSORY)
    } + figures.map {
        ChallengeSceneObject("figure_${it.id}", it.emoji, it.name, ChallengeSceneObjectKind.FIGURE)
    }
}
