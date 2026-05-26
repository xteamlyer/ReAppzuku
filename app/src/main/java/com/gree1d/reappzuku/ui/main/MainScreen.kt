package com.gree1d.reappzuku.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gree1d.reappzuku.AppModel
import com.gree1d.reappzuku.R
import com.gree1d.reappzuku.ui.theme.LocalIsLightAccent
import com.gree1d.reappzuku.ui.theme.LocalOnAccentColor


enum class NavDestination { MAIN, SETTINGS, STATISTICS }

data class MainScreenState(
    val apps: List<AppModel>         = emptyList(),
    val isRefreshing: Boolean        = false,
    val ramPercent: Float            = 0f,       // 0..1
    val ramLabel: String             = "",        // e.g. "3.2 / 8 GB"
    val selectedCount: Int           = 0,
    val searchQuery: String          = "",
    val isSearchActive: Boolean      = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainScreenState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSortClick: () -> Unit,
    onScanClick: () -> Unit,
    onKillSelected: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onKillApp: (AppModel) -> Unit,
    onToggleWhitelist: (AppModel) -> Unit,
    onAppClick: (AppModel) -> Unit,
    onAppOverflow: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAccent     = LocalOnAccentColor.current
    val isLightAccent = LocalIsLightAccent.current
    val primary      = MaterialTheme.colorScheme.primary
    val hasSelection = state.selectedCount > 0
    val pullState    = rememberPullToRefreshState()
    val listState    = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainTopBar(
                title           = stringResource(R.string.app_name),
                isSearchActive  = state.isSearchActive,
                searchQuery     = state.searchQuery,
                hasSelection    = hasSelection,
                onSearchQueryChange  = onSearchQueryChange,
                onSearchActiveChange = onSearchActiveChange,
                onSelectAllToggle    = if (hasSelection) onDeselectAll else onSelectAll,
                onSortClick          = onSortClick,
                backgroundColor      = primary,
                contentColor         = onAccent,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !hasSelection,
                enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                AppNavigationBar(
                    current    = NavDestination.MAIN,
                    onNavigate = onNavigate,
                )
            }
        },
        floatingActionButton = {},
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                RamBar(
                    percent = state.ramPercent,
                    label   = state.ramLabel,
                )

                AppCountRow(
                    count       = state.apps.size,
                    onScanClick = onScanClick,
                )

                PullToRefreshBox(
                    state       = pullState,
                    isRefreshing = state.isRefreshing,
                    onRefresh   = onRefresh,
                    modifier    = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state    = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = state.apps,
                            key   = { _, app -> app.packageName }
                        ) { index, app ->
                            AppListItem(
                                app             = app,
                                onKill          = { onKillApp(app) },
                                onToggleWhitelist = { onToggleWhitelist(app) },
                                onClick         = { onAppClick(app) },
                                onOverflow      = { onAppOverflow(app) },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible  = hasSelection,
                enter    = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit     = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                KillButton(
                    count   = state.selectedCount,
                    onClick = onKillSelected,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    hasSelection: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectAllToggle: () -> Unit,
    onSortClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
) {
    if (isSearchActive) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query          = searchQuery,
                    onQueryChange  = onSearchQueryChange,
                    onSearch       = {},
                    expanded       = true,
                    onExpandedChange = onSearchActiveChange,
                    placeholder    = { Text(stringResource(R.string.main_search_hint)) },
                )
            },
            expanded         = true,
            onExpandedChange = onSearchActiveChange,
            modifier         = Modifier.fillMaxWidth(),
            content          = {},
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text       = title,
                    fontWeight = FontWeight.SemiBold,
                    color      = contentColor,
                )
            },
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector        = Icons.Default.Search,
                        contentDescription = stringResource(R.string.main_search_hint),
                        tint               = contentColor,
                    )
                }
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector        = Icons.Outlined.Sort,
                        contentDescription = null,
                        tint               = contentColor,
                    )
                }
                IconButton(onClick = onSelectAllToggle) {
                    Icon(
                        imageVector        = Icons.Outlined.SelectAll,
                        contentDescription = if (hasSelection)
                            stringResource(R.string.menu_deselect_all)
                        else
                            stringResource(R.string.menu_select_all),
                        tint = contentColor,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor    = backgroundColor,
                scrolledContainerColor = backgroundColor,
            ),
            windowInsets = TopAppBarDefaults.windowInsets,
        )
    }
}


@Composable
private fun RamBar(
    percent: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text      = label,
            fontSize  = 12.sp,
            fontWeight = FontWeight.Bold,
            color     = MaterialTheme.colorScheme.onSurface,
            modifier  = Modifier.padding(end = 6.dp),
        )
        LinearProgressIndicator(
            progress        = { percent },
            modifier        = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            strokeCap       = StrokeCap.Round,
            color           = MaterialTheme.colorScheme.primary,
            trackColor      = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}


@Composable
private fun AppCountRow(
    count: Int,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = stringResource(R.string.main_active_apps_count, count),
            fontSize = 18.sp,
            color    = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = androidx.compose.material.ripple.rememberRipple(bounded = true),
                    onClick           = onScanClick,
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_scan),
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text     = stringResource(R.string.scan_button_label),
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}


@Composable
private fun KillButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAccent = LocalOnAccentColor.current
    val label = if (count >= 2)
        stringResource(R.string.fab_kill_apps) + " ($count)"
    else
        stringResource(R.string.fab_kill_app) + " ($count)"

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RectangleShape)
            .clickable(onClick = onClick)
            .padding(bottom = navBarPadding)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            color      = onAccent,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}


@Composable
private fun AppNavigationBar(
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavBarEntry(
            labelRes   = R.string.nav_main,
            iconRes    = R.drawable.ic_main,
            selected   = current == NavDestination.MAIN,
            onClick    = { onNavigate(NavDestination.MAIN) },
        )
        NavBarEntry(
            labelRes   = R.string.nav_settings,
            iconRes    = R.drawable.ic_settings,
            selected   = current == NavDestination.SETTINGS,
            onClick    = { onNavigate(NavDestination.SETTINGS) },
        )
        NavBarEntry(
            labelRes   = R.string.nav_statistics,
            iconRes    = R.drawable.ic_stats,
            selected   = current == NavDestination.STATISTICS,
            onClick    = { onNavigate(NavDestination.STATISTICS) },
        )
    }
}

@Composable
private fun NavBarEntry(
    labelRes: Int,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon = {
            Icon(
                painter           = painterResource(iconRes),
                contentDescription = null,
            )
        },
        label = { Text(stringResource(labelRes)) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor       = MaterialTheme.colorScheme.primary,
            selectedTextColor       = MaterialTheme.colorScheme.primary,
            indicatorColor          = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor     = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}