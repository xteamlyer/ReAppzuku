package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppModel,
    onKill: () -> Unit,
    onToggleWhitelist: () -> Unit,
    onClick: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            app.isSelected   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            app.isProtected  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            app.isWhitelisted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            else              -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "itemBg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick      = onClick,
                onLongClick  = onClick,
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app)

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = app.appName ?: app.packageName,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false),
                    )
                    if (app.isSystemApp) {
                        TagChip(stringResource(R.string.tag_system))
                    }
                    if (app.isPersistentApp) {
                        TagChip(stringResource(R.string.tag_persistent))
                    }
                    if (app.isProtected) {
                        TagChip(stringResource(R.string.tag_protected), highlight = true)
                    }
                }
                ResourceUsageRow(app = app)
            }

            Spacer(Modifier.width(2.dp))

            if (!app.isProtected) {
                IconButton(
                    onClick  = onToggleWhitelist,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (app.isWhitelisted) R.drawable.ic_whitelist_on
                            else                   R.drawable.ic_whitelist_off
                        ),
                        contentDescription = stringResource(
                            if (app.isWhitelisted) R.string.main_remove_from_whitelist_hint
                            else                   R.string.main_add_to_whitelist_hint
                        ),
                        tint = if (app.isWhitelisted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick  = onKill,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_kill),
                        contentDescription = stringResource(R.string.main_kill_hint),
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(
                onClick  = onOverflow,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_more_vert),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}


@Composable
private fun AppIcon(app: AppModel, modifier: Modifier = Modifier) {
    val iconDrawable = app.appIcon
    if (iconDrawable != null) {
        val bitmap = iconDrawable.toBitmap(width = 96, height = 96)
        androidx.compose.foundation.Image(
            painter            = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = app.appName,
            modifier           = modifier.size(44.dp),
        )
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text      = (app.appName?.firstOrNull() ?: '?').uppercaseChar().toString(),
                fontSize  = 20.sp,
                fontWeight = FontWeight.Bold,
                color     = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}


@Composable
private fun ResourceUsageRow(app: AppModel, modifier: Modifier = Modifier) {
    val ramText = when {
        app.memoryUsageMb > 0 -> "${app.memoryUsageMb} MB"
        else                  -> "—"
    }
    val cpuText = when {
        app.cpuUsage > 0f -> "${"%.1f".format(app.cpuUsage)}%"
        else              -> null
    }

    Row(
        modifier          = modifier.padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_ram),
            contentDescription = null,
            modifier           = Modifier.size(12.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text     = ramText,
            fontSize = 12.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (cpuText != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter            = painterResource(R.drawable.ic_cpu),
                contentDescription = null,
                modifier           = Modifier.size(12.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text     = cpuText,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (app.isBlacklisted) {
            Spacer(Modifier.width(6.dp))
            Text(
                text      = stringResource(R.string.tag_blacklisted),
                fontSize  = 10.sp,
                color     = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}


@Composable
private fun TagChip(
    label: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg   = if (highlight) MaterialTheme.colorScheme.errorContainer
               else           MaterialTheme.colorScheme.surfaceVariant
    val fg   = if (highlight) MaterialTheme.colorScheme.onErrorContainer
               else           MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text       = label,
        fontSize   = 9.sp,
        fontWeight = FontWeight.Medium,
        color      = fg,
        modifier   = modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
