package com.family.tv.ui

import com.family.tv.data.AppLanguage
import com.family.tv.data.Section
import com.family.tv.data.SubtitleSize

class AppStrings(val lang: AppLanguage) {
    private val tr = lang == AppLanguage.TR

    val appTitle = "Family TV"
    val chooseWhatToWatch = if (tr) "Ne izlemek istediğinizi seçin" else "Choose what you want to watch"
    val settings = if (tr) "Ayarlar" else "Settings"
    val switchPlaylist = if (tr) "Liste Değiştir" else "Switch Playlist"
    val homeTip = if (tr) {
        "İpucu: Favorilere eklemek için herhangi bir içeriğin üzerinde OK tuşuna basılı tutun"
    } else {
        "Tip: Hold OK on any channel, movie, or series to save it to Favorites"
    }

    fun sectionTitle(s: Section): String = when (s) {
        Section.LIVE -> if (tr) "Canlı TV" else "Live TV"
        Section.MOVIES -> if (tr) "Filmler" else "Movies"
        Section.SERIES -> if (tr) "Diziler" else "Series"
    }

    fun sectionTag(s: Section): String = when (s) {
        Section.LIVE -> if (tr) "CANLI YAYIN" else "BROADCAST"
        Section.MOVIES -> if (tr) "İSTEĞE BAĞLI" else "ON DEMAND"
        Section.SERIES -> if (tr) "SEZON & BÖLÜM" else "SEASONS & EPISODES"
    }

    fun channelsCount(n: Int) = if (tr) "$n kanal" else "$n channels"
    fun titlesCount(n: Int) = if (tr) "$n film" else "$n titles"
    fun showsCount(n: Int) = if (tr) "$n dizi" else "$n shows"

    // Browse & EPG
    fun continueWatchingLabel(n: Int) = if (tr) "▶  Devam Et ($n)" else "▶  Continue ($n)"
    fun continueWatchingHeader(n: Int) = if (tr) "İzlemeye Devam Et ($n)" else "Continue Watching ($n)"
    val noContinueWatching = if (tr) "Yarım kalan içerik yok" else "No unfinished titles right now"
    fun favoritesLabel(n: Int) = if (tr) "★  Favoriler ($n)" else "★  Favorites ($n)"
    fun recentLabel(n: Int) = if (tr) "◷  Son İzlenen ($n)" else "◷  Recent ($n)"
    fun allLabel(n: Int) = if (tr) "Tümü ($n)" else "All ($n)"
    fun favoritesHeader(n: Int) = if (tr) "Favoriler ($n)" else "Favorites ($n)"
    fun recentHeader(n: Int) = if (tr) "Son İzlenenler ($n)" else "Recently Watched ($n)"
    fun allHeader(sec: Section, n: Int) = if (tr) "Tüm ${sectionTitle(sec)} ($n)" else "All ${sectionTitle(sec)} ($n)"
    fun searchHeader(q: String, n: Int) = if (tr) "Arama: \"$q\" ($n)" else "Search: \"$q\" ($n)"
    val browseHint = if (tr) "İzlemek için OK • Favori için OK basılı tutun" else "Press OK to watch • Hold OK to favorite"
    val search = if (tr) "Ara" else "Search"
    val closeSearch = if (tr) "Aramayı Kapat" else "Close Search"
    val home = if (tr) "Ana Menü" else "Home"
    fun searchPlaceholder(sec: Section) = if (tr) "${sectionTitle(sec)} içinde ara…" else "Type to filter ${sectionTitle(sec)}…"
    fun noMatches(q: String) = if (tr) "\"$q\" için sonuç bulunamadı" else "No matches for \"$q\""
    val noFavorites = if (tr) "Henüz favori yok — sabitlemek için OK tuşuna basılı tutun" else "No favorites yet — hold OK on any item to pin it here"
    val noRecent = if (tr) "Bu bölümde henüz izlenen içerik yok" else "Nothing watched in this section yet"
    val noItems = if (tr) "Bu kategoride içerik yok" else "No items in this category"
    val nowPlayingTag = if (tr) "Şu an" else "Now"
    val upNextTag = if (tr) "Sonra" else "Next"

