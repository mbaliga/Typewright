// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An [AppConfigStore] held in memory, for [ProjectWorkspace] tests (no real file access needed). */
class InMemoryAppConfigStore : AppConfigStore {
    private val mutex = Mutex()
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(name: String): ByteArray? = mutex.withLock { files[name]?.copyOf() }

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        mutex.withLock { files[name] = bytes.copyOf() }
    }
}

/** A [StorageProvider] over [InMemoryProjectStore]s, keyed by [ProjectLocation], for [ProjectWorkspace] tests. */
class FakeStorageProvider : StorageProvider {
    private val stores = mutableMapOf<ProjectLocation, InMemoryProjectStore>()
    private var missing = mutableSetOf<ProjectLocation>()

    /** Makes [location] resolve to [store] (as if a folder were already there). */
    fun register(
        location: ProjectLocation,
        store: InMemoryProjectStore,
    ) {
        stores[location] = store
    }

    /** Makes [location] resolve as moved or deleted. */
    fun markMissing(location: ProjectLocation) {
        missing += location
    }

    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        if (location in missing) return StoreLookup.Missing
        val store = stores[location] ?: return StoreLookup.Missing
        return StoreLookup.Available(store)
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val parentName = (parent as? ProjectLocation.InMemory)?.name ?: "root"
        // InMemoryProjectStore computes its own location from the name it is given, so the key this
        // registers under must be exactly that location, not a separately-built one.
        val store = InMemoryProjectStore("$parentName/$name")
        stores[store.location] = store
        return store.location
    }
}
