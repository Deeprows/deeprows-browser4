package com.deeprows.browser

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val pubDate: String,
    val description: String,
    val imageUrl: String
)

class NewsRepository {

    // =========================================================
    // NEWS SOURCES
    // =========================================================

    // Multiple established publishers are used so the home page is not limited
    // to a single newsroom. Feeds are fetched in parallel and de-duplicated.
    private val latestNewsFeeds = listOf(
        "https://feeds.bbci.co.uk/news/rss.xml",
        "https://www.aljazeera.com/xml/rss/all.xml",
        "https://www.theguardian.com/world/rss",
        "https://feeds.npr.org/1001/rss.xml",
        "https://rss.dw.com/xml/rss-en-all",
        "https://www.euronews.com/rss"
    )

    private val sportNewsFeeds = listOf(
        "https://www.espn.com/espn/rss/news",
        "https://www.skysports.com/rss/12040",
        "https://www.cbssports.com/rss/headlines/",
        "https://www.theguardian.com/uk/sport/rss",
        "https://feeds.bbci.co.uk/sport/rss.xml"
    )

    // =========================================================
    // GOOGLE TRENDS RSS
    // =========================================================
    //
    // The country/geo is added dynamically.
    //
    // Example:
    // Egypt   -> ?geo=EG
    // Nigeria -> ?geo=NG
    // UK      -> ?geo=GB
    // USA     -> ?geo=US
    //
    // If no country is available, the worldwide/default
    // Google Trends feed is used.
    //
    // =========================================================

    private fun getGoogleTrendsUrl(
        countryCode: String?
    ): String {

        val geo =
            countryCode
                ?.trim()
                ?.uppercase()
                ?.takeIf {
                    it.length == 2
                }

        return if (
            geo != null
        ) {

            "https://trends.google.com/trending/rss?geo=$geo"

        } else {

            "https://trends.google.com/trending/rss"
        }
    }

    // =========================================================
    // LATEST NEWS
    // =========================================================

    suspend fun getLatestNews(
        limit: Int = 4
    ): List<NewsArticle> =
        fetchMultipleFeeds(latestNewsFeeds, limit)

    // =========================================================
    // SPORT NEWS
    // =========================================================

    suspend fun getSportNews(
        limit: Int = 4
    ): List<NewsArticle> =
        fetchMultipleFeeds(sportNewsFeeds, limit)

    private suspend fun fetchMultipleFeeds(
        feeds: List<String>,
        limit: Int
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        coroutineScope {
            val results = feeds.map { feed ->
                async { fetchRssFeed(feed, limit) }
            }.awaitAll()

            val unique = linkedMapOf<String, NewsArticle>()
            results.flatten().forEach { article ->
                val key = article.link.ifBlank { article.title.lowercase() }
                if (!unique.containsKey(key)) {
                    unique[key] = article
                }
            }

            // Round-robin the sources so the first screen contains several publishers.
            val output = mutableListOf<NewsArticle>()
            var index = 0
            while (output.size < limit && results.any { index < it.size }) {
                results.forEach { sourceArticles ->
                    if (index < sourceArticles.size && output.size < limit) {
                        val article = sourceArticles[index]
                        val key = article.link.ifBlank { article.title.lowercase() }
                        if (unique.containsKey(key)) {
                            output.add(article)
                            unique.remove(key)
                        }
                    }
                }
                index++
            }
            output
        }
    }

    // =========================================================
    // GOOGLE TRENDS
    // =========================================================
    //
    // countryCode should come from CountryProvider.
    //
    // Example:
    //
    // val countryCode =
    //     CountryProvider.getCountryCode(context)
    //
    // repository.getGoogleTrends(
    //     countryCode = countryCode
    // )
    //
    // =========================================================

    suspend fun getGoogleTrends(
        countryCode: String?,
        limit: Int = 10
    ): List<NewsArticle> =
        withContext(Dispatchers.IO) {

            fetchGoogleTrends(
                countryCode,
                limit
            )
        }

    // =========================================================
    // GENERIC RSS READER
    // =========================================================

    private fun fetchRssFeed(
        feedUrl: String,
        limit: Int
    ): List<NewsArticle> {

        val articles =
            mutableListOf<NewsArticle>()

        var connection:
                HttpURLConnection? = null

        try {

            connection =
                URL(feedUrl)
                    .openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                20000

            connection.readTimeout =
                20000

            connection.instanceFollowRedirects =
                true

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15; Mobile) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/153.0.0.0 Mobile Safari/537.36"
            )

