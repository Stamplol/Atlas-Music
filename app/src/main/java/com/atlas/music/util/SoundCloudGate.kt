package com.atlas.music.util

/**
 * Atlas's SoundCloud permission gate.
 *
 * SoundCloud's API exposes exactly one honest, programmatic reuse grant: the `license` field.
 * Artists who want their tracks redistributed pick a Creative Commons license (or CC0).
 * Everything else — the ~99% default, `all-rights-reserved` — means Atlas passes.
 *
 * SoundCloud's *native* download flag exists too, but the extractor never surfaces it into
 * the info dict, and in practice essentially no tracks enable it (artists gate downloads
 * off-site). We deliberately don't try to sniff it; the license is the contract.
 *
 * Go+ subscription tracks fail at extraction time anyway, so this gate is belt-and-braces.
 */
object SoundCloudGate {

    /** True when the artist licensed this track for redistribution. */
    fun licenseAllowsDownload(license: String?): Boolean {
        val normalized = license
            ?.trim()
            ?.lowercase()
            ?.replace('_', '-')
            ?.replace("creativecommons", "cc")
            ?.replace(" ", "-")
            ?: return false
        if (normalized == "cc0") return true                     // public-domain dedication
        if (normalized == "sampling+") return true                // sampling license, still an explicit grant
        if (normalized.startsWith("cc-")) {
            // Any of cc-by[-sa|-nc|-nd...] variants grant copy/distribution rights.
            // Atlas playback is local and personal; the NC clause covers that in every
            // Creative Commons jurisdiction we've checked.
            return true
        }
        return false
    }

    fun licenseLabel(license: String?): String = when {
        license == null -> "All rights reserved"
        licenseAllowsDownload(license) -> license.uppercase().replace('_', '-').replace("CC-", "CC ")
        else -> "All rights reserved"
    }

    const val BLOCKED_REASON = "This track isn't licensed for download — Atlas passes on all-rights-reserved streams."
}
