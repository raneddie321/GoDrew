package com.godraw.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.data.db.FolderEntity
import com.godraw.app.data.db.ProjectEntity
import com.godraw.app.data.project.TemplateNames
import com.godraw.app.data.settings.ProjectSort
import com.godraw.app.ui.common.Checkerboard
import com.godraw.app.ui.common.GoDrawWordmark
import com.godraw.app.ui.common.SectionTitle
import com.godraw.app.ui.common.TierBadge
import com.godraw.app.ui.common.UpgradeDialog
import com.godraw.app.ui.common.rememberThumbnail
import com.godraw.app.ui.theme.GoDrawColors
import com.godraw.app.ui.theme.LocalGoDrawExtras
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Tier
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectTemplate
import com.godraw.core.model.ProjectType
import kotlinx.coroutines.launch

@Composable
fun rememberCreationNames(): CreationNames {
    val background = stringResource(R.string.layer_background)
    val first = stringResource(R.string.layer_default_name, 1)
    val untitled = stringResource(R.string.untitled_project)
    val panels = stringResource(R.string.layer_panels)
    val guide = stringResource(R.string.layer_guide)
    val storyboard = stringResource(R.string.layer_storyboard)
    val copy = stringResource(R.string.copy_suffix)
    return remember(background, first, untitled, panels, guide, storyboard, copy) {
        CreationNames(
            ProjectFactory.LayerNames(background, first, untitled),
            TemplateNames(panels, guide, storyboard),
            copy,
        )
    }
}

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenTutorials: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val names = rememberCreationNames()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var newProjectType by remember { mutableStateOf<ProjectType?>(null) }
    var newProjectTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    var renaming by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleting by remember { mutableStateOf<ProjectEntity?>(null) }
    var moving by remember { mutableStateOf<ProjectEntity?>(null) }
    var exportingId by remember { mutableStateOf<String?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportingId
        if (uri != null && id != null) vm.exportProject(id) { context.contentResolver.openOutputStream(uri) }
        exportingId = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importProject { context.contentResolver.openInputStream(uri) }
    }

    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            when (e) {
                is HomeEvent.OpenProject -> onOpenProject(e.id)
                is HomeEvent.Locked -> locked = e.access
                is HomeEvent.Message -> scope.launch {
                    snackbar.showSnackbar(if (e.arg != null) context.getString(e.text, e.arg) else context.getString(e.text))
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            fullWidth {
                HomeHeader(
                    tier = state.tier,
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    onSettings = onOpenSettings,
                    onTierClick = onOpenSubscription,
                )
            }
            fullWidth {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CreateCard(
                        title = stringResource(R.string.new_drawing),
                        subtitle = stringResource(R.string.new_drawing_subtitle),
                        icon = Icons.Filled.Brush,
                        brush = Brush.linearGradient(listOf(GoDrawColors.Cyan, GoDrawColors.Blue, GoDrawColors.Purple)),
                        modifier = Modifier.weight(1f),
                    ) { newProjectTemplate = null; newProjectType = ProjectType.DRAWING }
                    CreateCard(
                        title = stringResource(R.string.new_animation),
                        subtitle = stringResource(R.string.new_animation_subtitle),
                        icon = Icons.Filled.Animation,
                        brush = Brush.linearGradient(listOf(GoDrawColors.Magenta, GoDrawColors.Coral, GoDrawColors.Orange)),
                        modifier = Modifier.weight(1f),
                    ) { newProjectTemplate = null; newProjectType = ProjectType.ANIMATION }
                }
            }
            fullWidth {
                SearchAndSort(state, vm)
            }
            fullWidth {
                FolderRow(
                    folders = state.folders,
                    selected = state.selectedFolderId,
                    onSelect = vm::selectFolder,
                    onCreate = { creatingFolder = true },
                    onEdit = { editingFolder = it },
                )
            }
            if (state.query.isBlank()) {
                fullWidth { SectionTitle(stringResource(R.string.templates)) }
                fullWidth {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ProjectTemplate.entries) { t ->
                            TemplateCard(t) {
                                newProjectTemplate = t
                                newProjectType = t.type
                            }
                        }
                    }
                }
                if (state.recent.isNotEmpty() && state.selectedFolderId == null) {
                    fullWidth { SectionTitle(stringResource(R.string.recent_projects)) }
                    fullWidth {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.recent, key = { "recent_" + it.id }) { p ->
                                ProjectCard(
                                    container = container,
                                    project = p,
                                    modifier = Modifier.width(150.dp),
                                    onOpen = { onOpenProject(p.id) },
                                    onRename = { renaming = p },
                                    onDuplicate = { vm.duplicate(p, names) },
                                    onMove = { moving = p },
                                    onExport = { exportingId = p.id; exportLauncher.launch(p.name + ".godraw") },
                                    onDelete = { deleting = p },
                                )
                            }
                        }
                    }
                }
            }
            fullWidth {
                SectionTitle(stringResource(R.string.my_gallery)) {
                    val limit = state.maxProjects
                    Text(
                        if (limit != null) {
                            stringResource(R.string.project_count_limited, state.totalProjects, limit)
                        } else {
                            pluralStringResource(R.plurals.project_count, state.totalProjects, state.totalProjects)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.gallery.isEmpty()) {
                fullWidth { EmptyGallery(searching = state.query.isNotBlank()) }
            } else {
                items(state.gallery, key = { it.id }) { p ->
                    ProjectCard(
                        container = container,
                        project = p,
                        modifier = Modifier.padding(horizontal = 0.dp),
                        onOpen = { onOpenProject(p.id) },
                        onRename = { renaming = p },
                        onDuplicate = { vm.duplicate(p, names) },
                        onMove = { moving = p },
                        onExport = { exportingId = p.id; exportLauncher.launch(p.name + ".godraw") },
                        onDelete = { deleting = p },
                    )
                }
            }
            fullWidth {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(8.dp))
                    InfoCard(
                        icon = Icons.Filled.School,
                        title = stringResource(R.string.tutorials_title),
                        body = stringResource(R.string.tutorials_card_body),
                        onClick = onOpenTutorials,
                    )
                    if (state.tier != Tier.MAX) {
                        UpgradeCard(state.tier, onOpenSubscription)
                    }
                }
            }
        }
    }

    newProjectType?.let { type ->
        NewProjectSheet(
            initialType = type,
            template = newProjectTemplate,
            maxFps = state.maxFps,
            onDismiss = { newProjectType = null },
            onCreate = { spec ->
                newProjectType = null
                vm.createProject(spec, names)
            },
        )
    }
    locked?.let { access ->
        UpgradeDialog(access, null, onDismiss = { locked = null }, onUpgrade = { locked = null; onOpenSubscription() })
    }
    renaming?.let { p ->
        TextInputDialog(
            title = stringResource(R.string.rename_project),
            initial = p.name,
            onDismiss = { renaming = null },
            onConfirm = { vm.rename(p.id, it); renaming = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_project_title),
            message = stringResource(R.string.delete_project_message, p.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(p.id); deleting = null },
        )
    }
    moving?.let { p ->
        MoveToFolderDialog(
            folders = state.folders,
            current = p.folderId,
            onDismiss = { moving = null },
            onMove = { vm.move(p.id, it); moving = null },
        )
    }
    if (creatingFolder) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            onDismiss = { creatingFolder = false },
            onConfirm = { vm.createFolder(it); creatingFolder = false },
        )
    }
    editingFolder?.let { f ->
        FolderEditDialog(
            folder = f,
            onDismiss = { editingFolder = null },
            onRename = { vm.renameFolder(f, it); editingFolder = null },
            onDelete = { vm.deleteFolder(f.id); editingFolder = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun HomeHeader(tier: Tier, onImport: () -> Unit, onSettings: () -> Unit, onTierClick: () -> Unit) {
    val extras = LocalGoDrawExtras.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(extras.headerBrush)
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GoDrawWordmark()
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.clickable(onClick = onTierClick)) { TierBadge(tier) }
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.import_project))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
    }
}

