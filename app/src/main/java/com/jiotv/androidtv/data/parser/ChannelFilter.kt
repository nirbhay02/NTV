package com.jiotv.androidtv.data.parser

import com.jiotv.androidtv.data.model.Category
import com.jiotv.androidtv.data.model.Channel
import java.util.Locale

/**
 * Filter and organization utility for broadcaster-specific categorization,
 * with primary emphasis on Zee Network channels and standard TV genres.
 */
object ChannelFilter {

    private val ZEE_REGEX = Regex(
        pattern = """(?i)(\bzee\b|&pictures|&tv|&flix|&xplor|&prive|zing|big\s*magic)"""
    )

    fun isZeeChannel(channel: Channel): Boolean {
        if (channel.isZee) return true
        val checkText = "${channel.name} ${channel.tvgName.orEmpty()} ${channel.groupTitle}"
        return ZEE_REGEX.containsMatchIn(checkText)
    }

    fun isHdChannel(name: String, groupTitle: String?): Boolean {
        val combined = "$name ${groupTitle.orEmpty()}".uppercase(Locale.ROOT)
        return combined.contains(" HD") || combined.endsWith("HD") || combined.contains("[HD]")
    }

    fun organizeCategories(
        channels: List<Channel>,
        favoriteIds: Set<String> = emptySet(),
        recentIds: List<String> = emptyList()
    ): List<Category> {
        val categories = mutableListOf<Category>()

        // 1. Zee Network
        val zeeChannels = channels.filter { isZeeChannel(it) }
        if (zeeChannels.isNotEmpty()) {
            categories.add(
                Category(
                    id = "zee_network",
                    title = "✨ Zee Network",
                    iconName = "zee_icon",
                    channels = zeeChannels,
                    isPriority = true
                )
            )
        }

        // 2. Entertainment
        val entertainment = channels.filter {
            !isZeeChannel(it) && it.groupTitle.contains("Entertainment", ignoreCase = true)
        }
        if (entertainment.isNotEmpty()) {
            categories.add(Category("entertainment", "🎭 Entertainment", channels = entertainment))
        }

        // 3. Movies
        val movies = channels.filter {
            it.groupTitle.contains("Movies", ignoreCase = true) ||
            it.groupTitle.contains("Movie", ignoreCase = true) ||
            it.name.contains("Cinema", ignoreCase = true) ||
            it.name.contains("Movie", ignoreCase = true)
        }
        if (movies.isNotEmpty()) {
            categories.add(Category("movies", "🎬 Movies", channels = movies))
        }

        // 4. Sports
        val sports = channels.filter {
            it.groupTitle.contains("Sports", ignoreCase = true) ||
            it.name.contains("Sports", ignoreCase = true)
        }
        if (sports.isNotEmpty()) {
            categories.add(Category("sports", "⚽ Sports", channels = sports))
        }

        // 5. Music
        val music = channels.filter {
            it.groupTitle.contains("Music", ignoreCase = true) ||
            it.name.contains("Music", ignoreCase = true) ||
            it.name.contains("9XM", ignoreCase = true) ||
            it.name.contains("MTV", ignoreCase = true)
        }
        if (music.isNotEmpty()) {
            categories.add(Category("music", "🎵 Music", channels = music))
        }

        // 6. News
        val news = channels.filter {
            it.groupTitle.contains("News", ignoreCase = true) ||
            it.name.contains("News", ignoreCase = true) ||
            it.name.contains("24 Taas", ignoreCase = true) ||
            it.name.contains("24 Ghanta", ignoreCase = true)
        }
        if (news.isNotEmpty()) {
            categories.add(Category("news", "📰 News", channels = news))
        }

        // 7. Kids
        val kids = channels.filter {
            it.groupTitle.contains("Kids", ignoreCase = true) ||
            it.name.contains("Cartoon", ignoreCase = true) ||
            it.name.contains("Pogo", ignoreCase = true) ||
            it.name.contains("Nick", ignoreCase = true) ||
            it.name.contains("Sonic", ignoreCase = true) ||
            it.name.contains("Hungama", ignoreCase = true) ||
            it.name.contains("Disney", ignoreCase = true)
        }
        if (kids.isNotEmpty()) {
            categories.add(Category("kids", "🧸 Kids", channels = kids))
        }

        // 8. Infotainment
        val infotainment = channels.filter {
            it.groupTitle.contains("Infotainment", ignoreCase = true) ||
            it.groupTitle.contains("Knowledge", ignoreCase = true) ||
            it.name.contains("Discovery", ignoreCase = true) ||
            it.name.contains("Nat Geo", ignoreCase = true) ||
            it.name.contains("History", ignoreCase = true)
        }
        if (infotainment.isNotEmpty()) {
            categories.add(Category("infotainment", "🌍 Infotainment", channels = infotainment))
        }

        // 9. Devotional
        val devotional = channels.filter {
            it.groupTitle.contains("Devotional", ignoreCase = true) ||
            it.name.contains("Aastha", ignoreCase = true) ||
            it.name.contains("Sanskriti", ignoreCase = true)
        }
        if (devotional.isNotEmpty()) {
            categories.add(Category("devotional", "🙏 Devotional", channels = devotional))
        }

        // Favorites
        val favorites = channels.filter { favoriteIds.contains(it.id) }
        if (favorites.isNotEmpty()) {
            categories.add(
                0,
                Category(
                    id = "favorites",
                    title = "⭐ Favorites",
                    channels = favorites,
                    isPriority = true
                )
            )
        }

        // Recently Played
        val recentChannels = recentIds.mapNotNull { rid ->
            val clean = rid.removePrefix("zee_")
            channels.find { it.id == rid || it.id == "zee_$clean" || it.id.removePrefix("zee_") == clean }
        }.distinctBy { it.id }

        if (recentChannels.isNotEmpty()) {
            val insertIdx = if (favorites.isNotEmpty()) 1 else 0
            categories.add(
                insertIdx,
                Category(
                    id = "recently_played",
                    title = "🕒 Recent",
                    channels = recentChannels,
                    isPriority = true
                )
            )
        }

        // All Channels
        categories.add(Category("all_channels", "📺 All Channels", channels = channels))

        return categories
    }
}
