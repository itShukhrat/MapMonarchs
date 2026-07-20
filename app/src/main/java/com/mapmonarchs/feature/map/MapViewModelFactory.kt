package com.mapmonarchs.feature.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mapmonarchs.data.local.DeliveryProofStore
import com.mapmonarchs.data.remote.SupabaseProvider
import com.mapmonarchs.data.repository.MapboxWorldBoundaryRepository
import com.mapmonarchs.data.repository.SupabaseAuthRepository
import com.mapmonarchs.data.repository.SupabaseTerritoryRepository
import com.mapmonarchs.domain.validation.MovementValidator
import com.mapmonarchs.location.LocationTracker

/**
 * Manual composition root. A DI framework would add a dependency and build time
 * without removing real complexity at this size. Introduce one when the graph
 * actually branches.
 */
class MapViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java)) {
            "Unknown ViewModel: ${modelClass.name}"
        }
        val client = SupabaseProvider.client
        return MapViewModel(
            locationTracker = LocationTracker(appContext),
            validator = MovementValidator(),
            repository = SupabaseTerritoryRepository(client),
            authRepository = SupabaseAuthRepository(client),
            deliveryProofStore = DeliveryProofStore(appContext),
            worldBoundaryRepository = MapboxWorldBoundaryRepository()
        ) as T
    }
}