package com.godraw.core.model

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}
