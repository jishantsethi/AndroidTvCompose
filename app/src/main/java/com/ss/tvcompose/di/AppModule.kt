package com.ss.tvcompose.di

import com.ss.tvcompose.data.InMemoryMovieRepository
import com.ss.tvcompose.data.MovieRepository
import com.ss.tvcompose.ui.browse.BrowseViewModel
import com.ss.tvcompose.ui.player.PlayerViewModel
import com.ss.tvcompose.ui.search.SearchViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin graph. Deliberately tiny -- the point of showing DI here is that the
 * ViewModels never construct their own dependencies, so each screen can be
 * exercised against a fake repository.
 *
 * [PlayerViewModel] is the interesting one: it takes a runtime argument (the
 * movie id from the navigation route) alongside its injected dependencies. That
 * is what the `params` lambda is for -- the call site passes `parametersOf(id)`
 * and Koin threads it through. The alternative is a SavedStateHandle; both are
 * fine, but `params` keeps the ViewModel's constructor honest about what it
 * needs instead of hiding it behind string keys.
 */
val appModule = module {

    single<MovieRepository> { InMemoryMovieRepository() }

    viewModel { BrowseViewModel(get()) }

    viewModel { SearchViewModel(get()) }

    viewModel { params ->
        PlayerViewModel(
            application = androidApplication(),
            repository = get(),
            movieId = params.get()
        )
    }
}
