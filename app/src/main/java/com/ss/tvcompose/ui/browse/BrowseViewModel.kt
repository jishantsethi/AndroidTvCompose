package com.ss.tvcompose.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ss.tvcompose.data.Category
import com.ss.tvcompose.data.MovieRepository
import com.ss.tvcompose.data.MovieRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the browse screen.
 *
 * A single sealed-ish data class rather than three separate flows (loading /
 * error / data). With three flows the UI can render impossible combinations --
 * "loading AND has an error AND has rows" -- and you will eventually ship one.
 * One object means the screen has exactly one thing to read and cannot get out
 * of sync with itself.
 */
data class BrowseUiState(
    val isLoading: Boolean = true,
    val rows: List<MovieRow> = emptyList(),
    val error: String? = null
)

class BrowseViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    /**
     * Remembers which category the rows were built for, so re-selecting the
     * same drawer section does not re-run the load and reset the user's scroll
     * position.
     */
    private var loadedFor: Category? = null
    private var hasLoaded = false

    fun load(categoryFilter: Category?) {
        if (hasLoaded && loadedFor == categoryFilter) return
        hasLoaded = true
        loadedFor = categoryFilter

        _state.value = BrowseUiState(isLoading = true)

        viewModelScope.launch {
            runCatching {
                // NOTE: collecting rather than first() -- the repository returns
                // a Flow, so a future Room- or network-backed implementation can
                // push updates and this screen will already handle them.
                repository.rows().collect { allRows ->
                    val rows = if (categoryFilter == null) {
                        allRows
                    } else {
                        // A category section shows only its own strip, plus any
                        // derived strip that still has matching content.
                        allRows
                            .map { row ->
                                row.copy(
                                    movies = row.movies.filter { it.category == categoryFilter }
                                )
                            }
                            .filter { it.movies.isNotEmpty() }
                    }
                    _state.value = BrowseUiState(isLoading = false, rows = rows)
                }
            }.onFailure { throwable ->
                // Never swallow cancellation: it is how coroutine scopes shut
                // down. Re-throwing keeps viewModelScope cancellation working;
                // catching it here would leak this collector past onCleared.
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                _state.value = BrowseUiState(
                    isLoading = false,
                    error = throwable.message ?: "Could not load the catalogue"
                )
            }
        }
    }
}
