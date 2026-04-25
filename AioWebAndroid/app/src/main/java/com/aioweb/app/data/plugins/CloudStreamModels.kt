package com.aioweb.app.data.plugins

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CloudStream extension/plugin repository JSON format.
 *
 * Compatible with `https://raw.githubusercontent.com/recloudstream/extensions/builds/repo.json`
 * and most community CS3 forks (e.g., recloudstream/cloudstreampluginsTest, doomedKnight,
 * Cloudburst, etc.).
 */
@Serializable
data class CloudStreamPlugin(
    val name: String = "",
    val url: String = "",                // .cs3 download URL
    val version: Int = 1,
    val apiVersion: Int = 1,
    val description: String? = null,
    val authors: List<String>? = null,
    val repositoryUrl: String? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val status: Int = 1,                 // 1 = ok, 0 = down
    @SerialName("tvTypes") val tvTypes: List<String>? = null,
    val internalName: String? = null,
    val fileSize: Long? = null,
)

@Serializable
data class CloudStreamRepo(
    val id: String,
    val name: String,
    val url: String,                     // points to repo.json
    val pluginCount: Int = 0,
    val lastFetched: Long = 0L,
)

@Serializable
data class InstalledPlugin(
    val name: String,
    val internalName: String,
    val version: Int,
    val filePath: String,                // local .cs3 path
    val sourceRepoId: String,
    val sourceUrl: String,
    val installedAt: Long,
)
