package com.ncmcloud.player.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import com.ncmcloud.player.domain.PlayMode

fun PlayMode.icon(): ImageVector = when (this) {
    PlayMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    PlayMode.ORDER, PlayMode.LIST_LOOP -> Icons.Filled.Repeat
}
