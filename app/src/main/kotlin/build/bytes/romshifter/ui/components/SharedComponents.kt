package build.bytes.romshifter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.utils.getAvatarColor
import coil.compose.AsyncImage

@Composable
fun MenuCard(title: String, icon: ImageVector, description: String, onClick: () -> Unit) {

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "MenuCardBounce"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.large)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    showBackupTime: Boolean = true,
    isMonochrome: Boolean = false, 
    onToggleSelect: (String) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (app.isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "item_color"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "AppListItemBounce"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onToggleSelect(app.packageName) }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.small)

        
        val imageColorFilter = if (isMonochrome) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else null

        if (app.iconBitmap != null) {
            Image(
                bitmap = app.iconBitmap.asImageBitmap(),
                contentDescription = "App Icon",
                colorFilter = imageColorFilter, 
                modifier = iconModifier
            )
        } else if (app.iconPath != null) {
            AsyncImage(
                model = app.iconPath,
                contentDescription = "App Icon",
                colorFilter = imageColorFilter, 
                modifier = iconModifier
            )
        } else {
            val letter = app.label.firstOrNull()?.uppercase() ?: "?"
            val bgColor = if (isMonochrome) Color.Gray else getAvatarColor(app.label) 
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(bgColor), contentAlignment = Alignment.Center) {
                Text(text = letter, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (app.isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelMedium,
                color = if (app.isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(
                    alpha = 0.8f
                ) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (showBackupTime && app.backupTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.backupTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (app.backupTime == "No backup on device") MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    ) else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(
            checked = app.isSelected,
            onCheckedChange = { onToggleSelect(app.packageName) },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
fun ShimmerAppListItem() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(initialValue = 0.2f, targetValue = 0.6f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(shimmerColor))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(CircleShape).background(shimmerColor))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(CircleShape).background(shimmerColor))
        }
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(shimmerColor))
    }
}