package com.aioweb.app.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aioweb.app.player.PlayerSource

@Composable
fun SourceSelectorPopup(
    sources: List<PlayerSource>,
    selectedSourceId: String?,
    onSwitchSource: (PlayerSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {

        Card(
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            modifier = Modifier
                .size(48.dp)
                .clickable { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Sources",
                    tint = Color.White
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.Black.copy(alpha = 0.85f))
        ) {
            sources.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (source.id == selectedSourceId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Green
                                    )
                                }
                                Text(
                                    text = source.qualityTag ?: "Unknown",
                                    color = Color.White
                                )
                            }
                            Text(
                                text = source.addonName,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSwitchSource(source)
                    },
                    leadingIcon = {
                        if (source.isMagnet) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Magnet",
                                tint = Color.Cyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Stream",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        }
    }
}