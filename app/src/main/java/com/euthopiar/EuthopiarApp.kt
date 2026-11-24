package com.euthopiar

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.euthopiar.app.plugin.PluginRepositoryService
import com.euthopiar.core.network.DohDns
import com.euthopiar.core.registry.PluginRegistry
import com.euthopiar.core.util.AppSessionManager
import com.euthopiar.data.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class EuthopiarApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("euthopiar_session", Context.MODE_PRIVATE)
        val lastIndex = prefs.getInt("launch_session_index", 0)
        val nextIndex = lastIndex + 1
        prefs.edit().putInt("launch_session_index", nextIndex).apply()
        AppSessionManager.initialize(nextIndex)

        // Initialize 50MB HTTP disk cache and connection pool immediately
        DohDns.initHttpCache(cacheDir)

        // Immediately warm DNS in parallel for all critical media, poster CDN, and metadata hosts
        // BEFORE any UI, catalog, or Coil image requests fire!
        DohDns.DEFAULT.prefetch(
            "euthopiar-vault.sehgalvansh716.workers.dev",
            "image.tmdb.org",
            "api.themoviedb.org",
            "graphql.anilist.co"
        )

        // Synchronously initialize JsPluginStorage, AddonRepository, and P2P engine instances
        com.euthopiar.core.js.JsPluginStorage.getInstance(this)
        com.euthopiar.core.addons.AddonRepository.getInstance(this)
        com.euthopiar.core.p2p.P2pStreamingEngine.initialize(this)
        com.euthopiar.core.p2p.P2pSettingsRepository.initialize(this)
        com.euthopiar.core.browser.HeadlessWebResolver.getInstance(this)

        val appScope = CoroutineScope(Dispatchers.IO)

        // Critical startup path: initialize plugins & restore active provider immediately
        appScope.launch {
            try {
                com.euthopiar.core.js.JsPluginStorage.getInstance(this@EuthopiarApp).initialize()
                com.euthopiar.core.addons.AddonRepository.getInstance(this@EuthopiarApp).initialize()
            } catch (_: Exception) {}

            try {
                val savedProvider = PreferencesRepository.getInstance(this@EuthopiarApp).activeProvider.first()
                if (savedProvider.isNotBlank()) {
                    PluginRegistry.Default.setActivePlugin(savedProvider)
                }
            } catch (_: Exception) {}

            try {
                PluginRepositoryService.getInstance(this@EuthopiarApp).initializePlugins()
            } catch (_: Exception) {}

            try {
                val prefsRepo = PreferencesRepository.getInstance(this@EuthopiarApp)
                val savedProvider = prefsRepo.activeProvider.first()
                if (savedProvider.isNotBlank() && PluginRegistry.Default.getPlugin(savedProvider) != null) {
                    PluginRegistry.Default.setActivePlugin(savedProvider)
                } else {
                    val firstPublic = PluginRepositoryService.getInstance(this@EuthopiarApp)
                        .getInstalledPublicPlugins()
                        .firstOrNull { it.isEnabled }
                    if (firstPublic != null) {
                        PluginRegistry.Default.setActivePlugin(firstPublic.name)
                        prefsRepo.setActiveProvider(firstPublic.name)
                    }
                }
            } catch (_: Exception) {}

            // Non-blocking plugin auto-update check after startup settles
            launch {
                delay(6_000L)
                try {
                    val updated = PluginRepositoryService.getInstance(this@EuthopiarApp)
                        .checkAndAutoUpdatePlugins(installNewFromRepos = false)
                    if (updated > 0) {
                        android.util.Log.i("EuthopiarApp", "Auto-updated $updated provider plugin(s) on launch")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EuthopiarApp", "Startup plugin auto-update error: ${e.message}")
                }
            }
        }

        // Independent preference observers (P2P policy & Debrid) — never block plugin init
        appScope.launch {
            try {
                val prefsRepo = PreferencesRepository.getInstance(this@EuthopiarApp)
                launch {
                    prefsRepo.p2pStreamsEnabled.collect { enabled ->
                        com.euthopiar.core.p2p.P2pSettingsRepository.setP2pEnabled(enabled)
                    }
                }
                launch {
                    kotlinx.coroutines.flow.combine(
                        prefsRepo.debridEnabled,
                        prefsRepo.activeDebridProvider,
                        prefsRepo.realDebridApiKey,
                        prefsRepo.allDebridApiKey,
                        prefsRepo.torBoxApiKey
                    ) { enabled, provider, rdKey, adKey, tbKey ->
                        if (!enabled) {
                            com.euthopiar.core.debrid.DebridManager.getInstance().setActiveProviders(emptyList())
                            return@combine
                        }
                        val pType = com.euthopiar.core.debrid.DebridProviderType.fromId(provider)
                        val key = when (pType) {
                            com.euthopiar.core.debrid.DebridProviderType.REAL_DEBRID -> rdKey
                            com.euthopiar.core.debrid.DebridProviderType.ALL_DEBRID -> adKey
                            com.euthopiar.core.debrid.DebridProviderType.TORBOX -> tbKey
                            com.euthopiar.core.debrid.DebridProviderType.NONE -> ""
                        }
                        if (pType != com.euthopiar.core.debrid.DebridProviderType.NONE && key.isNotBlank()) {
                            com.euthopiar.core.debrid.DebridManager.getInstance().setActiveProviders(
                                listOf(com.euthopiar.core.debrid.DebridManager.ActiveDebridProvider(pType, true))
                            )
                        } else {
                            com.euthopiar.core.debrid.DebridManager.getInstance().setActiveProviders(emptyList())
                        }
                    }.collect { }
                }
            } catch (_: Exception) {}
        }

        // Deferred smart cache maintenance: runs well after Home catalog & posters finish hydrating
        // so it NEVER competes with startup disk I/O or network fetches.
        appScope.launch {
            delay(20_000L)
            try {
                com.euthopiar.data.cache.AppCacheManager.runAutomaticMaintenance(this@EuthopiarApp)
            } catch (_: Exception) {}
        }
    }

    private val imageHttpClient by lazy {
        DohDns.sharedBaseClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 20
                }
            )
            .addInterceptor { chain ->
                val orig = chain.request()
                val req = if (orig.header("User-Agent") == null) {
                    orig.newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                        .build()
                } else {
                    orig
                }
                try {
                    val response = chain.proceed(req)
                    if (!response.isSuccessful && (response.code == 429 || response.code in 500..599)) {
                        response.close()
                        chain.proceed(req)
                    } else {
                        response
                    }
                } catch (e: java.io.IOException) {
                    if (chain.call().isCanceled()) throw e
                    chain.proceed(req)
                }
            }
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { imageHttpClient }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(com.euthopiar.data.cache.AppCacheManager.getCoilDiskCacheCapBytes(this))
                    .build()
            }
            .bitmapConfig(Bitmap.Config.HARDWARE)
            .crossfade(220)
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Coil 3 trims memory proportionally on routine trim events.
        // Only clear the entire bitmap cache under extreme background eviction pressure
        // so browsing between Home, Details, Search, and Library never forces poster reloads.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            runCatching {
                coil3.SingletonImageLoader.get(this).memoryCache?.clear()
            }
        }
    }
}
