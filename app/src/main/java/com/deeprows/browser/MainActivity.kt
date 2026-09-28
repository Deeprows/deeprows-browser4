package com.deeprows.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.webkit.CookieManager
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.DownloadManager
import android.os.Environment
import android.webkit.URLUtil
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.launch
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeSite(
    val name: String,
    val url: String
)

data class HomeSubCategory(
    val title: String,
    val sites: List<HomeSite>
)

data class HomeCategory(
    val title: String,
    val subCategories: List<HomeSubCategory>
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView

    private lateinit var addressBar: android.widget.EditText
    private lateinit var loadingBar: android.widget.ProgressBar

    private lateinit var dataSavingSwitch: Switch
    private lateinit var adBlockingSwitch: Switch

    private lateinit var vpnSwitch: Switch
    private lateinit var vpnLocationText: TextView
    private val vpnCountries by lazy { VpnCountryRepository.allCountries() }

    private val preferences by lazy {
        getSharedPreferences(
            "deeprows_browser",
            MODE_PRIVATE
        )
    }

    private val newsRepository =
        NewsRepository()

    // =========================================================
    // OPEN TABS
    // =========================================================

    data class BrowserTab(
        val id: Int,
        var title: String,
        var url: String
    )

    private val openTabs =
        mutableListOf<BrowserTab>()

    private var activeTabId = 0

    private var nextTabId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView =
            findViewById(R.id.webView)

        homePage =
            findViewById(R.id.homePage)

        settingsPage =
            findViewById(R.id.settingsPage)

        addressBar =
            findViewById(R.id.addressBar)

        loadingBar =
            findViewById(R.id.loadingBar)

        dataSavingSwitch =
            findViewById(R.id.dataSavingSwitch)

        adBlockingSwitch =
            findViewById(R.id.adBlockingSwitch)

        vpnSwitch = findViewById(R.id.vpnSwitch)
        vpnLocationText = findViewById(R.id.vpnLocationText)

        setupWebView()
        setupControls()
        setupDynamicHomepage()
        setupSettings()

        createNotificationChannel()
        requestNotificationPermission()
        showNotification(
            "Deeprows Browser",
            "Notifications are working!"
        )

        applyAppTheme()

        showHomePage()

        loadLatestNews()
        loadSportNews()
        loadGoogleTrends()

        hideSystemNavigationBar()
    }

    private fun setupDynamicHomepage() {

        val container =
            findViewById<LinearLayout>(R.id.siteCategoriesContainer)

        container.removeAllViews()

        val categories = listOf(

            HomeCategory(
                "🤖 AI TOOLS",
                listOf(

                    HomeSubCategory(
                        "🎬 AI VIDEO",
                        listOf(
                            HomeSite("Kling AI", "https://klingai.com/"),
                            HomeSite("Hailuo AI", "https://hailuoai.video/"),
                            HomeSite("Pika", "https://pika.art/"),
                            HomeSite("Runway", "https://runwayml.com/"),
                            HomeSite("Luma Dream Machine", "https://lumalabs.ai/dream-machine"),
                            HomeSite("PixVerse", "https://pixverse.ai/"),
                            HomeSite("Vidu", "https://www.vidu.com/"),
                            HomeSite("CapCut", "https://www.capcut.com/"),
                            HomeSite("Canva", "https://www.canva.com/"),
                            HomeSite("Krea", "https://www.krea.ai/")
                        )
                    ),

                    HomeSubCategory(
                        "🖼️ AI IMAGE",
                        listOf(
                            HomeSite("Microsoft Designer", "https://designer.microsoft.com/"),
                            HomeSite("Leonardo AI", "https://leonardo.ai/"),
                            HomeSite("Ideogram", "https://ideogram.ai/"),
                            HomeSite("Adobe Firefly", "https://firefly.adobe.com/"),
                            HomeSite("Google Gemini", "https://gemini.google.com/"),
                            HomeSite("Canva AI", "https://www.canva.com/ai-image-generator/"),
                            HomeSite("Playground AI", "https://playground.com/"),
                            HomeSite("Krea AI", "https://www.krea.ai/"),
                            HomeSite("Freepik AI", "https://www.freepik.com/ai/image-generator"),
                            HomeSite("Craiyon", "https://www.craiyon.com/")
                        )
                    ),

                    HomeSubCategory(
                        "🎵 AI AUDIO / MUSIC / VOICE",
                        listOf(
                            HomeSite("ElevenLabs", "https://elevenlabs.io/"),
                            HomeSite("Suno", "https://suno.com/"),
                            HomeSite("Udio", "https://udio.com/"),
                            HomeSite("Murf AI", "https://murf.ai/"),
                            HomeSite("PlayHT", "https://play.ht/"),
                            HomeSite("Speechify", "https://speechify.com/"),
                            HomeSite("AIVA", "https://www.aiva.ai/"),
                            HomeSite("Soundraw", "https://soundraw.io/"),
                            HomeSite("Adobe Podcast", "https://podcast.adobe.com/"),
                            HomeSite("TTSMaker", "https://ttsmaker.com/")
                        )
                    )
                )
            ),

            HomeCategory(
                "🎓 EDUCATION",
                listOf(

                    HomeSubCategory(
                        "📚 FREE ONLINE COURSES",
                        listOf(
                            HomeSite("MIT OpenCourseWare", "https://ocw.mit.edu/"),
                            HomeSite("OpenLearn", "https://www.open.edu/openlearn/"),
                            HomeSite("edX", "https://www.edx.org/"),
                            HomeSite("Coursera", "https://www.coursera.org/"),
                            HomeSite("Open Yale Courses", "https://oyc.yale.edu/"),
                            HomeSite("NPTEL", "https://nptel.ac.in/")
                        )
                    ),

                    HomeSubCategory(
                        "🎓 SCHOLARSHIPS & SPONSORSHIPS",
                        listOf(
                            HomeSite("Chevening", "https://www.chevening.org/"),
                            HomeSite("Erasmus+", "https://erasmus-plus.ec.europa.eu/"),
                            HomeSite("DAAD", "https://www.daad.de/en/studying-in-germany/scholarships/"),
                            HomeSite("Commonwealth", "https://cscuk.fcdo.gov.uk/scholarships-filter-search/"),
                            HomeSite("Mastercard Foundation", "https://mastercardfdn.org/all/scholars/"),
                            HomeSite("Swedish Institute", "https://si.se/en/apply/scholarships/"),
                            HomeSite("Opportunity Desk", "https://opportunitydesk.org/"),
                            HomeSite("Scholarship Positions", "https://www.scholarshippositions.com/"),
                            HomeSite("Studyportals", "https://www.mastersportal.com/scholarships/"),
                            HomeSite("African Union", "https://au.int/")
                        )
                    )
                )
            ),

            HomeCategory(
                "JOBS & CAREERS",
                listOf(
                    HomeSubCategory(
                        "💼",
                        listOf(
                            HomeSite("LinkedIn Jobs", "https://www.linkedin.com/jobs/"),
                            HomeSite("Indeed", "https://www.indeed.com/"),
                            HomeSite("Glassdoor", "https://www.glassdoor.com/"),
                            HomeSite("ZipRecruiter", "https://www.ziprecruiter.com/"),
                            HomeSite("Bayt", "https://www.bayt.com/"),
                            HomeSite("Jooble", "https://jooble.org/"),
                            HomeSite("Monster", "https://www.monster.com/"),
                            HomeSite("JobStreet", "https://www.jobstreet.com/"),
                            HomeSite("Wellfound", "https://wellfound.com/jobs")
                        )
                    )
                )
            ),

            HomeCategory(
                "🎬 ENTERTAINMENT",
                listOf(

                    HomeSubCategory(
                        "🎌 ANIME",
                        listOf(
                            HomeSite("Miruro", "https://www.miruro.tv/"),
                            HomeSite("AnimePahe", "https://animepahe.ru/"),
                            HomeSite("KickAssAnime", "https://kaa.to/")
                        )
                    ),

                    HomeSubCategory(
                        "🎨 CARTOONS",
                        listOf(
                            HomeSite("WatchCartoonOnline", "https://www.wco.tv/"),
                            HomeSite("SuperCartoons", "https://www.supercartoons.net/"),
                            HomeSite("Japanese Animated Film Classics", "https://animation.filmarchives.jp/")
                        )
                    ),

                    HomeSubCategory(
                        "🇰🇷 ASIAN / K-DRAMA",
                        listOf(
                            HomeSite("AsianCrush", "https://www.asiancrush.com/"),
                            HomeSite("OnDemandChina", "https://www.ondemandchina.com/"),
                            HomeSite("Einthusan", "https://einthusan.tv/")
                        )
                    ),

                    HomeSubCategory(
                        "🎞️ CLASSICS",
                        listOf(
                            HomeSite("Internet Archive", "https://archive.org/"),
                            HomeSite("WikiFlix", "https://wikiflix.toolforge.org/"),
                            HomeSite("NASA+", "https://plus.nasa.gov/")
                        )
                    )
                )
            ),

            HomeCategory(
                "SPORTS",
                listOf(

                    HomeSubCategory(
                        "⚽",
                        listOf(
                            HomeSite("ESPN", "https://www.espn.com/"),
                            HomeSite("BBC Sport", "https://www.bbc.com/sport"),
                            HomeSite("Sky Sports", "https://www.skysports.com/"),
                            HomeSite("Goal", "https://www.goal.com/"),
                            HomeSite("The Athletic", "https://www.nytimes.com/athletic/"),
                            HomeSite("CBS Sports", "https://www.cbssports.com/"),
                            HomeSite("FOX Sports", "https://www.foxsports.com/"),
                            HomeSite("Sporting News", "https://www.sportingnews.com/"),
                            HomeSite("Eurosport", "https://www.eurosport.com/"),
                            HomeSite("Sports Illustrated", "https://www.si.com/")
                        )
                    ),

                    HomeSubCategory(
                        "📼 SPORTS REPLAYS",
                        listOf(
                            HomeSite("Footballia", "https://footballia.online/"),
                            HomeSite("FullRaces", "https://fullraces.com/"),
                            HomeSite("HooFoot", "https://hoofoot.net/")
                        )
                    )
                )
            ),

            HomeCategory(
                "NEWS",
                listOf(
                    HomeSubCategory(
                        "📰",
                        listOf(
                            HomeSite("BBC News", "https://www.bbc.com/news"),
                            HomeSite("Reuters", "https://www.reuters.com/"),
                            HomeSite("AP News", "https://apnews.com/"),
                            HomeSite("CNN", "https://www.cnn.com/"),
                            HomeSite("Al Jazeera", "https://www.aljazeera.com/"),
                            HomeSite("The Guardian", "https://www.theguardian.com/international"),
                            HomeSite("New York Times", "https://www.nytimes.com/"),
                            HomeSite("Sky News", "https://news.sky.com/"),
                            HomeSite("France 24", "https://www.france24.com/en/"),
                            HomeSite("DW", "https://www.dw.com/en/")
                        )
                    )
                )
            ),

            HomeCategory(
                "📺 IPTV",
                listOf(

                    HomeSubCategory(
                        "🛠️ IPTV TOOLS",
                        listOf(
                            HomeSite("Awesome IPTV", "https://github.com/iptv-org/awesome-iptv"),
                            HomeSite("IPTV Playlists", "https://iptv-org.github.io/"),
                            HomeSite("M3Unator", "https://m3unator.com/"),
                            HomeSite("M3U4U", "https://m3u4u.com/"),
                            HomeSite("M3U8DL-RE", "https://github.com/nilaoda/N_m3u8DL-RE")
                        )
                    ),

                    HomeSubCategory(
                        "▶️ IPTV PLAYERS",
                        listOf(
                            HomeSite("IPTVnator", "https://github.com/4gray/iptvnator"),
                            HomeSite("ynoTV", "https://ynotv.com/"),
                            HomeSite("Open TV", "https://opentv.app/"),
                            HomeSite("LivePush", "https://livepush.io/"),
                            HomeSite("Jellyfin", "https://jellyfin.org/")
                        )
                    )
                )
            ),

            HomeCategory(
                "ANDROID TV APPS",
                listOf(
                    HomeSubCategory(
                        "📺",
                        listOf(
                            HomeSite("SmartTube", "https://github.com/yuliskov/SmartTube"),
                            HomeSite("TiviMate", "https://tivimate.com/"),
                            HomeSite("Downloader", "https://www.aftvnews.com/downloader/"),
                            HomeSite("CloudStream", "https://cloudstream3.com/"),
                            HomeSite("Nova Video Player", "https://github.com/nova-video-player/aos-AVP")
                        )
                    )
                )
            ),

            HomeCategory(
                "SOCIAL MEDIA",
                listOf(
                    HomeSubCategory(
                        "📲",
                        listOf(
                            HomeSite("Facebook", "https://www.facebook.com/"),
                            HomeSite("TikTok", "https://www.tiktok.com/"),
                            HomeSite("YouTube", "https://www.youtube.com/"),
                            HomeSite("X", "https://x.com/"),
                            HomeSite("Dailymotion", "https://www.dailymotion.com/")
                        )
                    )
                )
            ),
            HomeCategory(
                "MESSAGING",
                listOf(
                    HomeSubCategory(
                        "💬",
                        listOf(
                            HomeSite("WhatsApp", "https://web.whatsapp.com/"),
                            HomeSite("Snapchat", "https://www.snapchat.com/"),
                            HomeSite("Telegram", "https://web.telegram.org/")
                        )
                    )
                )
            )
        )

        categories.forEach { category ->

            addMainCategoryHeader(
                container,
                category.title
            )

            category.subCategories.forEach { subCategory ->

                addSubCategoryHeader(
                    container,
                    subCategory.title
                )

                addSiteGrid(
                    container,
                    subCategory.sites
                )
            }
        }

    }

    private fun addMainCategoryHeader(
        container: LinearLayout,
        title: String
    ) {
        val titleView = TextView(this)

        titleView.text = title
        titleView.textSize = 13f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(getThemeTextColor())

        titleView.setPadding(
            dp(8),
            dp(12),
            dp(8),
            dp(6)
        )

        container.addView(titleView)
    }

    private fun addSubCategoryHeader(
        container: LinearLayout,
        title: String
    ) {
        val titleView = TextView(this)

        titleView.text = title
        titleView.textSize = 10f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setTextColor(getThemeMutedColor())

        titleView.setPadding(
            dp(8),
            dp(4),
            dp(6),
            dp(4)
        )

        container.addView(titleView)
    }

    private fun addSiteGrid(
        container: LinearLayout,
        sites: List<HomeSite>
    ) {
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = (sites.size + 2) / 3
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(dp(2), dp(1), dp(2), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalPadding = dp(4)
        val gap = dp(5)
        val cardWidth = ((screenWidth - horizontalPadding - (gap * 2)) / 3).coerceAtLeast(dp(82))

        sites.forEachIndexed { index, site ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                elevation = dp(1).toFloat()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(getThemeSurfaceColor())
                    setStroke(dp(1), getThemeBorderColor())
                }
                tag = "site_card"
                isClickable = true
                isFocusable = true
                setOnClickListener { openWebsite(site.url) }
            }

            val logo = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "${site.name} logo"
            }

            val name = TextView(this).apply {
                tag = "site_name"
                text = site.name
                textSize = 9.5f
                setTextColor(getThemeTextColor())
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(dp(1), dp(4), dp(1), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            card.addView(logo)
            card.addView(name)

            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3),
                GridLayout.spec(index % 3)
            ).apply {
                width = cardWidth
                height = dp(68)
                setMargins(gap / 2, dp(3), gap / 2, dp(3))
            }

            grid.addView(card, params)
            loadSiteLogo(site.url, logo)
        }

        container.addView(grid)
    }

    private fun loadSiteLogo(
        siteUrl: String,
        imageView: ImageView
    ) {
        val logoUrl = try {
            val host = java.net.URL(siteUrl).host
            "https://www.google.com/s2/favicons?domain=$host&sz=64"
        } catch (e: Exception) {
            return
        }

        imageView.tag = logoUrl

        Thread {
            try {
                val connection =
                    java.net.URL(logoUrl).openConnection()
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val bitmap = connection.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }

                if (bitmap != null) {
                    runOnUiThread {
                        if (imageView.tag == logoUrl) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep the card usable if its favicon cannot load.
            }
        }.start()
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    // =========================================================
    // SYSTEM NAVIGATION BAR
    // =========================================================

    private fun hideSystemNavigationBar() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.R
        ) {

            window.insetsController?.let { controller ->

                controller.hide(
                    WindowInsets.Type.navigationBars()
                )

                controller.systemBarsBehavior =
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        } else {

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    // =========================================================
    // WEBVIEW
    // =========================================================

    private fun setupWebView() {

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            loadWithOverviewMode = false

            useWideViewPort = false
            setInitialScale(0)

            mediaPlaybackRequiresUserGesture = false

            userAgentString =
                "Mozilla/5.0 (Linux; Android 15; Mobile) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/153.0.0.0 Mobile Safari/537.36"

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            allowFileAccess = true

            allowContentAccess = true

            blockNetworkImage =
                preferences.getBoolean(
                    "data_saving",
                    false
                )
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
            )

        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {

                    loadingBar.visibility =
                        View.VISIBLE

                    if (
                        !url.isNullOrBlank() &&
                        (
                            url.startsWith("http://") ||
                                    url.startsWith("https://")
                            )
                    ) {

                        addressBar.setText(url)

                        saveHistory(url)
                    }
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    loadingBar.visibility =
                        View.GONE

                    if (
                        url != null &&
                        (
                            url.startsWith("http://") ||
                            url.startsWith("https://")
                        )
                    ) {

                        addressBar.setText(url)
                        // Update active tab information
                        val activeTab =
                            openTabs.find {
                                it.id == activeTabId
                            }

                        if (activeTab != null) {

                            activeTab.url =
                                url

                            activeTab.title =
                                view?.title
                                    ?.trim()
                                    ?.ifBlank {
                                        "New Tab"
                                    }
                                    ?: "New Tab"
                        }

                        // Hide search/address bar after website finishes loading
                        addressBar.visibility =
                            View.GONE

                        findViewById<View>(
                            R.id.goButton
                        ).visibility =
                            View.GONE
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    return false
                }
            }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startBrowserDownload(url, userAgent, contentDisposition, mimeType)
        }

        webView.setOnScrollChangeListener {
                _, _, _, _, _ ->
        }
    }

    // =========================================================
    // CONTROLS
    // =========================================================

    private fun setupControls() {

        findViewById<View>(
            R.id.goButton
        ).setOnClickListener {

            openAddress()
        }

        addressBar.setOnEditorActionListener {
                _, _, _ ->

            openAddress()

            true
        }

        findViewById<View>(
            R.id.backButton
        ).setOnClickListener {

            if (
                settingsPage.visibility ==
                View.VISIBLE
            ) {

                showHomePage()

            } else if (
                webView.visibility ==
                View.VISIBLE &&
                webView.canGoBack()
            ) {

                webView.goBack()

            } else {

                showHomePage()
            }
        }

        findViewById<View>(
            R.id.forwardButton
        ).setOnClickListener {

            if (
                webView.visibility ==
                View.VISIBLE &&
                webView.canGoForward()
            ) {

                webView.goForward()
            }
        }

        findViewById<View>(
            R.id.moreTrendsButton
        ).setOnClickListener {

            openWebsite(
                "https://trends.google.com/trending"
            )
        }

        findViewById<View>(R.id.trendsTranslateButton).setOnClickListener {
            translateVisibleTrendsToEnglish()
        }

        // =====================================================
        // REFRESH
        // =====================================================

        findViewById<View>(
            R.id.refreshPageButton
        ).setOnClickListener {

            if (
                webView.visibility ==
                View.VISIBLE
            ) {

                webView.reload()

            } else {

                Toast.makeText(
                    this,
                    "Open a website first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // =====================================================
        // OPEN PAGES
        // =====================================================

        findViewById<View>(
            R.id.refreshButton
        ).setOnClickListener {

            showOpenTabs()
        }

        // =====================================================
        // HOME
        // =====================================================

        findViewById<View>(
            R.id.homeButton
        ).setOnClickListener {

            showHomePage()
        }

        // =====================================================
        // BOOKMARK
        // =====================================================

        findViewById<View>(
            R.id.bookmarkButton
        ).setOnClickListener {

            saveCurrentBookmark()
        }

        // =====================================================
        // MENU
        // =====================================================

        findViewById<View>(
            R.id.menuButton
        ).setOnClickListener {

            showSettings()
        }

        // =====================================================
        // THEME
        // =====================================================

        findViewById<View>(
            R.id.themeButton
        ).setOnClickListener {
            showThemeSelector()
        }

        // =====================================================
        // NEWS BUTTONS
        // =====================================================

        findViewById<View>(
            R.id.moreNewsButton
        ).setOnClickListener {

            openWebsite(
                "https://news.google.com/"
            )
        }

        findViewById<View>(
            R.id.moreSportNewsButton
        ).setOnClickListener {

            openWebsite(
                "https://news.google.com/search?q=football"
            )
        }
    }

    // =========================================================
    // OPEN TABS
    // =========================================================

    private fun showOpenTabs() {

        if (openTabs.isEmpty()) {

            Toast.makeText(
                this,
                "No open tabs",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val container =
            android.widget.LinearLayout(this)

        container.orientation =
            android.widget.LinearLayout.VERTICAL

        container.setPadding(
            20,
            10,
            20,
            10
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Open Tabs (${openTabs.size})"
                )
                .setView(container)
                .setNegativeButton(
                    "Close",
                    null
                )
                .create()

        fun refreshTabList() {

            container.removeAllViews()

            dialog.setTitle(
                "Open Tabs (${openTabs.size})"
            )

            openTabs.forEach { tab ->

                val row =
                    android.widget.LinearLayout(this)

                row.orientation =
                    android.widget.LinearLayout.HORIZONTAL

                row.gravity =
                    android.view.Gravity.CENTER_VERTICAL

                row.setPadding(
                    12,
                    12,
                    8,
                    12
                )

                row.setBackgroundColor(
                    android.graphics.Color.parseColor(
                        if (tab.id == activeTabId)
                            "#26364F"
                        else
                            "#182437"
                    )
                )

                val rowParams =
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                rowParams.setMargins(
                    0,
                    0,
                    0,
                    8
                )

                row.layoutParams =
                    rowParams

                val title =
                    android.widget.TextView(this)

                title.text =
                    if (tab.id == activeTabId) {
                        "✓ ${tab.title}"
                    } else {
                        tab.title
                    }

                title.setTextColor(
                    android.graphics.Color.WHITE
                )

                title.textSize =
                    14f

                title.maxLines =
                    1

                title.ellipsize =
                    android.text.TextUtils.TruncateAt.END

                title.layoutParams =
                    android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                val closeButton =
                    android.widget.TextView(this)

                closeButton.text =
                    "✕"

                closeButton.gravity =
                    android.view.Gravity.CENTER

                closeButton.setTextColor(
                    android.graphics.Color.WHITE
                )

                closeButton.textSize =
                    18f

                closeButton.setPadding(
                    16,
                    8,
                    16,
                    8
                )

                row.addView(
                    title
                )

                row.addView(
                    closeButton
                )

                // Open this tab
                title.setOnClickListener {

                    activeTabId =
                        tab.id

                    homePage.visibility =
                        View.GONE

                    settingsPage.visibility =
                        View.GONE

                    webView.visibility =
                        View.VISIBLE

                    addressBar.visibility =
                        View.GONE

                    findViewById<View>(
                        R.id.goButton
                    ).visibility =
                        View.GONE

                    webView.loadUrl(
                        tab.url
                    )

                    dialog.dismiss()
                }

                // Close this tab
                closeButton.setOnClickListener {

                    val wasActive =
                        tab.id == activeTabId

                    openTabs.remove(
                        tab
                    )

                    if (openTabs.isEmpty()) {

                        activeTabId =
                            0

                        updateTabsCount()

                        dialog.dismiss()

                        showHomePage()

                        return@setOnClickListener
                    }

                    if (wasActive) {

                        val newActiveTab =
                            openTabs.last()

                        activeTabId =
                            newActiveTab.id

                        webView.loadUrl(
                            newActiveTab.url
                        )
                    }

                    updateTabsCount()

                    refreshTabList()
                }

                container.addView(
                    row
                )
            }
        }

        refreshTabList()

        dialog.show()
    }

    private fun loadWebsiteLogo(
        textView: android.widget.TextView,
        domain: String
    ) {

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO
        ).launch {

            try {

                val logoUrl =
                    "https://www.google.com/s2/favicons?domain=$domain&sz=128"

                val connection =
                    java.net.URL(
                        logoUrl
                    ).openConnection()

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val input =
                    connection.getInputStream()

                val bitmap =
                    android.graphics.BitmapFactory
                        .decodeStream(input)

                input.close()

                if (bitmap != null) {

                    runOnUiThread {

                        val density =
                            resources.displayMetrics.density

                        val size =
                            (30 * density).toInt()

                        val drawable =
                            android.graphics.drawable.BitmapDrawable(
                                resources,
                                bitmap
                            )

                        drawable.setBounds(
                            0,
                            0,
                            size,
                            size
                        )

                        textView.setCompoundDrawables(
                            null,
                            drawable,
                            null,
                            null
                        )

                        textView.compoundDrawablePadding =
                            (7 * density).toInt()

                        textView.gravity =
                            android.view.Gravity.CENTER

                        textView.includeFontPadding =
                            false

                        textView.setPadding(
                            (4 * density).toInt(),
                            (8 * density).toInt(),
                            (4 * density).toInt(),
                            (8 * density).toInt()
                        )
                    }
                }

            } catch (_: Exception) {

                // Keep the website name visible
            }
        }
    }

    private fun loadWebsiteLogoToImage(
        containerId: Int,
        domain: String
    ) {

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO
        ).launch {

            try {

                val logoUrl =
                    "https://www.google.com/s2/favicons?domain=$domain&sz=128"

                val connection =
                    java.net.URL(
                        logoUrl
                    ).openConnection()

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val input =
                    connection.getInputStream()

                val bitmap =
                    android.graphics.BitmapFactory
                        .decodeStream(input)

                input.close()

                if (bitmap != null) {

                    runOnUiThread {

                        val container =
                            findViewById<android.widget.LinearLayout>(
                                containerId
                            )

                        val imageView =
                            container.getChildAt(0)

                                as? android.widget.ImageView

                        imageView?.setImageBitmap(
                            bitmap
                        )
                    }
                }

            } catch (_: Exception) {

                // Keep the existing icon if logo fails
            }
        }
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    private val notificationChannelId =
        "deeprows_browser_notifications"

    private fun createNotificationChannel() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    notificationChannelId,
                    "Deeprows Browser",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {

                    description =
                        "Notifications from Deeprows Browser"
                }

            val notificationManager =
                getSystemService(
                    NotificationManager::class.java
                )

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    1001
                )
            }
        }
    }

    private fun showNotification(
        title: String,
        message: String
    ) {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification =
            NotificationCompat.Builder(
                this,
                notificationChannelId
            )
                .setSmallIcon(
                    R.drawable.deeprows_logo
                )
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat
            .from(this)
            .notify(
                System.currentTimeMillis().toInt(),
                notification
            )
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private fun setupVpnSettings() {
        vpnSwitch.isChecked = false
        vpnSwitch.isEnabled = false
        vpnSwitch.visibility = View.GONE
        vpnLocationText.text = "Country selection will be available soon"
        vpnLocationText.setTextColor(Color.parseColor("#9AA0A6"))

        findViewById<View>(R.id.vpnLocationButton).apply {
            isEnabled = false
            alpha = 0.55f
            setOnClickListener {
                Toast.makeText(this@MainActivity, "VPN is coming soon.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVpnCountryPicker() {
        Toast.makeText(this, "VPN is coming soon.", Toast.LENGTH_SHORT).show()
    }

    private fun VpnCountry.flag(): String = code
        .map { char -> String(Character.toChars(0x1F1E6 + (char.code - 'A'.code))) }
        .joinToString("")

    private fun setupSettings() {

        dataSavingSwitch.isChecked =
            preferences.getBoolean(
                "data_saving",
                false
            )

        setupVpnSettings()

        adBlockingSwitch.isChecked =
            preferences.getBoolean(
                "ad_blocking",
                false
            )

        dataSavingSwitch.setOnCheckedChangeListener {
                _, enabled ->

            preferences.edit()
                .putBoolean(
                    "data_saving",
                    enabled
                )
                .apply()

            webView.settings.blockNetworkImage =
                enabled

            Toast.makeText(
                this,
                if (enabled)
                    "Data Saving enabled"
                else
                    "Data Saving disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        adBlockingSwitch.setOnCheckedChangeListener {
                _, enabled ->

            preferences.edit()
                .putBoolean(
                    "ad_blocking",
                    enabled
                )
                .apply()

            Toast.makeText(
                this,
                if (enabled)
                    "Ad Blocking enabled"
                else
                    "Ad Blocking disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(
            R.id.historyButton
        ).setOnClickListener {

            showHistory()
        }

        findViewById<View>(
            R.id.bookmarksButton
        ).setOnClickListener {

            showBookmarks()
        }

        findViewById<View>(
            R.id.offlinePagesButton
        ).setOnClickListener {

            showOfflinePages()
        }

        findViewById<View>(
            R.id.clearCacheButton
        ).setOnClickListener {

            webView.clearCache(true)

            Toast.makeText(
                this,
                "Browser cache cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(R.id.downloadsButton).setOnClickListener {
            showDownloads()
        }

        findViewById<View>(
            R.id.shareButton
        ).setOnClickListener {

            val currentUrl =
                webView.url

            if (
                currentUrl.isNullOrBlank()
            ) {

                Toast.makeText(
                    this,
                    "No webpage to share",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val shareIntent =
                android.content.Intent(
                    android.content.Intent.ACTION_SEND
                ).apply {

                    type = "text/plain"

                    putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        currentUrl
                    )

                    putExtra(
                        android.content.Intent.EXTRA_SUBJECT,
                        webView.title
                            ?: "Deeprows Browser"
                    )
                }

            startActivity(
                android.content.Intent.createChooser(
                    shareIntent,
                    "Share page"
                )
            )
        }

        findViewById<View>(
            R.id.translateButton
        ).setOnClickListener {

            val currentUrl =
                webView.url

            if (
                currentUrl.isNullOrBlank()
            ) {

                Toast.makeText(
                    this,
                    "No webpage to translate",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val translateUrl =
                "https://translate.google.com/translate" +
                        "?sl=auto&tl=en&u=" +
                        android.net.Uri.encode(
                            currentUrl
                        )

            openWebsite(
                translateUrl
            )
        }

        findViewById<View>(
            R.id.settingsBackButton
        ).setOnClickListener {

            showHomePage()
        }
    }

    // =========================================================
    // HOME / SETTINGS / WEB
    // =========================================================

    private fun showHomePage() {

        homePage.visibility =
            View.VISIBLE

        webView.visibility =
            View.GONE

        settingsPage.visibility =
            View.GONE

        loadingBar.visibility =
            View.GONE

        addressBar.visibility =
            View.VISIBLE

        findViewById<View>(
            R.id.goButton
        ).visibility =
            View.VISIBLE
    }

    private fun showSettings() {

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.GONE

        settingsPage.visibility =
            View.VISIBLE

        loadingBar.visibility =
            View.GONE
    }

    private fun configureWebViewForUrl(url: String) {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
        val desktopOnly = host == "facebook.com" || host.endsWith(".facebook.com") ||
                host == "tiktok.com" || host.endsWith(".tiktok.com")
        val whatsapp = host == "web.whatsapp.com" || host.endsWith(".whatsapp.com")

        val mobileChrome = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36"
        val desktopChrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"

        webView.settings.userAgentString = if (desktopOnly || whatsapp) desktopChrome else mobileChrome
        webView.settings.useWideViewPort = desktopOnly || whatsapp
        webView.settings.loadWithOverviewMode = desktopOnly || whatsapp
        webView.settings.setInitialScale(0)

        if (desktopOnly) {
            webView.settings.textZoom = 100
        } else {
            webView.settings.textZoom = 100
        }
    }

    // =========================================================
    // OPEN WEBSITE
    // =========================================================

    private fun openWebsite(
        url: String
    ) {

        settingsPage.visibility =
            View.GONE

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.VISIBLE

        addressBar.visibility =
            View.GONE

        findViewById<View>(
            R.id.goButton
        ).visibility =
            View.GONE

        // Create a new tab
        val newTab =
            BrowserTab(
                id = nextTabId++,
                title = "New Tab",
                url = url
            )

        openTabs.add(
            newTab
        )

        activeTabId =
            newTab.id

        updateTabsCount()

        configureWebViewForUrl(url)
        webView.loadUrl(url)
    }

    // =========================================================
    // UPDATE TABS COUNT
    // =========================================================

    private fun updateTabsCount() {

        findViewById<android.widget.TextView>(
            R.id.pagesCount
        ).text =
            openTabs.size.toString()
    }

    // =========================================================
    // ADDRESS BAR
    // =========================================================

    private fun openAddress() {

        var text =
            addressBar.text
                .toString()
                .trim()

        if (text.isBlank()) {
            return
        }

        if (
            !text.startsWith("http://") &&
            !text.startsWith("https://")
        ) {

            if (
                text.contains(".") &&
                !text.contains(" ")
            ) {

                text =
                    "https://$text"

            } else {

                text =
                    "https://www.google.com/search?q=" +
                            Uri.encode(text)
            }
        }

        openWebsite(text)
    }

    private fun startBrowserDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (url.isBlank()) return

        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading with Deeprows Browser")
                setMimeType(mimeType ?: "application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                addRequestHeader("Referer", webView.url ?: url)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            val ids = preferences.getStringSet("download_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            ids.add(downloadId.toString())
            preferences.edit().putStringSet("download_ids", ids).apply()

            Toast.makeText(this, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed to start", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDownloads() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val ids = preferences.getStringSet("download_ids", emptySet())?.mapNotNull { it.toLongOrNull() } ?: emptyList()

        if (ids.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Downloads")
                .setMessage("No downloads yet. Downloads from websites will appear here.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val entries = mutableListOf<Pair<Long, String>>()
        ids.asReversed().forEach { id ->
            try {
                manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val title = if (titleIndex >= 0) cursor.getString(titleIndex) else "Download $id"
                        val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED
                        val label = when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> "✓ $title"
                            DownloadManager.STATUS_RUNNING -> "↓ $title  • downloading"
                            DownloadManager.STATUS_PENDING -> "… $title  • pending"
                            else -> "! $title  • failed"
                        }
                        entries.add(id to label)
                    }
                }
            } catch (_: Exception) { }
        }

        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Downloads")
                .setMessage("No tracked downloads are available.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Downloads")
            .setItems(entries.map { it.second }.toTypedArray()) { _, which ->
                val id = entries[which].first
                try {
                    manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED
                            val localUri = if (uriIndex >= 0) cursor.getString(uriIndex) else null
                            if (status == DownloadManager.STATUS_SUCCESSFUL && !localUri.isNullOrBlank()) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(localUri)).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } else {
                                Toast.makeText(this, "Download is not ready yet.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "Unable to open download.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // =========================================================
    // HISTORY
    // =========================================================

    private fun saveHistory(
        url: String
    ) {

        if (
            url.isBlank() ||
            url == "about:blank" ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {
            return
        }

        val history =
            preferences.getStringSet(
                "history",
                emptySet()
            )?.toMutableList()
                ?: mutableListOf()

        history.remove(url)

        history.add(
            0,
            url
        )

        val limited =
            history
                .take(50)
                .toSet()

        preferences.edit()
            .putStringSet(
                "history",
                limited
            )
            .apply()
    }

    private fun showHistory() {

        val history =
            preferences.getStringSet(
                "history",
                emptySet()
            )?.toList()
                ?: emptyList()

        if (history.isEmpty()) {

            AlertDialog.Builder(this)
                .setTitle("History")
                .setMessage(
                    "No browsing history yet."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        AlertDialog.Builder(this)
            .setTitle("History")
            .setItems(
                history.toTypedArray()
            ) { _, which ->

                openWebsite(
                    history[which]
                )
            }
            .setNegativeButton(
                "Clear History"
            ) { _, _ ->

                preferences.edit()
                    .remove("history")
                    .apply()

                Toast.makeText(
                    this,
                    "History cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    // =========================================================
    // BOOKMARKS
    // =========================================================

    private fun saveCurrentBookmark() {

        val url =
            webView.url

        if (
            url.isNullOrBlank() ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {

            Toast.makeText(
                this,
                "Open a website first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val bookmarks =
            preferences.getStringSet(
                "bookmarks",
                emptySet()
            )?.toMutableSet()
                ?: mutableSetOf()

        bookmarks.add(url)

        preferences.edit()
            .putStringSet(
                "bookmarks",
                bookmarks
            )
            .apply()

        Toast.makeText(
            this,
            "Page bookmarked",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {

        val bookmarks =
            preferences.getStringSet(
                "bookmarks",
                emptySet()
            )?.toList()
                ?: emptyList()

        if (bookmarks.isEmpty()) {

            AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setMessage(
                    "No bookmarks saved yet."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(
                bookmarks.toTypedArray()
            ) { _, which ->

                openWebsite(
                    bookmarks[which]
                )
            }
            .setNegativeButton(
                "Clear All"
            ) { _, _ ->

                preferences.edit()
                    .remove("bookmarks")
                    .apply()

                Toast.makeText(
                    this,
                    "Bookmarks cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    // =========================================================
    // OFFLINE PAGES
    // =========================================================

    private fun getOfflineDirectory(): File {

        val directory =
            File(
                filesDir,
                "offline_pages"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return directory
    }

    private fun saveOfflinePage() {

        val url =
            webView.url

        if (
            url.isNullOrBlank() ||
            url == "about:blank" ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {

            Toast.makeText(
                this,
                "Open a website first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (webView.progress < 100) {

            Toast.makeText(
                this,
                "Wait until the page finishes loading",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val title =
            webView.title
                ?.trim()
                ?.ifBlank {
                    "Offline Page"
                }
                ?: "Offline Page"

        val safeTitle =
            title
                .replace(
                    Regex("[^A-Za-z0-9 _-]"),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    "_"
                )
                .take(50)
                .ifBlank {
                    "Offline_Page"
                }

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val file =
            File(
                getOfflineDirectory(),
                "${safeTitle}_$timestamp.mht"
            )

        Toast.makeText(
            this,
            "Saving offline page...",
            Toast.LENGTH_SHORT
        ).show()

        webView.saveWebArchive(
            file.absolutePath,
            false
        ) { savedPath ->

            runOnUiThread {

                if (
                    !savedPath.isNullOrBlank() &&
                    File(savedPath).exists()
                ) {

                    Toast.makeText(
                        this,
                        "Offline page saved",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Unable to save offline page",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showOfflinePages() {

        val directory =
            getOfflineDirectory()

        val files =
            directory
                .listFiles { file ->

                    file.isFile &&
                            file.extension.equals(
                                "mht",
                                ignoreCase = true
                            )
                }
                ?.sortedByDescending {
                    it.lastModified()
                }
                ?: emptyList()

        val builder =
            AlertDialog.Builder(this)
                .setTitle("Offline Pages")

        if (files.isEmpty()) {

            builder.setMessage(
                "No offline pages saved yet."
            )

        } else {

            val names =
                files.map { file ->

                    file.nameWithoutExtension
                        .replace("_", " ")

                }.toTypedArray()

            builder.setItems(
                names
            ) { _, which ->

                openOfflinePage(
                    files[which]
                )
            }
        }

        builder.setPositiveButton(
            "Save Current"
        ) { _, _ ->

            saveOfflinePage()
        }

        if (files.isNotEmpty()) {

            builder.setNeutralButton(
                "Clear All"
            ) { _, _ ->

                clearOfflinePages()
            }
        }

        builder.setNegativeButton(
            "Close",
            null
        )

        builder.show()
    }

    private fun openOfflinePage(
        file: File
    ) {

        if (!file.exists()) {

            Toast.makeText(
                this,
                "Offline page no longer exists",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        settingsPage.visibility =
            View.GONE

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.VISIBLE

        loadingBar.visibility =
            View.GONE

        addressBar.setText(
            "Offline: " +
                    file.nameWithoutExtension
                        .replace("_", " ")
        )

        webView.loadUrl(
            Uri.fromFile(file).toString()
        )
    }

    private fun clearOfflinePages() {

        val directory =
            getOfflineDirectory()

        val files =
            directory.listFiles()

        var deleted = 0

        files?.forEach { file ->

            if (
                file.isFile &&
                file.extension.equals(
                    "mht",
                    ignoreCase = true
                )
            ) {

                if (file.delete()) {
                    deleted++
                }
            }
        }

        Toast.makeText(
            this,
            "$deleted offline page(s) deleted",
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // NEWS
    // =========================================================

    private fun loadLatestNews() {

        val newsList =
            findViewById<android.widget.LinearLayout>(
                R.id.newsList
            )

        newsList.removeAllViews()

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val articles =
                newsRepository.getLatestNews(4)

            articles.forEach { article ->

                addNewsCard(
                    newsList,
                    article
                )
            }
        }
    }

    private fun loadSportNews() {

        val sportNewsList =
            findViewById<android.widget.LinearLayout>(
                R.id.sportNewsList
            )

        sportNewsList.removeAllViews()

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val articles =
                newsRepository.getSportNews(4)

            articles.forEach { article ->

                addNewsCard(
                    sportNewsList,
                    article
                )
            }
        }
    }

    private fun loadGoogleTrends() {

        val trendsList =
            findViewById<android.widget.LinearLayout>(
                R.id.trendsList
            )

        trendsList.removeAllViews()

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val countryCode = CountryProvider.getCountryCode(this@MainActivity)
            val trendsCountry = findViewById<TextView>(R.id.trendsLocation)
            trendsCountry.text = if (countryCode.isBlank()) {
                "Worldwide"
            } else {
                Locale.Builder().setRegion(countryCode).build().displayCountry
            }

            val trends =
                newsRepository.getGoogleTrends(countryCode.ifBlank { null }, 10)

            if (trends.isEmpty()) {

                val emptyText =
                    android.widget.TextView(this@MainActivity)

                emptyText.text =
                    "Unable to load Google Trends"

                emptyText.setTextColor(
                    android.graphics.Color.LTGRAY
                )

                emptyText.textSize =
                    13f

                emptyText.setPadding(
                    8,
                    12,
                    8,
                    12
                )

                trendsList.addView(
                    emptyText
                )

                return@launch
            }

            trends.forEachIndexed { index, trend ->

                val trendRow =
                    android.widget.LinearLayout(
                        this@MainActivity
                    )

                trendRow.orientation =
                    android.widget.LinearLayout.HORIZONTAL

                trendRow.gravity =
                    android.view.Gravity.CENTER_VERTICAL

                trendRow.setPadding(
                    8,
                    10,
                    8,
                    10
                )

                trendRow.tag = "trend_row"
                trendRow.setBackgroundColor(
                    getThemeSurface2Color()
                )

                val number =
                    android.widget.TextView(
                        this@MainActivity
                    )

                number.text =
                    "${index + 1}"

                number.setTextColor(
                    getThemeAccentColor()
                )

                number.tag =
                    "accent"

                number.textSize =
                    14f

                number.gravity =
                    android.view.Gravity.CENTER

                val numberParams =
                    android.widget.LinearLayout.LayoutParams(
                        32,
                        48
                    )

                trendRow.addView(
                    number,
                    numberParams
                )

                val textContainer =
                    android.widget.LinearLayout(
                        this@MainActivity
                    )

                textContainer.orientation =
                    android.widget.LinearLayout.VERTICAL

                textContainer.layoutParams =
                    android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                val title =
                    android.widget.TextView(
                        this@MainActivity
                    )

                title.tag = "trend_title"
                title.text =
                    trend.title

                title.setTextColor(
                    android.graphics.Color.WHITE
                )

                title.textSize =
                    14f

                title.maxLines =
                    2

                title.ellipsize =
                    android.text.TextUtils.TruncateAt.END

                textContainer.addView(
                    title
                )

                val source =
                    android.widget.TextView(
                        applicationContext
                    )

                source.text =
                    if (trend.source.isNotBlank()) {
                        trend.source
                    } else {
                        "Google Trends"
                    }

                source.setTextColor(
                    getThemeAccentColor()
                )

                source.tag =
                    "accent"

                source.textSize =
                    10f

                source.setPadding(
                    0,
                    4,
                    0,
                    0
                )

                textContainer.addView(
                    source
                )

                trendRow.addView(
                    textContainer
                )

                trendRow.setOnClickListener {

                    if (
                        trend.link.isNotBlank()
                    ) {

                        openWebsite(
                            trend.link
                        )
                    }
                }

                val rowParams =
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                rowParams.setMargins(
                    0,
                    0,
                    0,
                    5
                )

                trendsList.addView(
                    trendRow,
                    rowParams
                )
            }
        }
    }

    private fun translateVisibleTrendsToEnglish() {
        val trendsList = findViewById<LinearLayout>(R.id.trendsList)
        val button = findViewById<TextView>(R.id.trendsTranslateButton)
        val titles = (0 until trendsList.childCount).mapNotNull { index ->
            val row = trendsList.getChildAt(index) as? ViewGroup
            row?.findViewWithTag<TextView>("trend_title")
        }

        if (titles.isEmpty()) {
            Toast.makeText(this, "No trends to translate yet.", Toast.LENGTH_SHORT).show()
            return
        }

        button.isEnabled = false
        button.text = "Translating…"
        var remaining = titles.size

        titles.forEach { titleView ->
            val original = titleView.text.toString().trim()
            if (original.isBlank()) {
                remaining--
                return@forEach
            }

            val languageIdentifier = LanguageIdentification.getClient()
            languageIdentifier.identifyLanguage(original)
                .addOnSuccessListener { languageCode ->
                    val source = TranslateLanguage.fromLanguageTag(languageCode)
                    if (source == null || languageCode == "en" || languageCode == "und") {
                        remaining--
                        if (remaining <= 0) {
                            button.isEnabled = true
                            button.text = "Translate"
                        }
                        languageIdentifier.close()
                        return@addOnSuccessListener
                    }

                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build()
                    val translator = Translation.getClient(options)
                    translator.downloadModelIfNeeded()
                        .addOnSuccessListener {
                            translator.translate(original)
                                .addOnSuccessListener { translated ->
                                    titleView.text = translated
                                    remaining--
                                    if (remaining <= 0) {
                                        button.isEnabled = true
                                        button.text = "Translate"
                                    }
                                    translator.close()
                                    languageIdentifier.close()
                                }
                                .addOnFailureListener {
                                    remaining--
                                    if (remaining <= 0) {
                                        button.isEnabled = true
                                        button.text = "Translate"
                                    }
                                    translator.close()
                                    languageIdentifier.close()
                                }
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining <= 0) {
                                button.isEnabled = true
                                button.text = "Translate"
                            }
                            translator.close()
                            languageIdentifier.close()
                        }
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining <= 0) {
                        button.isEnabled = true
                        button.text = "Translate"
                    }
                    languageIdentifier.close()
                }
        }
    }

    private fun addNewsCard(
        container: android.widget.LinearLayout,
        article: NewsArticle
    ) {

        val card =
            android.widget.LinearLayout(this)

        card.orientation =
            android.widget.LinearLayout.HORIZONTAL
        card.tag = "news_card"

        card.setPadding(
            14,
            14,
            14,
            14
        )

        card.setBackgroundColor(
            getThemeSurfaceColor()
        )

        val cardParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.setMargins(
            0,
            0,
            0,
            10
        )

        card.layoutParams =
            cardParams

        val imageView =
            android.widget.ImageView(this)

        val imageParams =
            android.widget.LinearLayout.LayoutParams(
                105,
                85
            )

        imageParams.setMargins(
            0,
            0,
            14,
            0
        )

        imageView.layoutParams =
            imageParams

        imageView.scaleType =
            android.widget.ImageView.ScaleType.CENTER_CROP

        imageView.setBackgroundColor(
            getThemeSurface2Color()
        )

        val textContainer =
            android.widget.LinearLayout(this)

        textContainer.orientation =
            android.widget.LinearLayout.VERTICAL

        textContainer.layoutParams =
            android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        val title =
            android.widget.TextView(this)

        title.text =
            article.title

        title.setTextColor(
            getThemeTextColor()
        )

        title.textSize =
            15f

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        title.maxLines =
            3

        title.ellipsize =
            android.text.TextUtils.TruncateAt.END

        val source =
            android.widget.TextView(this)

        source.text =
            if (article.source.isNotBlank()) {
                article.source
            } else {
                "News"
            }

        source.setTextColor(
            getThemeAccentColor()
        )

        source.tag =
            "accent"

        source.textSize =
            12f

        source.setPadding(
            0,
            8,
            0,
            0
        )

        textContainer.addView(
            title
        )

        textContainer.addView(
            source
        )

        card.addView(
            imageView
        )

        card.addView(
            textContainer
        )

        card.setOnClickListener {

            openWebsite(
                article.link
            )
        }

        container.addView(
            card
        )

        if (
            article.imageUrl.isNotBlank()
        ) {

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO
            ).launch {

                try {

                    val connection =
                        java.net.URL(
                            article.imageUrl
                        ).openConnection()

                    connection.connect()

                    val input =
                        connection.getInputStream()

                    val bitmap =
                        android.graphics.BitmapFactory
                            .decodeStream(input)

                    input.close()

                    runOnUiThread {

                        if (bitmap != null) {

                            imageView.setImageBitmap(
                                bitmap
                            )
                        }
                    }

                } catch (_: Exception) {

                    // Keep placeholder
                }
            }
        }
    }

    // =========================================================
    // THEME SELECTOR
    // =========================================================

    private fun showThemeSelector() {

        val themes = arrayOf(
            "Chrome Light",
            "Midnight",
            "Graphite",
            "Ocean",
            "Rose"
        )

        val currentTheme =
            preferences.getString(
                "app_theme",
                "Chrome Light"
            )

        var selectedIndex =
            themes.indexOf(currentTheme)

        if (selectedIndex < 0) {
            selectedIndex = 0
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(
                themes,
                selectedIndex
            ) { dialog, which ->

                preferences.edit()
                    .putString(
                        "app_theme",
                        themes[which]
                    )
                    .apply()

                applyAppTheme()

                Toast.makeText(
                    this,
                    "${themes[which]} selected",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // APPLY THEME
    // =========================================================

    private fun applyAppTheme() {
        val theme = preferences.getString("app_theme", "Chrome Light")

        val backgroundColor: Int
        val surfaceColor: Int
        val surface2Color: Int
        val textColor: Int
        val mutedColor: Int
        val accentColor: Int

        when (theme) {
            "Midnight" -> {
                backgroundColor = Color.parseColor("#0F1115")
                surfaceColor = Color.parseColor("#171A20")
                surface2Color = Color.parseColor("#20252E")
                textColor = Color.parseColor("#F8FAFC")
                mutedColor = Color.parseColor("#AAB4C3")
                accentColor = Color.parseColor("#8AB4F8")
            }

            "Graphite" -> {
                backgroundColor = Color.parseColor("#12161C")
                surfaceColor = Color.parseColor("#1A2028")
                surface2Color = Color.parseColor("#242C36")
                textColor = Color.parseColor("#F1F5F9")
                mutedColor = Color.parseColor("#A7B0BD")
                accentColor = Color.parseColor("#9AA7B8")
            }

            "Ocean" -> {
                backgroundColor = Color.parseColor("#F2F8FB")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#E7F2F8")
                textColor = Color.parseColor("#18313F")
                mutedColor = Color.parseColor("#607D8B")
                accentColor = Color.parseColor("#0B84C6")
            }

            "Rose" -> {
                backgroundColor = Color.parseColor("#FFF7F8")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#FBECEF")
                textColor = Color.parseColor("#2D2024")
                mutedColor = Color.parseColor("#8D6D74")
                accentColor = Color.parseColor("#D94F70")
            }

            else -> {
                backgroundColor = Color.parseColor("#F6F7F9")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#F0F2F5")
                textColor = Color.parseColor("#202124")
                mutedColor = Color.parseColor("#5F6368")
                accentColor = Color.parseColor("#1A73E8")
            }
        }

        // Main backgrounds
        findViewById<View>(android.R.id.content)
            .setBackgroundColor(backgroundColor)

        homePage.setBackgroundColor(backgroundColor)
        settingsPage.setBackgroundColor(backgroundColor)
        webView.setBackgroundColor(backgroundColor)

        // Homepage containers
        homePage.getChildAt(0)
            ?.setBackgroundColor(backgroundColor)

        findViewById<View>(R.id.categoryContainer)
            .setBackgroundColor(backgroundColor)

        findViewById<View>(R.id.siteCategoriesContainer)
            .setBackgroundColor(Color.TRANSPARENT)

        // Homepage content cards
        val categoryRoot = findViewById<GridLayout>(R.id.categoryContainer)
        for (i in 3 until categoryRoot.childCount) {
            val child = categoryRoot.getChildAt(i)
            if (child is LinearLayout) {
                child.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(surfaceColor)
                    setStroke(dp(1), getThemeBorderColor())
                }
            }
        }

        // Address bar
        addressBar.setBackgroundColor(Color.TRANSPARENT)
        addressBar.setTextColor(textColor)
        addressBar.setHintTextColor(mutedColor)

        val bottomBar = findViewById<LinearLayout>(R.id.bottomBar)
        bottomBar.setBackgroundColor(surfaceColor)
        for (i in 0 until bottomBar.childCount) {
            val item = bottomBar.getChildAt(i)
            if (item is ViewGroup) {
                for (j in 0 until item.childCount) {
                    val v = item.getChildAt(j)
                    if (v is ImageView) v.setColorFilter(mutedColor)
                    if (v is TextView) v.setTextColor(mutedColor)
                }
            }
        }
        findViewById<ImageView>(R.id.homeButton).setColorFilter(accentColor)
        (findViewById<ImageView>(R.id.homeButton).parent as? ViewGroup)?.let { parent ->
            for (j in 0 until parent.childCount) {
                val v = parent.getChildAt(j)
                if (v is TextView) v.setTextColor(accentColor)
            }
        }

        val categoryContainer = findViewById<LinearLayout>(R.id.siteCategoriesContainer)
        for (i in 0 until categoryContainer.childCount) {
            val child = categoryContainer.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(
                    if (child.textSize >= 14f * resources.displayMetrics.scaledDensity) {
                        textColor
                    } else {
                        mutedColor
                    }
                )
            }
        }

        fun tintHomepageText(view: View) {
            if (view is TextView) {
                when {
                    view.tag == "accent" -> view.setTextColor(accentColor)
                    view.text.toString().startsWith("More") ||
                        view.text.toString().startsWith("View Google") -> view.setTextColor(accentColor)
                    view.text.toString().startsWith("Loading") ||
                        view.text.toString() == "Worldwide" -> view.setTextColor(mutedColor)
                    else -> view.setTextColor(textColor)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintHomepageText(view.getChildAt(i))
                }
            }
        }

        for (i in 3 until categoryRoot.childCount) {
            tintHomepageText(categoryRoot.getChildAt(i))
        }

        fun refreshHomepageComponents(view: View) {
            when (view.tag) {
                "site_card" -> {
                    view.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(16).toFloat()
                        setColor(surfaceColor)
                        setStroke(dp(1), getThemeBorderColor())
                    }
                }
                "site_name" -> {
                    (view as? TextView)?.setTextColor(textColor)
                }
                "trend_row" -> {
                    view.setBackgroundColor(surface2Color)
                }
                "news_card" -> {
                    view.setBackgroundColor(surfaceColor)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    refreshHomepageComponents(view.getChildAt(i))
                }
            }
        }
        refreshHomepageComponents(categoryRoot)

        val searchBar = addressBar.parent as? View

        searchBar?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(surfaceColor)
            setStroke(
                dp(1),
                getThemeBorderColor()
            )
        }

        // Go button
        findViewById<View>(R.id.goButton).background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(13).toFloat()
                setColor(accentColor)
            }
        findViewById<ImageView>(R.id.goButton).setColorFilter(Color.WHITE)

        // Settings background
        settingsPage.setBackgroundColor(backgroundColor)

        // Settings cards
        val settingsCardIds = listOf(
            R.id.dataSavingSwitch,
            R.id.adBlockingSwitch,
            R.id.historyButton,
            R.id.bookmarksButton,
            R.id.offlinePagesButton,
            R.id.clearCacheButton,
            R.id.themeButton,
            R.id.downloadsButton,
            R.id.shareButton,
            R.id.translateButton,
            R.id.settingsBackButton
        )

        settingsCardIds.forEach { id ->
            val view = findViewById<View>(id)
            val card = if (id == R.id.dataSavingSwitch || id == R.id.adBlockingSwitch) {
                view.parent as? View
            } else {
                view
            }

            card?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1), getThemeBorderColor())
            }

            if (view is TextView) {
                view.setTextColor(
                    if (id == R.id.settingsBackButton) accentColor else textColor
                )
            }
        }

        // Make every settings label readable in every theme.
        fun tintSettingsText(view: View) {
            if (view is TextView && view.id != R.id.settingsBackButton) {
                view.setTextColor(
                    if (view.text.toString().contains("Customize") ||
                        view.text.toString().contains("Reduce") ||
                        view.text.toString().contains("Block common")
                    ) mutedColor else textColor
                )
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintSettingsText(view.getChildAt(i))
                }
            }
        }
        tintSettingsText(settingsPage)

        // System bars
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
    }

    // =========================================================
    // THEME COLORS
    // =========================================================

    private fun getThemeTextColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#F8FAFC")
            "Graphite" -> Color.parseColor("#F1F5F9")
            "Ocean" -> Color.parseColor("#18313F")
            "Rose" -> Color.parseColor("#2D2024")
            else -> Color.parseColor("#202124")
        }
    }

    private fun getThemeMutedColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#AAB4C3")
            "Graphite" -> Color.parseColor("#A7B0BD")
            "Ocean" -> Color.parseColor("#607D8B")
            "Rose" -> Color.parseColor("#8D6D74")
            else -> Color.parseColor("#5F6368")
        }
    }

    private fun getThemeBorderColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#2B3442")
            "Graphite" -> Color.parseColor("#303946")
            "Ocean" -> Color.parseColor("#CDE8F5")
            "Rose" -> Color.parseColor("#F0D9DE")
            else -> Color.parseColor("#E1E5EA")
        }
    }

    private fun getThemeSurfaceColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#171A20")
            "Graphite" -> Color.parseColor("#1A2028")
            "Ocean" -> Color.WHITE
            "Rose" -> Color.WHITE
            else -> Color.WHITE
        }
    }

    private fun getThemeSurface2Color(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#20252E")
            "Graphite" -> Color.parseColor("#242C36")
            "Ocean" -> Color.parseColor("#E7F2F8")
            "Rose" -> Color.parseColor("#FBECEF")
            else -> Color.parseColor("#F0F2F5")
        }
    }

    private fun getThemeAccentColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#8AB4F8")
            "Graphite" -> Color.parseColor("#9AA7B8")
            "Ocean" -> Color.parseColor("#0B84C6")
            "Rose" -> Color.parseColor("#D94F70")
            else -> Color.parseColor("#1A73E8")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "VPN permission granted. Connect after a server is configured.", Toast.LENGTH_LONG).show()
            } else {
                vpnSwitch.isChecked = false
            }
        }
    }

    companion object {
        private const val VPN_PERMISSION_REQUEST = 9401
    }

}
