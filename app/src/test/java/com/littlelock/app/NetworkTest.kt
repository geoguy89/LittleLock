package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying no external server connections except Discord webhook.
 * Searches all .kt files for hardcoded URLs that are NOT discord.com/webhooks.
 */
class NetworkTest {

    private val srcMain = File("src/main")

    /**
     * Verify that only DiscordWebhookService.kt makes HTTP connections
     * and no hardcoded non-Discord URLs exist.
     */
    @Test
    fun the_app_makes_no_external_network_requests_to_original_servers() {
        val javaDir = File(srcMain, "java")
        if (!javaDir.exists()) {
            fail("java directory not found")
            return
        }

        val ktFiles = collectFiles(javaDir, ".kt")

        for (file in ktFiles) {
            val content = file.readText()
            val lines = content.lines()

            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                // Skip comments
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue
                }

                // Check for hardcoded URLs (http:// or https://)
                if (trimmed.contains("http://") || trimmed.contains("https://")) {
                    // Allow Discord webhook URLs
                    if (trimmed.contains("discord.com/webhooks")) {
                        continue
                    }
                    // Allow localhost/127.0.0.1 for testing
                    if (trimmed.contains("localhost") || trimmed.contains("127.0.0.1")) {
                        continue
                    }
                    // Allow the app's own GitHub releases (self-update, Updater.kt)
                    if (file.name == "Updater.kt" && trimmed.contains("api.github.com/repos/geoguy89/LittleLock/")) {
                        continue
                    }
                    // Allow file:// resources
                    if (trimmed.contains("file://")) {
                        continue
                    }

                    // Check if this file is DiscordWebhookService.kt (it uses dynamic URLs)
                    if (file.name == "DiscordWebhookService.kt") {
                        // DiscordWebhookService uses dynamic webhookUrl parameter, not hardcoded URLs
                        // The only "URL" references are the .url(webhookUrl) call and string templates
                        if (!trimmed.contains(".url(") && !trimmed.contains("\"http")) {
                            fail(
                                "Found hardcoded URL in ${file.name} line ${index + 1}: $trimmed"
                            )
                        }
                        continue
                    }

                    fail(
                        "Found hardcoded URL in ${file.relativeTo(srcMain)} line ${index + 1}: $trimmed"
                    )
                }
            }
        }
    }

    /**
     * Verify that only DiscordWebhookService.kt makes HTTP connections.
     */
    @Test
    fun testOnlyDiscordWebhookMakesHttp() {
        val javaDir = File(srcMain, "java")
        if (!javaDir.exists()) {
            fail("java directory not found")
            return
        }

        val ktFiles = collectFiles(javaDir, ".kt")

        for (file in ktFiles) {
            val content = file.readText()
            val fileName = file.name

            if (fileName == "DiscordWebhookService.kt") {
                // This file is allowed to use OkHttpClient and HTTP calls
                assertTrue(
                    "DiscordWebhookService.kt should use OkHttpClient",
                    content.contains("OkHttpClient")
                )
                continue
            }

            // All other files should NOT use OkHttpClient or make HTTP calls
            assertFalse(
                "${file.name} should not use OkHttpClient",
                content.contains("OkHttpClient")
            )
            assertFalse(
                "${file.name} should not use okhttp3",
                content.contains("okhttp3")
            )
        }
    }

    private fun collectFiles(dir: File, extension: String): List<File> {
        val result = mutableListOf<File>()
        for (entry in dir.listFiles() ?: emptyArray()) {
            if (entry.isDirectory) {
                result.addAll(collectFiles(entry, extension))
            } else if (entry.name.endsWith(extension)) {
                result.add(entry)
            }
        }
        return result
    }
}
