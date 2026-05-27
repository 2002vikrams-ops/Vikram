package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.CallState
import com.example.data.MessageEntity
import kotlinx.coroutines.delay
import kotlin.math.sin

data class Point(val x: Float, val y: Float)

// Simple drawing structures mapped to palette indexes
data class DrawStroke(val colorIndex: Int, val width: Float, val points: List<Point>)

// Cohesive palette colors
val snapPalette = listOf(
    Color(0xFF00FFCC), // 0: neon cyan
    Color(0xFFFF3366), // 1: bubblegum pink
    Color(0xFFFFCC00), // 2: golden yellow
    Color(0xFF33FF33), // 3: electric green
    Color(0xFF9933FF), // 4: vivid purple
    Color(0xFFFFFFFF), // 5: pure white
    Color(0xFF0088FF)  // 6: deep sky blue
)

/**
 * Compact stroke serialization helper.
 * stroke1: index;width;x1,y1_x2,y2_x3,y3|stroke2...
 */
fun serializeStrokes(strokes: List<DrawStroke>): String {
    return _serializeStrokes(strokes)
}

fun _serializeStrokes(strokes: List<DrawStroke>): String {
    return strokes.joinToString("|") { stroke ->
        val pointsStr = stroke.points.joinToString("_") { "${it.x},${it.y}" }
        "${stroke.colorIndex};${stroke.width};$pointsStr"
    }
}