    // Movie Info Card
    val resumeMovie = if (tr) "▶  Kaldığın Yerden Devam Et" else "▶  Resume Movie"
    val watchMovie = if (tr) "▶  Filmi İzle" else "▶  Watch Movie"
    val addFavorite = if (tr) "☆  Favorilere Ekle" else "☆  Add to Favorites"
    val removeFavorite = if (tr) "★  Favorilerden Çıkar" else "★  Remove Favorite"
    val loadingInfo = if (tr) "Film bilgisi yükleniyor…" else "Loading movie details…"
    val noPlotAvailable = if (tr) "Bu film için özet bilgisi bulunamadı." else "No plot summary available for this title."

    // Parental Lock & PIN
    val parentalLockTitle = if (tr) "Ebeveyn Kilidi (PIN)" else "Parental Lock (PIN)"
    val parentalLockDesc = if (tr) "Kilitli kategorileri 4 haneli şifre ile koruyun" else "Protect locked categories with a 4-digit PIN"
    val setPin = if (tr) "PIN Oluştur" else "Set PIN"
    val changePin = if (tr) "PIN Değiştir" else "Change PIN"
    val removePin = if (tr) "Kilidi Kaldır" else "Remove PIN"
    val manageLockedCats = if (tr) "Kilitli Kategoriler" else "Locked Categories"
    fun lockedCount(n: Int) = if (tr) "$n kategori kilitli" else "$n categories locked"
    val enterPinTitle = if (tr) "Ebeveyn PIN Kodunu Girin" else "Enter Parental PIN"
    val createPinTitle = if (tr) "Yeni 4 Haneli PIN Oluşturun" else "Create New 4-Digit PIN"
    val wrongPin = if (tr) "Hatalı PIN, tekrar deneyin" else "Incorrect PIN, try again"
    val clear = if (tr) "Sil" else "Clear"

    // Episodes
    val loadingEpisodes = if (tr) "Bölümler yükleniyor…" else "Loading episodes…"
    val noEpisodes = if (tr) "Bölüm bulunamadı" else "No episodes found"
    fun seasonLabel(n: Int) = if (tr) "Sezon $n" else "Season $n"
    val playArrow = if (tr) "İzle ▶" else "Play ▶"

    // Player & Next Episode
    val liveBadge = if (tr) "CANLI" else "LIVE"
    val buffering = if (tr) "Yükleniyor..." else "Buffering..."
    val retryNow = if (tr) "Tekrar Dene" else "Retry Now"
    val pause = if (tr) "Duraklat" else "Pause"
    val play = if (tr) "Oynat" else "Play"
    val prevCh = if (tr) "Önceki Kanal" else "Prev Ch"
    val nextCh = if (tr) "Sonraki Kanal" else "Next Ch"
    fun aspectLabel(modeName: String) = if (tr) "Oran: $modeName" else "Aspect: $modeName"
    fun audioLabel(track: String) = if (tr) "Ses: $track" else "Audio: $track"
    fun subtitlesLabel(track: String) = if (tr) "Altyazı: $track" else "Subtitles: $track"
    val defaultTrack = if (tr) "Varsayılan" else "Default"
    val off = if (tr) "Kapalı" else "Off"
    val on = if (tr) "Açık" else "On"
    val selectAudio = if (tr) "Ses Dili Seçin" else "Select Audio Track"
    val selectSubtitles = if (tr) "Altyazı Seçin" else "Select Subtitles"
    val close = if (tr) "Kapat" else "Close"
    val noTracks = if (tr) "Alternatif parça bulunamadı." else "No alternate tracks available."
    val upNext = if (tr) "Sıradaki Bölüm" else "Up Next"
    fun playingInSeconds(sec: Int) = if (tr) "$sec saniye içinde başlıyor…" else "Playing in ${sec}s…"
    val playNow = if (tr) "Şimdi Oynat" else "Play Now"
    val cancel = if (tr) "İptal" else "Cancel"

