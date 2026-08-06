package build.bytes.romshifter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.large) // Pulled dynamically from Theme.kt
            .clickable(onClick = onClick),
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
                    .size(52.dp) // Adjusted size
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
fun AppListItem(app: AppInfo, onToggleSelect: (String) -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (app.isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "item_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium) // Pulled dynamically
            .background(containerColor)
            .clickable { onToggleSelect(app.packageName) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier
            .size(48.dp) // Adjusted size
            .clip(MaterialTheme.shapes.small)

        if (app.iconBitmap != null) {
            Image(bitmap = app.iconBitmap.asImageBitmap(), contentDescription = "App Icon", modifier = iconModifier)
        } else if (app.iconPath != null) {
            AsyncImage(model = app.iconPath, contentDescription = "App Icon", modifier = iconModifier)
        } else {
            val letter = app.label.firstOrNull()?.uppercase() ?: "?"
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(getAvatarColor(app.label)), contentAlignment = Alignment.Center) {
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
                color = if (app.isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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