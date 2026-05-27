package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BadgeSystemText     = Color(0xFF1A5276)
private val BadgeSystemBg       = Color(0xFFD6EAF8)
private val BadgePersistentText = Color(0xFF6C3483)
private val BadgePersistentBg   = Color(0xFFF5EEF8)

private const val ALPHA_PROTECTED   = 0.4f
private const val ALPHA_WHITELISTED = 0.85f
private const val SWIPE_THRESHOLD   = 200f   // px до срабатывания kill

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppModel,
    selectionMode: Boolean,
    onKillApp: () -> Unit,
    onAppClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = when {
        app.isProtected   -> ALPHA_PROTECTED
        app.isWhitelisted -> ALPHA_WHITELISTED
        else              -> ALPHA_NORMAL
    }

    var expanded by remember(app.packageName) { mutableStateOf(false) }

    // ── Swipe-to-kill ────────────────────────────────────────────────────────
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope   = rememberCoroutineScope()
    val swipeAlpha by animateFloatAsState(
        targetValue = (offsetX / SWIPE_THRESHOLD).coerceIn(0f, 1f),
        label       = "swipeAlpha",
    )
    val animatedOffset by animateFloatAsState(
        targetValue   = offsetX,
        animationSpec = tween(100),
        label         = "offset",
    )

    // ── Динамический цвет из иконки (только когда раскрыто) ─────────────────
    val paletteColor: Color? = remember(app.packageName, expanded) {
        if (!expanded) return@remember null
        try {
            val bmp     = app.appIcon?.toBitmap(64, 64) ?: return@remember null
            val palette = Palette.from(bmp).generate()
            val argb    = palette.getDominantColor(0)
            if (argb == 0) null else Color(argb).copy(alpha = 0.15f)
        } catch (_: Exception) { null }
    }

    val expandedBg by animateColorAsState(
        targetValue = if (expanded && paletteColor != null) paletteColor
                      else Color.Transparent,
        animationSpec = tween(300),
        label = "expandedBg",
    )

    Box(modifier = modifier.fillMaxWidth()) {

        // ── Kill hint под строкой (красный фон при свайпе) ───────────────────
        if (!app.isProtected && !app.isWhitelisted && !selectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = swipeAlpha),
                                Color.Transparent,
                            )
                        )
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_force_stop),
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = swipeAlpha),
                    modifier           = Modifier
                        .padding(start = 16.dp)
                        .size(24.dp),
                )
            }
        }

        // ── Сама строка ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .background(expandedBg)
                .then(
                    if (!app.isProtected && !app.isWhitelisted && !selectionMode) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state       = rememberDraggableState { delta ->
                                offsetX = (offsetX + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { _ ->
                                scope.launch {
                                    if (offsetX >= SWIPE_THRESHOLD) {
                                        onKillApp()
                                    }
                                    offsetX = 0f
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                if (!app.isProtected && !app.isWhitelisted) onAppClick()
                            } else {
                                expanded = !expanded
                            }
                        },
                        onLongClick = {
                            if (!selectionMode && !app.isProtected && !app.isWhitelisted) {
                                onAppClick()
                            }
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Иконка скруглённая ───────────────────────────────────
                AppIcon(app = app, alpha = contentAlpha)

                Spacer(Modifier.width(12.dp))

                // ── Текст ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha),
                ) {
                    // Имя + значки
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
                        if (app.isProtected) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_protected),
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.padding(start = 4.dp).size(14.dp),
                            )
                        }
                        if (app.isWhitelisted) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_whitelist),
                                contentDescription = null,
                                tint               = Color(0xFF4CAF50),
                                modifier           = Modifier.padding(start = 6.dp).size(14.dp),
                            )
                        }
                    }

                    // RAM + CPU + badges в одну строку
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(top = 2.dp),
                    ) {
                        val ramText = app.appRam?.takeIf { it.isNotEmpty() }
                        if (ramText != null) {
                            Text(
                                text     = stringResource(R.string.app_ram_label, ramText),
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val cpuText = app.cpuUsage?.takeIf { it.isNotEmpty() }
                        if (cpuText != null) {
                            Text(
                                text     = "  ·  $cpuText",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            app.isPersistentApp -> {
                                Spacer(Modifier.width(6.dp))
                                Badge("Persistent", BadgePersistentText, BadgePersistentBg)
                            }
                            app.isSystemApp -> {
                                Spacer(Modifier.width(6.dp))
                                Badge("System", BadgeSystemText, BadgeSystemBg)
                            }
                        }
                    }
                }

                // ── Кнопка действия ─────────────────────────────────────
                when {
                    app.isProtected || app.isWhitelisted ->
                        Spacer(Modifier.size(40.dp))
                    selectionMode -> {
                        IconButton(onClick = onAppClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(
                                    if (app.isSelected) R.drawable.ic_checkbox_checked
                                    else                R.drawable.ic_checkbox_unchecked
                                ),
                                contentDescription = null,
                                tint = if (app.isSelected) MaterialTheme.colorScheme.error
                                       else                MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    else -> {
                        // Кнопка overflow для меню
                        IconButton(onClick = onOverflowClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_more_vert),
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // ── Раскрытая панель с деталями ──────────────────────────────
            AnimatedVisibility(
                visible = expanded && !selectionMode,
                enter   = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit    = shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
                ExpandedDetails(app = app, onKill = onKillApp)
            }

            // Разделитель
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Раскрытая панель деталей
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedDetails(app: AppModel, onKill: () -> Unit) {
    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 12.dp, bottom = 10.dp),
        shape         = RoundedCornerShape(12.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

            // Package
            DetailRow(label = "Package", value = app.packageName)

            // Статус
            val statusText = when {
                app.isProtected   -> stringResource(R.string.settings_mode_whitelist)
                app.isWhitelisted -> stringResource(R.string.settings_mode_whitelist)
                app.isSystemApp   -> "System app"
                app.isPersistentApp -> "Persistent"
                else              -> "Background"
            }
            DetailRow(label = "Status", value = statusText)

            // RAM
            val ram = app.appRam?.takeIf { it.isNotEmpty() }
            if (ram != null) DetailRow(label = "RAM", value = ram)

            // CPU
            val cpu = app.cpuUsage?.takeIf { it.isNotEmpty() }
            if (cpu != null) DetailRow(label = "CPU", value = cpu)

            // Background restriction
            if (app.isBackgroundRestricted) {
                DetailRow(
                    label = "Restriction",
                    value = stringResource(R.string.restriction_badge_hard),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }

            // Kill кнопка внизу панели (только для не-защищённых)
            if (!app.isProtected && !app.isWhitelisted) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        onClick = onKill,
                        shape   = RoundedCornerShape(8.dp),
                        color   = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_force_stop),
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onErrorContainer,
                                modifier           = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text     = stringResource(R.string.fab_kill_app),
                                fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            fontSize = 12.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text     = value,
            fontSize = 12.sp,
            color    = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App icon — скруглённая как Android 13+
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppIcon(app: AppModel, alpha: Float) {
    val drawable = app.appIcon
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap(144, 144) }
        Image(
            painter            = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = app.appName,
            modifier           = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))   // скруглённые иконки
                .alpha(alpha),
        )
    } else {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = (app.appName?.firstOrNull() ?: '?').uppercaseChar().toString(),
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Badge(text: String, textColor: Color, bgColor: Color) {
    Text(
        text       = text,
        fontSize   = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color      = textColor,
        modifier   = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private const val ALPHA_NORMAL = 1.0f
