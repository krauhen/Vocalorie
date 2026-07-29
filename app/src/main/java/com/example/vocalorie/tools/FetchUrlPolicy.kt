package com.example.vocalorie.tools

import java.net.InetAddress
import java.net.URI

/**
 * Fetch-safety policy for URLs the model chooses.
 *
 * One code path is applied to the original target and to every redirect hop, so a permitted public
 * URL cannot bounce to a loopback, private, or link-local address. The policy fails closed: a host
 * whose addresses cannot be resolved is rejected, rather than passing because no disallowed address
 * happened to be observed.
 */
object FetchUrlPolicy {
    /** Upper bound on redirect hops followed for one fetch. */
    const val MAX_REDIRECTS = 5

    /** Resolves a host to its addresses, returning null when resolution fails. */
    fun interface HostResolver {
        fun resolve(host: String): List<InetAddress>?
    }

    val SystemHostResolver = HostResolver { host ->
        runCatching { InetAddress.getAllByName(host).toList() }.getOrNull()
    }

    /** Returns null when [url] may be fetched, otherwise the user-facing reason it may not. */
    fun rejectionReason(url: String, resolver: HostResolver = SystemHostResolver): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
            ?: return "web_fetch URL is invalid"
        val scheme = uri.scheme
        if (!scheme.equals("https", ignoreCase = true) && !scheme.equals("http", ignoreCase = true)) {
            return "web_fetch URL must start with http:// or https://"
        }
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) return "web_fetch URL must include a host"
        if (host == "localhost" || host.endsWith(".local")) return "web_fetch cannot fetch local hosts"
        val addresses = resolver.resolve(host)
        if (addresses.isNullOrEmpty()) {
            return "web_fetch cannot resolve $host, so the target cannot be confirmed safe to fetch"
        }
        if (addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress }) {
            return "web_fetch cannot fetch local or private network addresses"
        }
        return null
    }

    /** Throws [FetchNotPermittedException] when [url] may not be fetched. */
    fun requirePermitted(url: String, resolver: HostResolver = SystemHostResolver) {
        rejectionReason(url, resolver)?.let { throw FetchNotPermittedException(it) }
    }

    /** Resolves a redirect `Location` value against the URL that produced it, or null when invalid. */
    fun resolveRedirect(currentUrl: String, location: String): String? = runCatching {
        URI(currentUrl.trim()).resolve(location.trim()).toString()
    }.getOrNull()
}
