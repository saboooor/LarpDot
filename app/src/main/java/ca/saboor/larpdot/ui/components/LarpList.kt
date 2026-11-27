package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.ui.theme.LarpIcons

/**
 * Shared row item used across reorderable and selectable lists (Island Priority,
 * Custom Islands, App Blacklist) with consistent pill surface styling, optional
 * rank circle, icon/leading slot, highlight badge, and reorder/trailing actions.
 */
@Composable
fun LarpListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    badge: String? = null,
    rank: Int? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    canMoveUp: Boolean = false,
    onMoveDown: (() -> Unit)? = null,
    canMoveDown: Boolean = false,
    subtitleMaxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val hasReorder = onMoveUp != null || onMoveDown != null
    val endPadding = if (hasReorder || trailing != null) 8.dp else 12.dp
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = endPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rank != null) {
                Surface(
                    shape = CircleShape,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$rank",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (highlighted) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (leading != null) {
                leading()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasReorder || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onMoveUp != null) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = LarpIcons.KeyboardArrowUp,
                                contentDescription = "Move $title up",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = LarpIcons.KeyboardArrowDown,
                                contentDescription = "Move $title down",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    trailing?.invoke(this)
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            modifier = modifier.fillMaxWidth(),
            content = rowContent,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            modifier = modifier.fillMaxWidth(),
            content = rowContent,
        )
    }
}