fun deserializeStrokes(str: String?): List<DrawStroke> {
    if (str.isNullOrBlank()) return emptyList()
    return try {
        str.split("|").map { item ->
            val parts = item.split(";")
            val colorIndex = parts[0].toInt()
            val width = parts[1].toFloat()
            val points = parts[2].split("_").mapNotNull { pStr ->
                val xy = pStr.split(",")
                if (xy.size == 2) Point(xy[0].toFloat(), xy[1].toFloat()) else null
            }
            DrawStroke(colorIndex, width, points)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Live Drawing Board for sending unique Snaps
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapDrawDialog(
    onDismiss: () -> Unit,
    onSendSnap: (serializedData: String, durationSeconds: Int) -> Unit
) {
    var strokes by remember { mutableStateOf(listOf<DrawStroke>()) }
    val currentPoints = remember { mutableStateListOf<Point>() }
    var currentColorIndex by remember { mutableStateOf(0) } // neon cyan default
    var currentWidth by remember { mutableStateOf(10f) }
    var disappearingTimerSeconds by remember { mutableStateOf(5) } // disappearing default

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close drawing board")
                    }
                    Text(
                        text = "NEW SNAP CANVAS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                strokes = strokes.dropLast(1)
                            }
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo stroke")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Container (10:13 vertical aspect ratio typical for snaps)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121212))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints.clear()
                                    currentPoints.add(Point(offset.x, offset.y))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints.add(Point(change.position.x, change.position.y))
                                    // Update visual feed reactively
                                    val stroke = DrawStroke(
                                        colorIndex = currentColorIndex,
                                        width = currentWidth,
                                        points = currentPoints.toList()
                                    )
                                    val list = strokes.toMutableList()
                                    if (list.size > 0 && list.last().points == currentPoints.dropLast(1)) {
                                        list[list.lastIndex] = stroke
                                    } else {
                                        list.add(stroke)
                                    }
                                    strokes = list
                                },
                                onDragEnd = {
                                    currentPoints.clear()
                                }
                            )
                        }
                        .testTag("snap_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        strokes.forEach { stroke ->
                            val path = Path()
                            if (stroke.points.isNotEmpty()) {
                                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                                stroke.points.forEach { point ->
                                    path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path = path,
                                    color = snapPalette.getOrElse(stroke.colorIndex) { Color.Cyan },
                                    style = CanvasStroke(
                                        width = stroke.width,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }

                    if (strokes.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Draw with your finger to paint a snap!",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Palette choosing row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    snapPalette.forEachIndexed { index, color ->
                        val isSelected = currentColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { currentColorIndex = index }
                        )
                    }
                    IconButton(onClick = { strokes = emptyList(); currentPoints.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear canvas", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Settings Block
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Brush thickness slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LineWeight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Brush Size", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = currentWidth,
                                onValueChange = { currentWidth = it },
                                valueRange = 4f..40f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Disappearing timer
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Self-Destruct Timer", fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (disappearingTimerSeconds == 99) "Never" else "${disappearingTimerSeconds}s",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Row {
                                    AssistChip(
                                        onClick = { disappearingTimerSeconds = 5 },
                                        label = { Text("5s") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (disappearingTimerSeconds == 5) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AssistChip(
                                        onClick = { disappearingTimerSeconds = 10 },
                                        label = { Text("10s") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (disappearingTimerSeconds == 10) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AssistChip(
                                        onClick = { disappearingTimerSeconds = 99 },
                                        label = { Text("Infinite") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (disappearingTimerSeconds == 99) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Button
                Button(
                    onClick = {
                        val data = serializeStrokes(strokes)
                        onSendSnap(data, disappearingTimerSeconds)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("send_snap_button"),
                    enabled = strokes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SEND SNAP TO CHAT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Disappearing Snap Viewer (Completes with ticking circle ring)
 */
@Composable
fun DisappearingSnapViewer(
    snapMessage: MessageEntity,
    onFinished: () -> Unit
) {
    val durationSeconds = snapMessage.snapDuration ?: 5
    var secondsLeft by remember { mutableStateOf(durationSeconds) }
    val isInfinite = durationSeconds == 99

    val strokes = remember { deserializeStrokes(snapMessage.snapUri) }

    // Tick Down Effect
    LaunchedEffect(key1 = Unit) {
        if (!isInfinite) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            onFinished()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isInfinite) {
                onFinished()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main drawing show case (10:13 centered)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F0F))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        strokes.forEach { stroke ->
                            val path = Path()
                            if (stroke.points.isNotEmpty()) {
                                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                                stroke.points.forEach { point ->
                                    path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path = path,
                                    color = snapPalette.getOrElse(stroke.colorIndex) { Color.Cyan },
                                    style = CanvasStroke(
                                        width = stroke.width,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Top Countdown bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onFinished,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.6f))
                    ) {
                        Text("Close", color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isInfinite) {
                            Icon(Icons.Default.AllInclusive, contentDescription = "Unlimited Snap", tint = Color.White)
                        } else {
                            Text(
                                text = secondsLeft.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                // Disappearing alert
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Red, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isInfinite) "Disappearing snap - click Close to finish viewing" else "This custom snap self-destructs in $secondsLeft seconds!",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dynamic Audio / Video Calling Interface
 */
@Composable
fun CallOverlay(
    activeCall: CallState.Active,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit
) {
    // Pulse animation for Radar rings in Call
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Oscillator wave drawing animation for Audio Call
    val oscPhase by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Elapsed timer format
    val minutes = activeCall.durationSeconds / 60
    val seconds = activeCall.durationSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Dialog(
        onDismissRequest = {}, // call dialog should not be dismissed by tap
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF09090C) // Space Slate Color
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E1A2F),
                                Color(0xFF09090C)
                            ),
                            radius = 1200f
                        )
                    )
            ) {
                // VIDEO SCREEN CONTENT
                if (activeCall.callType == "VIDEO" && !activeCall.isCameraOff) {
                    // Simulated Caller Video Feed
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Color wash particle effect to simulate active videocall stream
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val midX = w / 2
                            val midY = h / 2

                            // Draw beautiful geometric neon pulsing shapes as mockup webcam
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF00FFCC).copy(alpha = 0.15f), Color.Transparent),
                                    center = Offset(midX + sin(oscPhase) * 100, midY),
                                    radius = 350f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFF3366).copy(alpha = 0.1f), Color.Transparent),
                                    center = Offset(midX, midY + sin(oscPhase + 2) * 100),
                                    radius = 450f
                                )
                            )
                        }

                        // Video Header Overlays
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = activeCall.contactName,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "LIVE VIDEO CALL • $formattedTime",
                                color = Color(0xFF00FFCC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Selfie Floating PIP preview Card (Simulated User Camera Feed)
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 120.dp, end = 24.dp)
                                .width(110.dp)
                                .height(150.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color(0xFFFF3366).copy(alpha = 0.2f),
                                        center = center,
                                        radius = 60f
                                    )
                                }
                                Text(
                                    text = "ME\n(Selfie Preview)",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                } else {
                    // AUDIO SCREEN CONTENT (And when video camera is toggled OFF)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeCall.contactName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (activeCall.isMuted) "MUTED" else "VOICE CALLING...",
                            color = if (activeCall.isMuted) Color.Red else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(60.dp))

                        // Pulsing Avatar circles
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                            // Pulsing Ring 1
                            Box(
                                modifier = Modifier
                                    .size((140 * pulseScale).dp)
                                    .background(Color(0xFF00FFCC).copy(alpha = pulseAlpha), CircleShape)
                            )
                            // Pulsing Ring 2
                            Box(
                                modifier = Modifier
                                    .size((170 * (pulseScale * 0.7f)).dp)
                                    .background(Color(0xFFFF3366).copy(alpha = pulseAlpha * 0.7f), CircleShape)
                            )

                            // Main core Avatar
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .background(Color(0xFF2E2C3F), CircleShape)
                                    .border(3.dp, Color(0xFF00FFCC), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCall.contactAvatar,
                                    fontSize = 62.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                        Text(
                            text = formattedTime,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light
                        )

                        Spacer(modifier = Modifier.height(50.dp))

                        // Audio Oscilloscope waveform visualizer on custom Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .padding(horizontal = 48.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2
                            val points = 60
                            val stepX = w / (points - 1)
                            val path = Path()

                            path.moveTo(0f, midY)
                            for (i in 0 until points) {
                                val x = i * stepX
                                // Build a combination of sine waves showing simulated micro activity
                                val amp = if (activeCall.isMuted) 0f else (h / 3f) * (sin(i * 0.15f + oscPhase * 3).toFloat() + sin(i * 0.3f + oscPhase * 1.5).toFloat())
                                path.lineTo(x, midY + amp)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF00FFCC),
                                style = CanvasStroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // Call Controls Bar (Sticky bottom)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // MUTE ACTION
                        FilledIconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.size(54.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (activeCall.isMuted) Color.Red else Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(
                                imageVector = if (activeCall.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = Color.White
                            )
                        }

                        // SPEAKERPHONE ACTION
                        FilledIconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier.size(54.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (activeCall.isSpeakerOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = Color.White
                            )
                        }

                        // CAMERA ON/OFF (Only for Video calls)
                        if (activeCall.callType == "VIDEO") {
                            FilledIconButton(
                                onClick = onToggleCamera,
                                modifier = Modifier.size(54.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (activeCall.isCameraOff) Color.Red else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Icon(
                                    imageVector = if (activeCall.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                    contentDescription = "Toggle Camera",
                                    tint = Color.White
                                )
                            }
                        }

                        // END CALL BUTTON
                        FilledIconButton(
                            onClick = onEndCall,
                            modifier = Modifier.size(64.dp).testTag("end_call_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFFF3333)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