    // Setup
    val setupSubtitle = if (tr) "Kayıtlı bir liste seçin veya yeni Xtream / M3U kaynağı bağlayın." else "Select a saved playlist or connect a new Xtream / M3U source."
    val loadingPlaylist = if (tr) "Liste yükleniyor… (büyük hesaplar birkaç saniye sürebilir)" else "Loading playlist… (large accounts can take a few seconds)"
    val savedPlaylists = if (tr) "Kayıtlı Listeler" else "Saved Playlists"
    val demoPlaylist = if (tr) "Demo Listesi (Çevrimdışı Test)" else "Demo Playlist (Offline Test)"
    val xtreamLogin = if (tr) "Xtream Codes Girişi" else "Xtream Codes Login"
    val serverLabel = if (tr) "Sunucu (http://adres:port)" else "Server (http://host:port)"
    val usernameLabel = if (tr) "Kullanıcı Adı" else "Username"
    val passwordLabel = if (tr) "Şifre" else "Password"
    val connect = if (tr) "Bağlan" else "Connect"
    val orM3u = if (tr) "veya M3U Bağlantısı" else "or M3U Playlist URL"
    val loadUrl = if (tr) "Bağlantıyı Yükle" else "Load URL"

    // Settings
    val settingsSubtitle = if (tr) "Dil, altyazı, ebeveyn kilidi ve izleme geçmişini yönetin" else "Manage language, subtitles, parental lock, and watch history"
    val back = if (tr) "Geri" else "Back"
    val languageTitle = if (tr) "Uygulama Dili" else "App Language"
    val languageDesc = if (tr) "Menüler ve oynatıcı kontrolleri için dili seçin" else "Choose the display language for menus and player controls"
    val subtitleSizeTitle = if (tr) "Altyazı Boyutu" else "Subtitle Size"
    val subtitleSizeDesc = if (tr) "TV ekranı için altyazı yazı boyutunu ayarlayın" else "Adjust subtitle text size for living room viewing"
    fun subtitleSizeLabel(size: SubtitleSize): String = when (size) {
        SubtitleSize.NORMAL -> if (tr) "Normal" else "Normal"
        SubtitleSize.LARGE -> if (tr) "Büyük" else "Large"
        SubtitleSize.XLARGE -> if (tr) "Çok Büyük" else "Extra Large"
    }
    val autoLoginTitle = if (tr) "Açılışta Otomatik Giriş" else "Auto-Login on Startup"
    val autoLoginDesc = if (tr) "Uygulama açıldığında son kullanılan listeyi otomatik yükle" else "Automatically connect to the last used playlist when opening the app"
    val enabled = if (tr) "Açık" else "Enabled"
    val disabled = if (tr) "Kapalı" else "Disabled"
    val clearHistoryTitle = if (tr) "İzleme İlerlemesini Sıfırla" else "Reset Watch Progress"
    val clearHistoryDesc = if (tr) "Filmler ve dizilerdeki kırmızı izleme çubuklarını ve kalınan süreleri temizle" else "Clear red progress bars and saved resume timestamps on movies and series"
    val clearHistoryButton = if (tr) "Geçmişi Temizle" else "Clear History"
    val clearedNotice = if (tr) "İzleme geçmişi temizlendi" else "Watch progress cleared"

    // App Updates
    val updateTitle = if (tr) "Uygulama Güncellemeleri" else "App Updates"
    fun currentVersionDesc(ver: String) = if (tr) "Mevcut sürüm: v$ver" else "Current version: v$ver"
    val checkUpdatesButton = if (tr) "Güncellemeleri Kontrol Et" else "Check for Updates"
    val checkingUpdates = if (tr) "Kontrol ediliyor…" else "Checking…"
    val upToDateDesc = if (tr) "Uygulama güncel!" else "You are on the latest version!"
    fun updateAvailableDesc(ver: String) = if (tr) "Yeni sürüm mevcut: v$ver" else "New version available: v$ver"
    fun installUpdateButton(ver: String) = if (tr) "İndir ve Yükle (v$ver)" else "Download & Install (v$ver)"
    fun downloadingProgress(pct: Int) = if (tr) "İndiriliyor… %$pct" else "Downloading… $pct%"
    val installReadyButton = if (tr) "Şimdi Yükle" else "Install Now"
    val allowInstallHint = if (tr) "TV ayarlarından 'Bilinmeyen uygulamalara izin ver' seçeneğini açıp tekrar 'Şimdi Yükle'ye basın" else "Enable 'Install unknown apps' in TV settings, then press 'Install Now'"
    fun updateErrorDesc(msg: String) = if (tr) "Güncelleme hatası: $msg" else "Update error: $msg"
}