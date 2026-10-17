package com.godraw.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Searchable metadata for a project. The artwork itself lives in the project directory. */
@Entity(tableName = "projects", indices = [Index("folderId"), Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val frameCount: Int,
    val layerCount: Int,
    val folderId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Bumped whenever the thumbnail changes so image caches refresh. */
    val thumbnailVersion: Long,
    val sizeBytes: Long,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)
