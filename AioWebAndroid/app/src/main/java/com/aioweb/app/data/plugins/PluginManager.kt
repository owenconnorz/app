package com.aioweb.app.data.plugins

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

class PluginManager(private val context: Context) {
    private val TAG = "PluginManager"
    
    // Use app-specific files directory with restricted permissions
    private val pluginDir: File
        get() {
            val dir = File(context.filesDir, "plugins")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun loadPlugin(pluginFile: File): LoadedPlugin? {
        return try {
            Log.d(TAG, "Loading plugin: ${pluginFile.name}")
            
            // Create a read-only copy first
            val readOnlyFile = File(pluginDir, pluginFile.name + ".ro")
            
            if (!readOnlyFile.exists()) {
                pluginFile.copyTo(readOnlyFile, overwrite = true)
                // Set file to read-only (important for Android 13+)
                readOnlyFile.setReadOnly()
                Log.d(TAG, "Created read-only copy: ${readOnlyFile.absolutePath}")
            }

            // Get or create optimized DEX cache directory
            val dexCache = File(context.cacheDir, "dex_cache")
            if (!dexCache.exists()) {
                dexCache.mkdirs()
            }

            // Create ClassLoader with read-only plugin
            val dexLoader = DexClassLoader(
                readOnlyFile.absolutePath,  // Read-only DEX file
                dexCache.absolutePath,      // Writable cache for optimized DEX
                null,                        // No native libs
                context.classLoader          // Parent ClassLoader
            )

            Log.d(TAG, "DexClassLoader created successfully for ${pluginFile.name}")

            LoadedPlugin(
                name = pluginFile.nameWithoutExtension,
                classLoader = dexLoader,
                file = readOnlyFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin ${pluginFile.name}: ${e.message}", e)
            null
        }
    }

    fun deletePlugin(pluginName: String) {
        val file = File(pluginDir, "$pluginName.cs3")
        val readOnlyFile = File(pluginDir, "$pluginName.cs3.ro")
        
        try {
            // Make writable before deleting
            if (readOnlyFile.exists()) {
                readOnlyFile.setWritable(true)
                readOnlyFile.delete()
                Log.d(TAG, "Deleted read-only file: ${readOnlyFile.absolutePath}")
            }
            if (file.exists()) {
                file.setWritable(true)
                file.delete()
                Log.d(TAG, "Deleted plugin file: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete plugin $pluginName: ${e.message}")
        }
    }

    fun listPlugins(): List<File> {
        return pluginDir.listFiles { file ->
            file.name.endsWith(".cs3.ro")
        }?.toList() ?: emptyList()
    }

    fun getPluginDir(): File = pluginDir

    data class LoadedPlugin(
        val name: String,
        val classLoader: DexClassLoader,
        val file: File
    )
}