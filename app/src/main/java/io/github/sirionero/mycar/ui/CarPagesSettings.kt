package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.BuiltInApps
import io.github.sirionero.mycar.car.DriverLayouts
import io.github.sirionero.mycar.car.Entry
import io.github.sirionero.mycar.car.HomePage
import io.github.sirionero.mycar.car.HomePages
import io.github.sirionero.mycar.car.LayoutDiagram
import io.github.sirionero.mycar.car.PageContent
import io.github.sirionero.mycar.car.PageLayout
import io.github.sirionero.mycar.car.carAppIn
import io.github.sirionero.mycar.car.fitsBesideMap
import io.github.sirionero.mycar.car.label
import io.github.sirionero.mycar.car.moved
import io.github.sirionero.mycar.car.partName
import io.github.sirionero.mycar.car.rememberAllEntries
import io.github.sirionero.mycar.car.rememberHomePages
import io.github.sirionero.mycar.car.withLayout
import io.github.sirionero.mycar.car.withNewPage
import io.github.sirionero.mycar.car.withPlanciaInside
import io.github.sirionero.mycar.car.withSlot
import io.github.sirionero.mycar.car.without

/**
 * The pages beside the map, changed from the phone: the pages the pencil changes in the car
 * ([HomePages]), by the same rules - the map's page always there, one car app on a page, and what a
 * page shows kept, in order, when its layout changes. Ready-made until the first change, which
 * saves them all, as in the car.
 */
@Composable
internal fun CarPagesSection() {
    val pages = rememberHomePages()
    val saved by HomePages.saved.collectAsState()
    val entries = rememberAllEntries()
    // The page opened out to change, by id; the part, layout or page a dialog is about.
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var layoutOf by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    var replacing by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val separator = stringResource(R.string.common_list_separator)
    // What each page shows, in a line: its layout, then its parts.
    val summaries = pages.map { page ->
        val parts = page.slots.map { partName(it, entries) }
        stringResource(page.layout.label) + " · " + parts.joinToString(separator)
    }

    SettingsSection(
        stringResource(R.string.car_pages_settings_title),
        header = AnnotatedString(stringResource(R.string.settings_car_pages_header)),
    ) {
        pages.forEachIndexed { index, page ->
            if (index > 0) SettingsDivider()
            if (page.layout == PageLayout.MAP) {
                DiagramRow(stringResource(R.string.car_pages_dot_map), leading = { Diagram(page.layout, scheme.outline) })
            } else {
                val opened = open == page.id
                DiagramRow(
                    stringResource(R.string.car_pages_dot_page, index + 1),
                    Modifier.clickable { open = if (opened) null else page.id },
                    subtitle = summaries[index],
                    leading = { Diagram(page.layout, scheme.primary) },
                    trailing = {
                        Icon(
                            if (opened) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.car_pages_change),
                            tint = scheme.onSurfaceVariant,
                        )
                    },
                )
                if (opened) PageChanges(page, index, pages, entries, onLayout = { layoutOf = page.id }, onPart = { choosing = page.id to it }, onDelete = { deleting = page.id })
            }
        }
        SettingsDivider()
        DiagramRow(
            stringResource(R.string.car_pages_add),
            Modifier.clickable {
                // Just right of the map's page, the first a swipe from the map reaches, as in the car.
                pages.firstOrNull { it.layout == PageLayout.MAP }?.let { map ->
                    val (changed, id) = pages.withNewPage(map.id)
                    HomePages.save(changed)
                    open = id
                }
            },
            leading = { RowIcon(Icons.Filled.Add, scheme.primary) },
        )
        SettingsDivider()
        val mine = saved != null
        DiagramRow(
            stringResource(R.string.car_pages_ready_made),
            if (mine) Modifier.clickable { replacing = true } else Modifier,
            leading = { RowIcon(Icons.Filled.Refresh, if (mine) scheme.primary else scheme.onSurfaceVariant) },
            titleColor = if (mine) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }

    // The part a choice is about, while its page is still there.
    choosing?.let { (pageId, slot) -> pages.find { it.id == pageId }?.takeIf { slot < it.slots.size }?.let { page -> page to slot } }
        ?.let { (page, slot) ->
            PartDialog(page, slot, entries, onPick = {
                HomePages.save(pages.withSlot(page.id, slot, it))
                choosing = null
            }, onDismiss = { choosing = null })
        }

    layoutOf?.let { id -> pages.find { it.id == id } }?.let { page ->
        val names = DriverLayouts.map { stringResource(it.label) }
        PickDialog(
            stringResource(R.string.car_pages_layout),
            DriverLayouts.mapIndexed { i, layout -> Pick(layout, names[i]) },
            page.layout,
            onPick = {
                HomePages.save(pages.withLayout(page.id, it))
                layoutOf = null
            },
            onDismiss = { layoutOf = null },
            note = stringResource(R.string.car_pages_layout_keeps),
            leading = { Diagram(it, scheme.primary) },
        )
    }

    deleting?.let { id -> pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } }?.let { index ->
        val page = pages[index]
        ConfirmDialog(
            title = stringResource(R.string.car_pages_delete),
            text = stringResource(R.string.car_pages_dot_page, index + 1) + ": " + summaries[index],
            confirm = stringResource(R.string.car_pages_delete_confirm),
            onConfirm = {
                HomePages.save(pages.without(page.id))
                if (open == page.id) open = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (replacing) {
        ConfirmDialog(
            title = stringResource(R.string.car_pages_ready_made),
            text = stringResource(R.string.settings_car_pages_ready_text),
            confirm = stringResource(R.string.car_pages_replace),
            onConfirm = {
                HomePages.reset()
                open = null
            },
            onDismiss = { replacing = false },
        )
    }
}

/** A page opened out: its layout, each of its parts, and moving or deleting it. */
@Composable
private fun PageChanges(
    page: HomePage,
    index: Int,
    pages: List<HomePage>,
    entries: List<Entry>,
    onLayout: () -> Unit,
    onPart: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 24.dp)) {
        DiagramRow(
            stringResource(R.string.car_pages_layout),
            Modifier.clickable(onClick = onLayout),
            subtitle = stringResource(page.layout.label),
            leading = { Diagram(page.layout, scheme.primary) },
        )
        // Each part with its place lit in the picture of the page, and what it shows.
        page.slots.forEachIndexed { slot, content ->
            DiagramRow(
                partName(content, entries),
                Modifier.clickable { onPart(slot) },
                leading = { Diagram(page.layout, scheme.outlineVariant, lit = slot, litColor = scheme.primary) },
                titleColor = if (content == null) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
        val left = index > 0
        val right = index < pages.lastIndex
        DiagramRow(
            stringResource(R.string.car_pages_move_left),
            if (left) Modifier.clickable { HomePages.save(pages.moved(page.id, -1)) } else Modifier,
            leading = { RowIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (left) scheme.primary else scheme.onSurfaceVariant) },
            titleColor = if (left) scheme.onSurface else scheme.onSurfaceVariant,
        )
        DiagramRow(
            stringResource(R.string.car_pages_move_right),
            if (right) Modifier.clickable { HomePages.save(pages.moved(page.id, 1)) } else Modifier,
            leading = { RowIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (right) scheme.primary else scheme.onSurfaceVariant) },
            titleColor = if (right) scheme.onSurface else scheme.onSurfaceVariant,
        )
        DiagramRow(
            stringResource(R.string.car_pages_delete),
            Modifier.clickable(onClick = onDelete),
            leading = { RowIcon(Icons.Filled.Delete, scheme.error) },
            titleColor = scheme.error,
        )
    }
}

