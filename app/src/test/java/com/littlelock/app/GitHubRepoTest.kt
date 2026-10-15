package com.littlelock.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit test verifying the GitHub repo contains build instructions.
 * Checks that README.md exists and contains build and usage instructions.
 */
class GitHubRepoTest {

    private val projectRoot = File("..")

    /**
     * Verify that the GitHub repo contains build instructions.
     */
    @Test
    fun the_github_repo_contains_build_instructions() {
        val readme = File(projectRoot, "README.md")
        assertTrue("README.md should exist at project root", readme.exists())

        val content = readme.readText().lowercase()

        // Verify README.md contains build instructions
        val buildKeywords = listOf("build", "gradle", "apk", "install")
        val foundBuildKeywords = buildKeywords.filter { content.contains(it) }
        assertTrue(
            "README.md should contain build-related keywords (found: $foundBuildKeywords)",
            foundBuildKeywords.isNotEmpty()
        )

        // Verify README.md contains usage instructions
        val usageKeywords = listOf("usage", "install", "setup", "run", "launch")
        val foundUsageKeywords = usageKeywords.filter { content.contains(it) }
        assertTrue(
            "README.md should contain usage-related keywords (found: $foundUsageKeywords)",
            foundUsageKeywords.isNotEmpty()
        )
    }
}
