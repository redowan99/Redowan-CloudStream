package com.yourorg

import kotlinx.coroutines.runBlocking
import com.redowan.BdixDhakaFlix14Provider

/**
 * Provider tester runner.
 * Usage:
 *   ./gradlew :provider-tester:run --args="<loadUrl> [providerMainUrl]"
 * Examples:
 *   ./gradlew :provider-tester:run --args="http://172.16.50.12/.../Season%201/ http://172.16.50.12"
 * Or set environment variable PROVIDER_MAIN_URL to override the provider's mainUrl.
 */
fun main(args: Array<String>) = runBlocking {
    val provider = BdixDhakaFlix14Provider()

    // Allow overriding provider.mainUrl via environment variable or second argument
    val envMain = System.getenv("PROVIDER_MAIN_URL")
    if (!envMain.isNullOrBlank()) {
        provider.mainUrl = envMain
    }
    if (args.size > 1 && !args[1].isNullOrBlank()) {
        provider.mainUrl = args[1]
    }

    // If no explicit override was provided, and we have a load URL, set provider.mainUrl
    // to the origin of the provided URL (scheme://host[:port]) so relative links resolve.
    if ((envMain.isNullOrBlank() && (args.size <= 1 || args[1].isNullOrBlank())) && args.isNotEmpty()) {
        try {
            val uri = java.net.URI(args[0])
            val origin = uri.scheme + "://" + uri.host + (if (uri.port != -1) ":" + uri.port else "")
            provider.mainUrl = origin
        } catch (ignored: Exception) {
            // leave default mainUrl if parsing fails
        }
    }

    println("Provider ready: name=${provider.name}, mainUrl=${provider.mainUrl}")

    if (args.isEmpty()) {
        println("No URL provided. To run a load test, pass a URL as the first argument.")
        println("Example: ./gradlew :provider-tester:run --args=\"http://172.16.50.12/.../Season%201/ http://172.16.50.12\"")
        return@runBlocking
    }

    val url = args[0]
    println("Running load test for: $url")
    try {
        val response = provider.load(url)
        println("LoadResponse:")
        println("  title/ name: ${response.name}")
        println("  posterUrl: ${response.posterUrl}")
        println("  plot: ${response.plot}")
        println("  year: ${response.year}")
        println("  score: ${response.score}")
        println(response)
    } catch (e: Exception) {
        println("Error during load test: ${e.message}")
        e.printStackTrace()
    }
}

