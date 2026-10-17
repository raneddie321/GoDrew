package com.godraw.core.model

/** Creates new documents with sensible defaults. */
object ProjectFactory {

    const val MIN_CANVAS = 16
    const val MAX_CANVAS = 8192

    fun create(
        name: String,
        type: ProjectType,
        width: Int,
        height: Int,
        backgroundColor: Int,
        fps: Int = 12,
        layerNames: LayerNames = LayerNames(),
        now: Long = System.currentTimeMillis(),
    ): ProjectDocument {
        require(width in MIN_CANVAS..MAX_CANVAS && height in MIN_CANVAS..MAX_CANVAS) { "Invalid canvas size" }
        val layers = buildList {
            if (backgroundColor != 0) {
                add(
                    Layer(
                        id = Ids.newId(),
                        name = layerNames.background,
                        isBackground = true,
                        isStatic = type == ProjectType.ANIMATION,
                        locked = true,
                    ),
                )
            }
            add(Layer(id = Ids.newId(), name = layerNames.firstLayer))
        }
        return ProjectDocument(
            id = Ids.newId(),
            name = name.trim().ifEmpty { layerNames.untitled },
            type = type,
            width = width,
            height = height,
            backgroundColor = backgroundColor,
            fps = fps.coerceIn(1, 60),
            layers = layers,
            frames = listOf(Frame(Ids.newId())),
            activeLayerId = layers.last().id,
            onionSkin = OnionSkinSettings(enabled = type == ProjectType.ANIMATION),
            createdAt = now,
            modifiedAt = now,
        )
    }

    /** Localized default names supplied by the UI layer. */
    data class LayerNames(
        val background: String = "Background",
        val firstLayer: String = "Layer 1",
        val untitled: String = "Untitled",
    )
}
