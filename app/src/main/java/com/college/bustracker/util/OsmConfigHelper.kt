package com.college.bustracker.util

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

object OsmConfigHelper {

    /**
     * Configures OSMDroid to comply strictly with OpenStreetMap's Tile Usage Policy:
     * 1. Requires a non-empty, identifiable HTTP User-Agent string.
     * 2. Sets up a persistent on-device disk tile cache so identical tiles are never re-requested.
     * 3. Disables redundant tile download threads.
     */
    fun initialize(context: Context) {
        val config = Configuration.getInstance()
        
        // 1. Identification: Identify the college application per OSM policy
        config.userAgentValue = "CollegeBusTracker/1.0.0 (contact: transport@college.edu; Android)"
        
        // 2. Tile Caching directory
        val osmCacheDir = File(context.cacheDir, "osmdroid")
        if (!osmCacheDir.exists()) {
            osmCacheDir.mkdirs()
        }
        config.osmdroidTileCache = osmCacheDir
        
        // 3. Cache limits: 100 MB max disk tile cache
        config.tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        config.tileFileSystemCacheTrimBytes = 80L * 1024 * 1024
        
        // 4. Threading limits to prevent network hammering
        config.tileDownloadThreads = 2
    }
}