/**
 * What can go in part [slot] of [page]: the widgets, the browser, Plancia inside myCar, the car
 * apps that fit, or nothing. One car app a page: with another part running one, the others wait.
 */
@Composable
private fun PartDialog(page: HomePage, slot: Int, entries: List<Entry>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val current = page.slots.getOrNull(slot)
    val otherApp = page.slots.withIndex().firstOrNull { (i, id) -> i != slot && carAppIn(id, entries) != null }?.value
    val apps = entries.filter { it.fitsBesideMap && it.template != null && it.componentId != null }
        .sortedBy { it.label.lowercase() }
    val appNames = apps.map { it.shownLabel }
    val plancia = entries.any { it.builtIn == BuiltInApps.Plancia }
    // The Plancia app saved in a part reads as Plancia inside myCar, once that's set up.
    val selected = if (plancia && withPlanciaInside(current, entries) == PageContent.PLANCIA) PageContent.PLANCIA else current
    val options = buildList<Pick<String?>> {
        add(Pick(PageContent.WEATHER, stringResource(R.string.car_weather_title)))
        add(Pick(PageContent.AGENDA, stringResource(R.string.car_pages_agenda)))
        add(Pick(PageContent.BROWSER, stringResource(R.string.car_apps_browser)))
        if (plancia) add(Pick(PageContent.PLANCIA, "Plancia"))
        apps.forEachIndexed { i, app ->
            val id = app.componentId!!
            add(Pick(id, appNames[i], enabled = otherApp == null || otherApp == id))
        }
        add(Pick(null, stringResource(R.string.car_pages_nothing)))
    }
    PickDialog(
        stringResource(R.string.car_pages_show_here),
        options,
        selected,
        onPick = onPick,
        onDismiss = onDismiss,
        note = otherApp?.let { stringResource(R.string.car_pages_one_app, partName(it, entries)) },
    )
}

/** A row of the pages' card: like [SettingsRow], with a picture of a page (or an icon) in front. */
@Composable
private fun DiagramRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(DIAGRAM_WIDTH), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.let { Box { it() } }
    }
}

/** A page's layout in small, its parts in [color] - or part [lit] alone in [litColor]. */
@Composable
private fun Diagram(layout: PageLayout, color: Color, lit: Int = -1, litColor: Color = color) {
    LayoutDiagram(layout, { if (it == lit) litColor else color }, Modifier.size(width = DIAGRAM_WIDTH, height = DIAGRAM_HEIGHT))
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) = Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))

private val DIAGRAM_WIDTH = 32.dp
private val DIAGRAM_HEIGHT = 20.dp