            connection.setRequestProperty(
                "Accept",
                "application/rss+xml, application/xml, text/xml, */*"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )

            if (
                connection.responseCode !=
                HttpURLConnection.HTTP_OK
            ) {

                return emptyList()
            }

            connection.inputStream.use { inputStream ->

                val parser =
                    Xml.newPullParser()

                parser.setFeature(
                    XmlPullParser.FEATURE_PROCESS_NAMESPACES,
                    false
                )

                parser.setInput(
                    inputStream,
                    "UTF-8"
                )

                var eventType =
                    parser.eventType

                var insideItem =
                    false

                var title =
                    ""

                var link =
                    ""

                var source =
                    ""

                var pubDate =
                    ""

                var description =
                    ""

                val imageCandidates =
                    mutableListOf<String>()

                while (
                    eventType !=
                    XmlPullParser.END_DOCUMENT
                ) {

                    when (eventType) {

                        XmlPullParser.START_TAG -> {

                            val tag =
                                parser.name
                                    ?.lowercase()
                                    ?: ""

                            // -------------------------------------------------
                            // ITEM START
                            // -------------------------------------------------

                            if (
                                tag == "item"
                            ) {

                                insideItem =
                                    true

                                title =
                                    ""

                                link =
                                    ""

                                source =
                                    ""

                                pubDate =
                                    ""

                                description =
                                    ""

                                imageCandidates.clear()
                            }

                            if (
                                insideItem
                            ) {

                                when (tag) {

                                    // -----------------------------------------
                                    // TITLE
                                    // -----------------------------------------

                                    "title" -> {

                                        if (
                                            title.isEmpty()
                                        ) {

                                            title =
                                                safeNextText(
                                                    parser
                                                )
                                        }
                                    }

                                    // -----------------------------------------
                                    // LINK
                                    // -----------------------------------------

                                    "link" -> {

                                        if (
                                            link.isEmpty()
                                        ) {

                                            link =
                                                safeNextText(
                                                    parser
                                                )
                                        }
                                    }

                                    // -----------------------------------------
                                    // SOURCE
                                    // -----------------------------------------

                                    "source" -> {

                                        if (
                                            source.isEmpty()
                                        ) {

                                            source =
                                                safeNextText(
                                                    parser
                                                )
                                        }
                                    }

                                    // -----------------------------------------
                                    // DATE
                                    // -----------------------------------------

                                    "pubdate",
                                    "published",
                                    "updated" -> {

                                        if (
                                            pubDate.isEmpty()
                                        ) {

                                            pubDate =
                                                safeNextText(
                                                    parser
                                                )
                                        }
                                    }

                                    // -----------------------------------------
                                    // DESCRIPTION
                                    // -----------------------------------------

                                    "description",
                                    "content:encoded" -> {

                                        if (
                                            description.isEmpty()
                                        ) {

                                            description =
                                                safeNextText(
                                                    parser
                                                )
                                        }
                                    }

                                    // -----------------------------------------
                                    // IMAGES
                                    // -----------------------------------------

                                    "media:content",
                                    "media:thumbnail",
                                    "enclosure",
                                    "thumbnail",
                                    "content" -> {

                                        val imageUrl =
                                            getImageUrl(
                                                parser
                                            )

                                        if (
                                            !imageUrl.isNullOrBlank()
                                        ) {

                                            imageCandidates.add(
                                                imageUrl
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {

                            val tag =
                                parser.name
                                    ?.lowercase()
                                    ?: ""

                            if (
                                tag == "item"
                            ) {

                                insideItem =
                                    false

                                if (
                                    title.isNotBlank() &&
                                    link.isNotBlank()
                                ) {

                                    val imageUrl =
                                        findImageUrl(
                                            description,
                                            imageCandidates
                                        )

                                    articles.add(
                                        NewsArticle(
                                            title =
                                                cleanText(
                                                    title
                                                ),

                                            link =
                                                link.trim(),

                                            source =
                                                cleanText(
                                                    source
                                                ).ifBlank {
                                                    detectSource(
                                                        feedUrl
                                                    )
                                                },

                                            pubDate =
                                                cleanText(
                                                    pubDate
                                                ),

                                            description =
                                                cleanText(
                                                    description
                                                ),

                                            imageUrl =
                                                imageUrl
                                        )
                                    )
                                }

                                if (
                                    articles.size >=
                                    limit
                                ) {

                                    break
                                }
                            }
                        }
                    }

                    eventType =
                        parser.next()
                }
            }

        } catch (
            e: Exception
        ) {

            e.printStackTrace()

        } finally {

            connection?.disconnect()
        }

        return articles
    }

    // =========================================================
    // GOOGLE TRENDS RSS
    // =========================================================

    private fun fetchGoogleTrends(
        countryCode: String?,
        limit: Int
    ): List<NewsArticle> {

        val articles =
            mutableListOf<NewsArticle>()

        var connection:
                HttpURLConnection? = null

        try {

            // -----------------------------------------------------
            // BUILD LOCATION-SPECIFIC GOOGLE TRENDS URL
            // -----------------------------------------------------

            val trendsUrl =
                getGoogleTrendsUrl(
                    countryCode
                )

            connection =
                URL(trendsUrl)
                    .openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                20000

            connection.readTimeout =
                20000

            connection.instanceFollowRedirects =
                true

            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15; Mobile) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/153.0.0.0 Mobile Safari/537.36"
            )

            connection.setRequestProperty(
                "Accept",
                "application/rss+xml, application/xml, text/xml, */*"
            )

            connection.setRequestProperty(
                "Accept-Language",
                "en-US,en;q=0.9"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )

            if (
                connection.responseCode !=
                HttpURLConnection.HTTP_OK
            ) {

                return emptyList()
            }

            connection.inputStream.use { inputStream ->

                val parser =
                    Xml.newPullParser()

                // -------------------------------------------------
                // IMPORTANT
                // -------------------------------------------------
                //
                // Google Trends uses XML namespaces:
                //
                // ht:news_item_title
                // ht:news_item_url
                // ht:news_item_source
                // ht:news_item_picture
                //
                // We enable namespace processing.
                //
                // -------------------------------------------------

                parser.setFeature(
                    XmlPullParser.FEATURE_PROCESS_NAMESPACES,
                    true
                )

                parser.setInput(
                    inputStream,
                    "UTF-8"
                )

                var eventType =
                    parser.eventType

                var insideItem =
                    false

                var insideNewsItem =
                    false

                // -------------------------------------------------
                // TREND INFORMATION
                // -------------------------------------------------

                var trendTitle =
                    ""

                var trendTraffic =
                    ""

                var trendDate =
                    ""

                var trendPicture =
                    ""

                // -------------------------------------------------
                // NEWS ARTICLE INFORMATION
                // -------------------------------------------------

                var newsTitle =
                    ""

                var newsUrl =
                    ""

                var newsSource =
                    ""

                var newsPicture =
                    ""

                var newsSnippet =
                    ""

                while (
                    eventType !=
                    XmlPullParser.END_DOCUMENT
                ) {

                    when (eventType) {

                        XmlPullParser.START_TAG -> {

                            val name =
                                parser.name
                                    ?.lowercase()
                                    ?: ""

                            val namespace =
                                parser.namespace
                                    ?.lowercase()
                                    ?: ""

                            // =================================================
                            // TREND ITEM START
                            // =================================================

                            if (
                                name == "item"
                            ) {

                                insideItem =
                                    true

                                insideNewsItem =
                                    false

                                trendTitle =
                                    ""

                                trendTraffic =
                                    ""

                                trendDate =
                                    ""

                                trendPicture =
                                    ""

                                newsTitle =
                                    ""

                                newsUrl =
                                    ""

                                newsSource =
                                    ""

                                newsPicture =
                                    ""

                                newsSnippet =
                                    ""
                            }

                            // =================================================
                            // TREND TITLE
                            // =================================================

                            if (
                                insideItem &&
                                !insideNewsItem &&
                                name == "title" &&
                                namespace.isEmpty()
                            ) {

                                if (
                                    trendTitle.isEmpty()
                                ) {

                                    trendTitle =
                                        safeNextText(
                                            parser
                                        )
                                }
                            }

                            // =================================================
                            // APPROXIMATE TRAFFIC
                            // =================================================

                            if (
                                insideItem &&
                                !insideNewsItem &&
                                name == "approx_traffic"
                            ) {

                                if (
                                    trendTraffic.isEmpty()
                                ) {

                                    trendTraffic =
                                        safeNextText(
                                            parser
                                        )
                                }
                            }

                            // =================================================
                            // TREND DATE
                            // =================================================

                            if (
                                insideItem &&
                                !insideNewsItem &&
                                name == "pubdate" &&
                                namespace.isEmpty()
                            ) {

                                if (
                                    trendDate.isEmpty()
                                ) {

                                    trendDate =
                                        safeNextText(
                                            parser
                                        )
                                }
                            }

                            // =================================================
                            // TREND PICTURE
                            // =================================================

                            if (
                                insideItem &&
                                !insideNewsItem &&
                                name == "picture"
                            ) {

                                if (
                                    trendPicture.isEmpty()
                                ) {

                                    trendPicture =
                                        safeNextText(
                                            parser
                                        )
                                }
                            }

                            // =================================================
                            // NEWS ITEM START
                            // =================================================

                            if (
                                name == "news_item"
                            ) {

                                insideNewsItem =
                                    true
                            }

                            // =================================================
                            // NEWS ARTICLE TITLE
                            // =================================================

                            if (
                                insideNewsItem &&
                                name == "news_item_title"
                            ) {

                                newsTitle =
                                    safeNextText(
                                        parser
                                    )
                            }

                            // =================================================
                            // NEWS ARTICLE URL
                            // =================================================

                            if (
                                insideNewsItem &&
                                name == "news_item_url"
                            ) {

                                newsUrl =
                                    safeNextText(
                                        parser
                                    )
                            }

                            // =================================================
                            // NEWS SOURCE
                            // =================================================

                            if (
                                insideNewsItem &&
                                name == "news_item_source"
                            ) {

                                newsSource =
                                    safeNextText(
                                        parser
                                    )
                            }

                            // =================================================
                            // NEWS IMAGE
                            // =================================================

                            if (
                                insideNewsItem &&
                                name == "news_item_picture"
                            ) {

                                newsPicture =
                                    safeNextText(
                                        parser
                                    )
                            }

                            // =================================================
                            // NEWS SNIPPET
                            // =================================================

                            if (
                                insideNewsItem &&
                                name == "news_item_snippet"
                            ) {

                                newsSnippet =
                                    safeNextText(
                                        parser
                                    )
                            }
                        }

                        XmlPullParser.END_TAG -> {

                            val name =
                                parser.name
                                    ?.lowercase()
                                    ?: ""

                            // =================================================
                            // NEWS ITEM END
                            // =================================================

                            if (
                                name == "news_item"
                            ) {

                                insideNewsItem =
                                    false
                            }

                            // =================================================
                            // TREND ITEM END
                            // =================================================

                            if (
                                name == "item"
                            ) {

                                insideItem =
                                    false

                                // -------------------------------------------------
                                // IMPORTANT:
                                //
                                // We use the nested news article URL.
                                //
                                // We DO NOT use:
                                //
                                // https://trends.google.com/trending/rss
                                //
                                // -------------------------------------------------

                                val finalTitle =
                                    if (
                                        newsTitle.isNotBlank()
                                    ) {

                                        newsTitle

                                    } else {

                                        trendTitle
                                    }

                                val finalUrl =
                                    newsUrl.trim()

                                val finalSource =
                                    if (
                                        newsSource.isNotBlank()
                                    ) {

                                        newsSource

                                    } else {

                                        "Google Trends"
                                    }

                                val finalImage =
                                    if (
                                        newsPicture.isNotBlank()
                                    ) {

                                        newsPicture

                                    } else {

                                        trendPicture
                                    }

                                // -------------------------------------------------
                                // DESCRIPTION
                                // -------------------------------------------------

                                val finalDescription =
                                    if (
                                        newsSnippet.isNotBlank()
                                    ) {

                                        newsSnippet

                                    } else if (
                                        trendTraffic.isNotBlank()
                                    ) {

                                        "$trendTraffic searches"

                                    } else {

                                        ""
                                    }

                                // -------------------------------------------------
                                // ONLY ADD REAL NEWS ARTICLES
                                // -------------------------------------------------

                                if (
                                    finalTitle.isNotBlank() &&
                                    finalUrl.isNotBlank() &&
                                    isValidUrl(
                                        finalUrl
                                    )
                                ) {

                                    articles.add(
                                        NewsArticle(
                                            title =
                                                cleanText(
                                                    finalTitle
                                                ),

                                            link =
                                                finalUrl,

                                            source =
                                                cleanText(
                                                    finalSource
                                                ),

                                            pubDate =
                                                cleanText(
                                                    trendDate
                                                ),

                                            description =
                                                cleanText(
                                                    finalDescription
                                                ),

                                            imageUrl =
                                                finalImage.trim()
                                        )
                                    )
                                }

                                if (
                                    articles.size >=
                                    limit
                                ) {

                                    break
                                }
                            }
                        }
                    }

                    eventType =
                        parser.next()
                }
            }

        } catch (
            e: Exception
        ) {

            e.printStackTrace()

        } finally {

            connection?.disconnect()
        }

        return articles
    }

    // =========================================================
    // IMAGE URL
    // =========================================================

    private fun getImageUrl(
        parser: XmlPullParser
    ): String? {

        val attributes =
            arrayOf(
                "url",
                "href",
                "src"
            )

        for (
            attribute in attributes
        ) {

            val value =
                parser.getAttributeValue(
                    null,
                    attribute
                )

            if (
                !value.isNullOrBlank() &&
                (
                    value.startsWith(
                        "http://"
                    ) ||
                    value.startsWith(
                        "https://"
                    )
                )
            ) {

                return value
            }
        }

        return null
    }

    // =========================================================
    // FIND IMAGE
    // =========================================================

    private fun findImageUrl(
        description: String,
        candidates: List<String>
    ): String {

        // ---------------------------------------------------------
        // FIRST: RSS IMAGE FIELDS
        // ---------------------------------------------------------

        for (
            candidate in candidates
        ) {

            if (
                isValidUrl(
                    candidate
                )
            ) {

                return decodeEntities(
                    candidate
                )
            }
        }

        // ---------------------------------------------------------
        // SECOND: HTML IMAGE INSIDE DESCRIPTION
        // ---------------------------------------------------------

        val imageRegex =
            Regex(
                """<img[^>]+(?:src|data-src|data-original|data-lazy-src)=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )

        val imageMatch =
            imageRegex.find(
                description
            )

        if (
            imageMatch != null
        ) {

            val image =
                decodeEntities(
                    imageMatch
                        .groupValues[1]
                        .trim()
                )

            if (
                isValidUrl(
                    image
                )
            ) {

                return image
            }
        }

        // ---------------------------------------------------------
        // THIRD: NORMAL IMAGE URL
        // ---------------------------------------------------------

        val urlRegex =
            Regex(
                """https?://[^\s"'<>]+?\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>]*)?""",
                RegexOption.IGNORE_CASE
            )

        val urlMatch =
            urlRegex.find(
                description
            )

        if (
            urlMatch != null
        ) {

            val image =
                decodeEntities(
                    urlMatch.value
                )

            if (
                isValidUrl(
                    image
                )
            ) {

                return image
            }
        }

        return ""
    }

    // =========================================================
    // SAFE XML TEXT
    // =========================================================

    private fun safeNextText(
        parser: XmlPullParser
    ): String {

        return try {

            parser.nextText()
                .trim()

        } catch (
            e: Exception
        ) {

            ""
        }
    }

    // =========================================================
    // SOURCE
    // =========================================================

    private fun detectSource(
        url: String
    ): String {

        return when {

            url.contains("bbci.co.uk") -> "BBC News"
            url.contains("aljazeera.com") -> "Al Jazeera"
            url.contains("theguardian.com") -> "The Guardian"
            url.contains("npr.org") -> "NPR"
            url.contains("dw.com") -> "DW"
            url.contains("euronews.com") -> "Euronews"
            url.contains("espn.com") -> "ESPN"
            url.contains("skysports.com") -> "Sky Sports"
            url.contains("cbssports.com") -> "CBS Sports"
            url.contains("google.com") -> "Google Trends"
            else -> "News"
        }
    }

    // =========================================================
    // URL VALIDATION
    // =========================================================

    private fun isValidUrl(
        url: String
    ): Boolean {

        return (
            url.startsWith(
                "http://"
            ) ||
            url.startsWith(
                "https://"
            )
        )
    }

    // =========================================================
    // HTML ENTITIES
    // =========================================================

    private fun decodeEntities(
        text: String
    ): String {

        return text
            .replace(
                "&amp;",
                "&"
            )
            .replace(
                "&quot;",
                "\""
            )
            .replace(
                "&#39;",
                "'"
            )
            .replace(
                "&apos;",
                "'"
            )
            .replace(
                "&lt;",
                "<"
            )
            .replace(
                "&gt;",
                ">"
            )
            .trim()
    }

    // =========================================================
    // CLEAN TEXT
    // =========================================================

    private fun cleanText(
        text: String
    ): String {

        return decodeEntities(
            text
                .replace(
                    Regex(
                        "<[^>]*>"
                    ),
                    ""
                )
                .replace(
                    Regex(
                        "\\s+"
                    ),
                    " "
                )
                .trim()
        )
    }
}
