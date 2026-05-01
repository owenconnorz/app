@file:Suppress("PackageDirectoryMismatch")
package com.lagradost.nicehttp

/**
 * Type alias to bridge cloudstream3's Requests with the nicehttp package that plugins expect.
 * Plugins are compiled against com.lagradost.nicehttp.Requests, so this alias ensures
 * they can resolve the class at runtime.
 */
typealias Requests = com.lagradost.cloudstream3.Requests