@Composable
private fun CreateCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(18.dp)
            .height(112.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White)
            }
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchAndSort(state: HomeUiState, vm: HomeViewModel) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_projects)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ProjectSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (s) {
                                            ProjectSort.RECENT -> R.string.sort_recent
                                            ProjectSort.NAME -> R.string.sort_name
                                            ProjectSort.CREATED -> R.string.sort_created
                                            ProjectSort.SIZE -> R.string.sort_size
                                        },
                                    ),
                                    fontWeight = if (s == state.sort) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { vm.setSort(s); sortMenu = false },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectFilter.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { vm.setFilter(f) },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    ProjectFilter.ALL -> R.string.filter_all
                                    ProjectFilter.DRAWINGS -> R.string.filter_drawings
                                    ProjectFilter.ANIMATIONS -> R.string.filter_animations
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folders: List<FolderEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (FolderEntity) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_projects)) },
            )
        }
        items(folders, key = { it.id }) { f ->
            FilterChip(
                selected = selected == f.id,
                onClick = { onSelect(f.id) },
                label = { Text(f.name) },
                leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(16.dp)) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.folder_options),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onEdit(f) },
                    )
                },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCreate,
                label = { Text(stringResource(R.string.new_folder)) },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun TemplateCard(template: ProjectTemplate, onClick: () -> Unit) {
    val (title, icon) = templateInfo(template)
    Card(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (template.type == ProjectType.ANIMATION) GoDrawColors.drawBrush else GoDrawColors.gBrush),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White) }
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2)
            Text(
                "${template.width}×${template.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun templateInfo(template: ProjectTemplate): Pair<Int, ImageVector> = when (template) {
    ProjectTemplate.BLANK_SKETCH -> R.string.template_blank_sketch to Icons.Filled.Brush
    ProjectTemplate.COMIC_PAGE -> R.string.template_comic_page to Icons.Filled.Palette
    ProjectTemplate.STORYBOARD -> R.string.template_storyboard to Icons.Filled.Palette
    ProjectTemplate.FLIPBOOK -> R.string.template_flipbook to Icons.Filled.Animation
    ProjectTemplate.BOUNCING_BALL -> R.string.template_bouncing_ball to Icons.Filled.Animation
    ProjectTemplate.PIXEL_ART -> R.string.template_pixel_art to Icons.Filled.Palette
    ProjectTemplate.STICKER -> R.string.template_sticker to Icons.Filled.Brush
    ProjectTemplate.SOCIAL_LOOP -> R.string.template_social_loop to Icons.Filled.Animation
}

@Composable
private fun ProjectCard(
    container: AppContainer,
    project: ProjectEntity,
    modifier: Modifier,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val thumbFile = remember(project.id) { container.projects.thumbnailFile(project.id) }
    val thumb = rememberThumbnail(container.thumbnails, thumbFile, "${project.id}:${project.thumbnailVersion}")
    Card(
        onClick = onOpen,
        modifier = modifier.padding(horizontal = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Checkerboard(Modifier.fillMaxSize(), cell = 16f)
            if (thumb != null) {
                Image(thumb, contentDescription = project.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (project.type == ProjectType.ANIMATION.name) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.frame_count, project.frameCount, project.frameCount),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Row(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${project.width}×${project.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.project_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_project_file)) }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(searching: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (searching) Icons.Filled.Search else Icons.Filled.Add,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            stringResource(if (searching) R.string.no_search_results else R.string.empty_gallery_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!searching) {
            Text(
                stringResource(R.string.empty_gallery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpgradeCard(tier: Tier, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GoDrawColors.brandBrush)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (tier == Tier.FREE) R.string.upgrade_card_title_free else R.string.upgrade_card_title_pro),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (tier == Tier.FREE) R.string.upgrade_card_body_free else R.string.upgrade_card_body_pro),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
