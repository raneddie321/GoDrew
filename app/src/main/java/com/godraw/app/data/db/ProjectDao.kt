package com.godraw.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun allProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: String): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun projectCount(): Int

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, folderId: String?)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE projects SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)
}
