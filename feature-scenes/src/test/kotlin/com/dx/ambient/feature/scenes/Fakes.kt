package com.dx.ambient.feature.scenes

import com.dx.ambient.domain.model.ImportedFolder
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import com.dx.ambient.domain.model.ProjectorSettings
import com.dx.ambient.domain.model.Scene
import com.dx.ambient.domain.repository.MediaLibraryRepository
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeSceneRepository(initial: List<Scene> = emptyList()) : SceneRepository {
    val scenes = MutableStateFlow(initial.associateBy { it.id })
    var duplicateCalledWith: String? = null
    var deleteCalledWith: String? = null

    override fun observeScenes(): Flow<List<Scene>> = scenes.map { it.values.toList() }
    override suspend fun getScene(id: String): Scene? = scenes.value[id]
    override suspend fun upsertScene(scene: Scene): Scene {
        val saved = if (scene.id.isBlank()) scene.copy(id = UUID.randomUUID().toString()) else scene
        scenes.value = scenes.value + (saved.id to saved)
        return saved
    }
    override suspend fun deleteScene(id: String) {
        deleteCalledWith = id
        scenes.value = scenes.value - id
    }
    override suspend fun duplicateScene(id: String): Scene? {
        duplicateCalledWith = id
        val original = scenes.value[id] ?: return null
        val copy = original.copy(id = UUID.randomUUID().toString(), name = "${original.name} (copy)")
        scenes.value = scenes.value + (copy.id to copy)
        return copy
    }
}

class FakeSettingsRepository(initial: ProjectorSettings = ProjectorSettings()) : SettingsRepository {
    val settings = MutableStateFlow(initial)
    override fun observeSettings(): Flow<ProjectorSettings> = settings
    override suspend fun update(transform: (ProjectorSettings) -> ProjectorSettings) {
        settings.value = transform(settings.value)
    }
    override suspend fun setLastSceneId(sceneId: String?) {
        settings.value = settings.value.copy(lastSceneId = sceneId)
    }
}

class FakeMediaLibraryRepository : MediaLibraryRepository {
    val media = MutableStateFlow<List<LibraryMedia>>(emptyList())
    val folders = MutableStateFlow<List<ImportedFolder>>(emptyList())
    override fun observeFolders(): Flow<List<ImportedFolder>> = folders
    override fun observeMedia(kind: MediaKind?): Flow<List<LibraryMedia>> =
        if (kind == null) media else media.map { list -> list.filter { it.kind == kind } }
    override suspend fun importFolder(treeUri: String): ImportedFolder =
        ImportedFolder(treeUri, "Folder").also { folders.value = folders.value + it }
    override suspend fun removeFolder(treeUri: String) {
        folders.value = folders.value.filterNot { it.treeUri == treeUri }
    }
    override suspend fun refresh() = Unit
}
