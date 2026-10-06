package com.ss.tvcompose.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ss.tvcompose.data.Movie
import com.ss.tvcompose.data.MovieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Movie> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)

/**
 * ============================================================================
 *  SEARCH STATE
 * ============================================================================
 *
 * WHY DEBOUNCE MATTERS MORE ON TV THAN ON A PHONE
 * -----------------------------------------------
 * Every keypress here is a D-pad press on an on-screen keyboard. Users hold the
 * direction button and hammer OK, so keystrokes arrive in fast bursts. Firing a
 * query per keystroke means a stack of in-flight requests whose responses land
 * out of order, and the grid flickers between result sets.
 *
 * Two operators fix that, and they do different jobs -- you want both:
 *
 *   debounce(220)  waits for a gap in typing before searching at all. 220 ms is
 *                  tuned for D-pad cadence; the ~150 ms you would use for a
 *                  touch keyboard fires too eagerly here, and anything past
 *                  ~350 ms feels laggy.
 *
 *   mapLatest      cancels the previous search the moment a newer query starts.
 *                  This is what actually guarantees the displayed results match
 *                  the displayed query. debounce alone does not: a slow request
 *                  from three letters ago can still land last and win.
 *
 * The query itself lives in a MutableStateFlow rather than in the composable so
 * that it survives the search screen leaving composition -- which happens every
 * time the user opens a result and comes back.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            // NB: no distinctUntilChanged here -- queryFlow is a StateFlow, which
            // already conflates equal consecutive values. Adding the operator is
            // a no-op that the compiler now flags as an error.
            .debounce(220)
            .mapLatest { query ->
                if (query.isBlank()) {
                    SearchUiState(query = query)
                } else {
                    _state.value = _state.value.copy(isSearching = true)
                    SearchUiState(
                        query = query,
                        results = repository.search(query),
                        isSearching = false,
                        hasSearched = true
                    )
                }
            }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    /** Called by the on-screen keyboard for every accepted character. */
    fun append(char: Char) {
        val next = queryFlow.value + char
        queryFlow.value = next
        // The pill must echo the keypress on the very next frame. Waiting for
        // the debounced pipeline to update it would make the keyboard feel
        // broken -- a fifth of a second of nothing after pressing OK reads as a
        // dropped input, and the user presses again.
        _state.value = _state.value.copy(query = next)
    }

    fun backspace() {
        val next = queryFlow.value.dropLast(1)
        queryFlow.value = next
        _state.value = _state.value.copy(query = next)
    }

    fun clear() {
        queryFlow.value = ""
        _state.value = SearchUiState()
    }

    /** Skips the debounce, for the keyboard's Done key. */
    fun searchNow() {
        val query = queryFlow.value
        if (query.isBlank()) return
        _state.value = _state.value.copy(isSearching = true)
        viewModelScope.launch {
            runCatching { repository.search(query) }
                .onSuccess { results ->
                    _state.value = SearchUiState(
                        query = query,
                        results = results,
                        isSearching = false,
                        hasSearched = true
                    )
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.value = _state.value.copy(isSearching = false, hasSearched = true)
                }
        }
    }
}

