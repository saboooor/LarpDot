package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class LarpButtonOption(
    val label: String,
    val checked: Boolean,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    val fontFamily: FontFamily? = null,
    val onCheckedChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LarpConnectedButtons(
    options: List<LarpButtonOption>,
    modifier: Modifier = Modifier,
    singleSelection: Boolean = true,
    maxButtonsPerRow: Int = Int.MAX_VALUE,
    scrollable: Boolean = false,
) {
    if (options.isEmpty()) return
    val selection = if (singleSelection) Modifier.selectableGroup() else Modifier
    if (scrollable) {
        ConnectedButtonRow(
            options = options,
            modifier = modifier.fillMaxWidth().then(selection).horizontalScroll(rememberScrollState()),
            singleSelection = singleSelection,
            scrollable = true,
        )
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minButtonWidth = maxOf(48.dp, with(LocalDensity.current) { 52.sp.toDp() })
        val columns = (maxWidth / minButtonWidth).toInt().coerceIn(1, minOf(options.size, maxButtonsPerRow))
        val rowCount = (options.size + columns - 1) / columns
        val buttonsPerRow = (options.size + rowCount - 1) / rowCount
        Column(
            modifier = selection,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.chunked(buttonsPerRow).forEach { row ->
                ConnectedButtonRow(row, Modifier.fillMaxWidth(), singleSelection)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedButtonRow(
    options: List<LarpButtonOption>,
    modifier: Modifier,
    singleSelection: Boolean,
    scrollable: Boolean = false,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when {
                options.size == 1 -> ToggleButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(12.dp),
                    checkedShape = CircleShape,
                )
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val width = if (scrollable) Modifier else Modifier.weight(1f)
            ToggleButton(
                checked = option.checked,
                enabled = option.enabled,
                onCheckedChange = { checked ->
                    option.onCheckedChange(if (singleSelection) true else checked)
                },
                shapes = shapes,
                contentPadding = PaddingValues(horizontal = if (scrollable) 10.dp else 4.dp, vertical = 8.dp),
                modifier = width.fillMaxHeight().then(
                    if (singleSelection) Modifier.semantics { role = Role.RadioButton } else Modifier,
                ),
            ) {
                option.icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = option.label,
                    fontFamily = option.fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = if (scrollable) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
