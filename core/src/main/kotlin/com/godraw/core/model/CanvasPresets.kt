package com.godraw.core.model

enum class CanvasPreset(val width: Int, val height: Int) {
    HD_LANDSCAPE(1920, 1080),
    HD_PORTRAIT(1080, 1920),
    SQUARE(2048, 2048),
    SOCIAL_POST(1080, 1350),
    A4_PORTRAIT(2480, 3508),
    COMIC_PAGE(2480, 3720),
    WALLPAPER(1440, 3200),
    ANIMATION_SD(1280, 720),
    STICKER(512, 512),
    PIXEL_ART(128, 128),
    UHD_LANDSCAPE(3840, 2160),
}

/** Ready-made starting points shown on the home screen. */
enum class ProjectTemplate(
    val type: ProjectType,
    val width: Int,
    val height: Int,
    val fps: Int = 12,
    val background: Int = 0xFFFFFFFF.toInt(),
    val decoration: TemplateDecoration = TemplateDecoration.NONE,
) {
    BLANK_SKETCH(ProjectType.DRAWING, 2048, 2048),
    COMIC_PAGE(ProjectType.DRAWING, 2480, 3508, decoration = TemplateDecoration.COMIC_PANELS),
    STORYBOARD(ProjectType.DRAWING, 1920, 1080, decoration = TemplateDecoration.STORYBOARD),
    FLIPBOOK(ProjectType.ANIMATION, 1280, 720, fps = 12),
    BOUNCING_BALL(ProjectType.ANIMATION, 1280, 720, fps = 24, decoration = TemplateDecoration.BOUNCING_BALL_GUIDE),
    PIXEL_ART(ProjectType.DRAWING, 64, 64, decoration = TemplateDecoration.PIXEL_GRID),
    STICKER(ProjectType.DRAWING, 512, 512, background = 0),
    SOCIAL_LOOP(ProjectType.ANIMATION, 1080, 1080, fps = 15),
}

enum class TemplateDecoration { NONE, COMIC_PANELS, STORYBOARD, BOUNCING_BALL_GUIDE, PIXEL_GRID }
