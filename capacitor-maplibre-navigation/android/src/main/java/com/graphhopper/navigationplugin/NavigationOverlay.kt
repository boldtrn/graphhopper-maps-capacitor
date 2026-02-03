package com.graphhopper.navigationplugin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import org.maplibre.android.maps.MapView

@Composable
fun NavigationScreen(
    mapView: MapView,
    turnIconRes: Int,
    distanceToTurn: String,
    instruction: String,
    isMuted: Boolean,
    eta: String,
    remainingTime: String,
    remainingDistance: String,
    currentSpeed: String,
    showRecenter: Boolean,
    thenTurnIconRes: Int?,
    roundaboutExit: Int?,
    onMuteToggle: () -> Unit,
    onStop: () -> Unit,
    onRecenter: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Native MapView — full screen
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top: instruction bar + optional "then" panel
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = 420.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            TopInstructionBar(
                turnIconRes = turnIconRes,
                distanceToTurn = distanceToTurn,
                instruction = instruction,
                isMuted = isMuted,
                onMuteToggle = onMuteToggle,
                showThen = thenTurnIconRes != null,
                roundaboutExit = roundaboutExit,
            )

            if (thenTurnIconRes != null) {
                ThenPanel(thenTurnIconRes = thenTurnIconRes)
            }
        }

        // Bottom area: speed panel + info bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 420.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            // Speed panel + recenter button stacked vertically
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                if (showRecenter) {
                    RecenterButton(onClick = onRecenter)
                }
                SpeedPanel(
                    currentSpeed = currentSpeed,
                    modifier = if (showRecenter) Modifier.padding(top = 8.dp) else Modifier,
                )
            }

            // Bottom info bar (full width, with system bar insets)
            BottomInfoBar(
                eta = eta,
                remainingTime = remainingTime,
                remainingDistance = remainingDistance,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun TopInstructionBar(
    turnIconRes: Int,
    distanceToTurn: String,
    instruction: String,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    showThen: Boolean = false,
    roundaboutExit: Int? = null,
    modifier: Modifier = Modifier,
) {
    val shape = if (showThen) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(12.dp)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(Color.White, shape)
            .padding(12.dp)
    ) {
        // Turn icon with optional roundabout exit number overlay
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            Image(
                painter = painterResource(turnIconRes),
                contentDescription = "Turn direction",
                modifier = Modifier.size(48.dp)
            )
            if (roundaboutExit != null) {
                BasicText(
                    text = roundaboutExit.toString(),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    ),
                    modifier = Modifier.offset(y = (-4).dp),
                )
            }
        }

        // Instruction text area
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            BasicText(
                text = distanceToTurn,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            )
            BasicText(
                text = instruction,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color(0xFF444444),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Mute button
        Image(
            painter = painterResource(
                if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
            ),
            contentDescription = "Toggle voice instructions",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onMuteToggle)
                .padding(8.dp)
        )
    }
}

@Composable
private fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_my_location),
            contentDescription = "Re-center map",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SpeedPanel(
    currentSpeed: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = currentSpeed,
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        )

        // km/h stacked unit
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            BasicText(
                text = "km",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                )
            )
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 1.dp)
                    .background(Color(0xFF666666))
            )
            BasicText(
                text = "h",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                )
            )
        }
    }
}

@Composable
private fun BottomInfoBar(
    eta: String,
    remainingTime: String,
    remainingDistance: String,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color.White, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(12.dp)
    ) {
        // Left side: remaining time + distance
        Column {
            BasicText(
                text = remainingTime,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            )
            BasicText(
                text = remainingDistance,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ETA centered in remaining space
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            BasicText(
                text = eta,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                ),
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        // Stop button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(48.dp)
                .border(1.5.dp, Color(0xFF333333), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onStop)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Stop navigation",
                colorFilter = ColorFilter.tint(Color(0xFF333333)),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ThenPanel(
    thenTurnIconRes: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.White, shape)
            .drawBehind {
                drawLine(
                    color = Color(0xFFDDDDDD),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        BasicText(
            text = "Then",
            style = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF666666),
            )
        )
        Image(
            painter = painterResource(thenTurnIconRes),
            contentDescription = "Then turn",
            modifier = Modifier
                .padding(start = 6.dp)
                .size(28.dp)
        )
    }
}
