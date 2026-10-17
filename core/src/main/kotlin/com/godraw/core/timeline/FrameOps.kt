package com.godraw.core.timeline

import com.godraw.core.model.Frame
import com.godraw.core.model.Ids

/**
 * Pure frame-list operations. Each returns the new frame list together with the cell copies the
 * caller must perform (source frame id → new frame id) so drawings follow their frames.
 */
object FrameOps {

    data class Result(
        val frames: List<Frame>,
        /** Index that should become active after the operation. */
        val activeIndex: Int,
        val cellCopies: List<Pair<String, String>> = emptyList(),
        val removedFrameIds: List<String> = emptyList(),
    )

    fun insertBlank(frames: List<Frame>, afterIndex: Int, idProvider: () -> String = Ids::newId): Result {
        val at = (afterIndex + 1).coerceIn(0, frames.size)
        val newFrame = Frame(idProvider())
        return Result(frames.toMutableList().apply { add(at, newFrame) }, at)
    }

    fun duplicate(frames: List<Frame>, indices: List<Int>, idProvider: () -> String = Ids::newId): Result {
        val sorted = indices.filter { it in frames.indices }.distinct().sorted()
        if (sorted.isEmpty()) return Result(frames, 0)
        val copies = ArrayList<Pair<String, String>>()
        val newFrames = sorted.map { i ->
            val src = frames[i]
            val dst = Frame(idProvider(), src.hold)
            copies += src.id to dst.id
            dst
        }
        val insertAt = sorted.last() + 1
        val result = frames.toMutableList().apply { addAll(insertAt, newFrames) }
        return Result(result, insertAt, copies)
    }

    /** Deletes frames; at least one frame always remains. */
    fun delete(frames: List<Frame>, indices: List<Int>, idProvider: () -> String = Ids::newId): Result {
        val toRemove = indices.filter { it in frames.indices }.toSet()
        if (toRemove.isEmpty()) return Result(frames, 0)
        val removed = frames.filterIndexed { i, _ -> i in toRemove }.map { it.id }
        var remaining = frames.filterIndexed { i, _ -> i !in toRemove }
        if (remaining.isEmpty()) remaining = listOf(Frame(idProvider()))
        val active = (toRemove.min() - 1).coerceIn(0, remaining.lastIndex)
        return Result(remaining, active, removedFrameIds = removed)
    }

    /** Moves a contiguous or scattered selection so that it starts at [targetIndex] (in the final list). */
    fun move(frames: List<Frame>, indices: List<Int>, targetIndex: Int): Result {
        val sorted = indices.filter { it in frames.indices }.distinct().sorted()
        if (sorted.isEmpty()) return Result(frames, 0)
        val moving = sorted.map { frames[it] }
        val rest = frames.filterIndexed { i, _ -> i !in sorted }.toMutableList()
        val at = targetIndex.coerceIn(0, rest.size)
        rest.addAll(at, moving)
        return Result(rest, at)
    }

    /** Pastes copies of [clipboard] frames after [afterIndex]. */
    fun paste(frames: List<Frame>, clipboard: List<Frame>, afterIndex: Int, idProvider: () -> String = Ids::newId): Result {
        if (clipboard.isEmpty()) return Result(frames, afterIndex.coerceIn(0, frames.lastIndex))
        val copies = ArrayList<Pair<String, String>>()
        val pasted = clipboard.map { src ->
            val dst = Frame(idProvider(), src.hold)
            copies += src.id to dst.id
            dst
        }
        val at = (afterIndex + 1).coerceIn(0, frames.size)
        return Result(frames.toMutableList().apply { addAll(at, pasted) }, at, copies)
    }

    fun setHold(frames: List<Frame>, index: Int, hold: Int): List<Frame> =
        frames.mapIndexed { i, f -> if (i == index) f.copy(hold = hold.coerceIn(1, 120)) else f }
}
