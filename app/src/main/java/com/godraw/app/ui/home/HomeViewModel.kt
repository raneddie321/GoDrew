package com.godraw.app.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.data.db.FolderEntity
import com.godraw.app.data.db.ProjectEntity
import com.godraw.app.data.project.TemplateNames
import com.godraw.app.data.project.TemplateRenderer
import com.godraw.app.data.settings.ProjectSort
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Tier
import com.godraw.core.model.CellKey
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectTemplate
import com.godraw.core.model.ProjectType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

enum class ProjectFilter { ALL, DRAWINGS, ANIMATIONS }

data class HomeUiState(
    val gallery: List<ProjectEntity> = emptyList(),
    val recent: List<ProjectEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null,
    val query: String = "",
    val filter: ProjectFilter = ProjectFilter.ALL,
    val sort: ProjectSort = ProjectSort.RECENT,
    val tier: Tier = Tier.FREE,
    val totalProjects: Int = 0,
    val maxProjects: Int? = null,
    val maxFps: Int = 24,
)

data class NewProjectSpec(
    val name: String,
    val type: ProjectType,
    val width: Int,
    val height: Int,
    val background: Int,
    val fps: Int,
    val template: ProjectTemplate? = null,
)

sealed interface HomeEvent {
    data class OpenProject(val id: String) : HomeEvent
    data class Locked(val access: Access.Locked) : HomeEvent
    data class Message(val text: Int, val arg: String? = null) : HomeEvent
}

/** Localized default names the ViewModel needs when creating documents. */
data class CreationNames(
    val layers: ProjectFactory.LayerNames,
    val template: TemplateNames,
    val copySuffix: String,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.projects
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ProjectFilter.ALL)
    private val folder = MutableStateFlow<String?>(null)

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private data class Controls(val query: String, val filter: ProjectFilter, val folder: String?, val sort: ProjectSort)

    private val controls = combine(query, filter, folder, container.settings.settings) { q, f, fo, s ->
        Controls(q, f, fo, s.projectSort)
    }

    val state: StateFlow<HomeUiState> = combine(
        repo.projects,
        repo.folders,
        controls,
        container.entitlements.gate,
    ) { projects, folders, c, gate ->
        val q = c.query.trim()
        val filtered = projects
            .asSequence()
            .filter { c.folder == null || it.folderId == c.folder }
            .filter {
                when (c.filter) {
                    ProjectFilter.ALL -> true
                    ProjectFilter.DRAWINGS -> it.type == ProjectType.DRAWING.name
                    ProjectFilter.ANIMATIONS -> it.type == ProjectType.ANIMATION.name
                }
            }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .let { seq ->
                when (c.sort) {
                    ProjectSort.RECENT -> seq.sortedByDescending { it.updatedAt }
                    ProjectSort.NAME -> seq.sortedBy { it.name.lowercase() }
                    ProjectSort.CREATED -> seq.sortedByDescending { it.createdAt }
                    ProjectSort.SIZE -> seq.sortedByDescending { it.sizeBytes }
                }
            }
            .toList()
        HomeUiState(
            gallery = filtered,
            recent = projects.sortedByDescending { it.updatedAt }.take(8),
            folders = folders,
            selectedFolderId = c.folder,
            query = c.query,
            filter = c.filter,
            sort = c.sort,
            tier = gate.tier,
            totalProjects = projects.size,
            maxProjects = gate.limits.maxProjects,
            maxFps = gate.limits.maxFps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: ProjectFilter) { filter.value = value }
    fun selectFolder(id: String?) { folder.value = id }
    fun setSort(sort: ProjectSort) = viewModelScope.launch { container.settings.setProjectSort(sort) }

    fun createProject(spec: NewProjectSpec, names: CreationNames) = viewModelScope.launch {
        val gate = container.entitlements.currentGate
        val access = gate.canCreateProject(repo.projectCount())
        if (access is Access.Locked) {
            events.send(HomeEvent.Locked(access))
            return@launch
        }
        runCatching {
            var doc = ProjectFactory.create(
                name = spec.name,
                type = spec.type,
                width = spec.width,
                height = spec.height,
                backgroundColor = spec.background,
                fps = gate.clampFps(spec.fps),
                layerNames = names.layers,
            )
            var cells: Map<CellKey, Bitmap> = emptyMap()
            spec.template?.let { t ->
                val (d, c) = TemplateRenderer.apply(doc, t.decoration, names.template)
                doc = d
                cells = c
            }
            repo.create(doc, cells, folderId = folder.value)
        }.onSuccess { id ->
            events.send(HomeEvent.OpenProject(id))
        }.onFailure {
            events.send(HomeEvent.Message(R.string.error_create_project))
        }
    }

    fun rename(id: String, name: String) = viewModelScope.launch { repo.rename(id, name) }

    fun duplicate(project: ProjectEntity, names: CreationNames) = viewModelScope.launch {
        val access = container.entitlements.currentGate.canCreateProject(repo.projectCount())
        if (access is Access.Locked) {
            events.send(HomeEvent.Locked(access))
            return@launch
        }
        runCatching { repo.duplicate(project.id, project.name + names.copySuffix) }
            .onFailure { events.send(HomeEvent.Message(R.string.error_generic)) }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun move(id: String, folderId: String?) = viewModelScope.launch { repo.move(listOf(id), folderId) }

    fun createFolder(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.createFolder(name)
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.renameFolder(folder, name)
    }

    fun deleteFolder(id: String) = viewModelScope.launch {
        repo.deleteFolder(id)
        if (folder.value == id) folder.value = null
    }

    fun exportProject(id: String, out: () -> OutputStream?) = viewModelScope.launch {
        val result = runCatching {
            val stream = out() ?: error("No output")
            stream.use { repo.exportProject(id, it) }
        }
        events.send(HomeEvent.Message(if (result.isSuccess) R.string.project_exported else R.string.error_export_project))
    }

    fun importProject(input: () -> InputStream?) = viewModelScope.launch {
        val access = container.entitlements.currentGate.canCreateProject(repo.projectCount())
        if (access is Access.Locked) {
            events.send(HomeEvent.Locked(access))
            return@launch
        }
        val result = runCatching {
            val stream = input() ?: error("No input")
            stream.use { repo.importProject(it) }
        }
        result.onSuccess { events.send(HomeEvent.OpenProject(it)) }
            .onFailure { events.send(HomeEvent.Message(R.string.error_import_project)) }
    }
}
