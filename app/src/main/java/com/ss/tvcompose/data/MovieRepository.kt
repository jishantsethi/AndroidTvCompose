package com.ss.tvcompose.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The single source of truth for catalogue data.
 *
 * It is an interface for one practical reason: the ViewModels above it never
 * learn where rows come from. Replace [InMemoryMovieRepository] with a Retrofit
 * or Room implementation and no ViewModel, screen or composable changes.
 */
interface MovieRepository {
    /** Every row the browse screen shows, in display order. */
    fun rows(): Flow<List<MovieRow>>

    /** Case-insensitive title/studio match. Empty query returns an empty list. */
    suspend fun search(query: String): List<Movie>

    /** Single lookup, used by the player when it is handed only an id. */
    suspend fun byId(id: String): Movie?
}

/**
 * A titled horizontal strip of cards. The browse screen is nothing more than a
 * vertical list of these -- which is the Compose equivalent of Leanback's
 * `ArrayObjectAdapter` of `ListRow`s, minus the presenter plumbing.
 */
data class MovieRow(
    val title: String,
    val movies: List<Movie>
)

/**
 * In-memory implementation over [MovieCatalog].
 *
 * The small [delay] in [rows] is not padding -- it forces the UI to render its
 * loading state at least once on every cold start. On TV that state is easy to
 * forget about and then ships broken, because on a fast dev machine real data
 * arrives before the first frame and you never see it.
 */
class InMemoryMovieRepository(
    private val catalog: List<Movie> = MovieCatalog.all
) : MovieRepository {

    override fun rows(): Flow<List<MovieRow>> = flow {
        delay(450)
        emit(
            buildList {
                // A hand-picked hero strip first -- the row a TV user lands on.
                add(MovieRow("Featured", catalog.take(5)))
                // Then one strip per drawer category, skipping any that are empty.
                Category.entries.forEach { category ->
                    val items = catalog.filter { it.category == category }
                    if (items.isNotEmpty()) add(MovieRow(category.label, items))
                }
                // Two derived strips, to prove rows are just queries.
                add(MovieRow("Adaptive: HLS", catalog.filter { it.streamType == StreamType.HLS }))
                add(MovieRow("Adaptive: DASH", catalog.filter { it.streamType == StreamType.DASH }))
            }
        )
    }

    override suspend fun search(query: String): List<Movie> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        // Simulated round trip, so the search screen's debounce is observable.
        delay(120)
        return catalog.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.studio.contains(q, ignoreCase = true) ||
                it.category.label.contains(q, ignoreCase = true)
        }
    }

    override suspend fun byId(id: String): Movie? = catalog.firstOrNull { it.id == id }
}
