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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

        // Top instruction bar
        TopInstructionBar(
            turnIconRes = turnIconRes,
            distanceToTurn = distanceToTurn,
            instruction = instruction,
            isMuted = isMuted,
            onMuteToggle = onMuteToggle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        )

        // Bottom area: speed panel + info bar
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Recenter button (above speed panel, right-aligned)
            if (showRecenter) {
                RecenterButton(
                    onClick = onRecenter,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, bottom = 8.dp)
                )
            }

            // Speed panel (left-aligned)
            SpeedPanel(
                currentSpeed = currentSpeed,
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 8.dp)
            )

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
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Turn icon
        Image(
            painter = painterResource(turnIconRes),
            contentDescription = "Turn direction",
            modifier = Modifier.size(48.dp)
        )

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
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
    ) {
        // ETA
        BasicText(
            text = eta,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.weight(1f)
        )

        // Remaining time
        BasicText(
            text = remainingTime,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.weight(1f)
        )

        // Remaining distance
        BasicText(
            text = remainingDistance,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.weight(1f)
        )

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
