package ru.kaufd.bodega.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import ru.kaufd.bodega.domain.EntryFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterStateHolder @Inject constructor() {
    val filter = MutableStateFlow(EntryFilter())
}
