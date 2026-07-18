package com.mapmonarchs.feature.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mapmonarchs.data.remote.SupabaseProvider
import com.mapmonarchs.data.repository.SupabaseTerritoryRepository
import com.mapmonarchs.domain.validation.MovementValidator
import com.mapmonarchs.location.LocationTracker

/**
 * Manual composition root.
 *
 * A DI framework is deliberately not used yet: at this size it would add a
 * dependency and build time without removing any real complexity. Introduce one
 * when the graph actually branches, not before.
 */
class MapViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java)) {
            "Unknown ViewModel: ${modelClass.name}"
        }
        return MapViewModel(
            locationTracker = LocationTracker(appContext),
            validator = MovementValidator(),
            repository = SupabaseTerritoryRepository(SupabaseProvider.client)
        ) as T
    }
}