package com.sadikmen.kinogerto

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class KinogerProvider : MainAPI() {
    override var mainUrl = "https://kinoger.to"
    override var name = "Kinoger"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "de" // Almanca içerik olduğu için
    override val hasMainPage = true

    // 1. Ana Sayfa İçeriklerini Çekme
    override suspend fun getMainPage(page: Int, request: HomePageRequest): HomePageResponse? {
        // Sitenin ana sayfa HTML kodunu indiriyoruz
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        
        val homeItems = mutableListOf<HomePageList>()
        
        // Örnek: HTML içindeki film kutularını (örneğin .movie-box sınıfını) buluyoruz
        val items = document.select(".movie-box").mapNotNull {
            it.toSearchResult()
        }
        
        if (items.isNotEmpty()) {
            homeItems.add(HomePageList("Aktuelle Filme & Serien", items))
        }
        
        return newHomePageResponse(homeItems, false)
    }

    // 2. Arama Fonksiyonu
    override suspend fun search(query: String): List<SearchResponse> {
        // Kinoger arama URL yapısına göre isteği gönderiyoruz
        val searchUrl = "$mainUrl/?do=search&subaction=search&story=$query"
        val html = app.get(searchUrl).text
        val document = Jsoup.parse(html)

        return document.select(".movie-box").mapNotNull {
            it.toSearchResult()
        }
    }

    // HTML elementini Cloudstream arama sonucuna dönüştüren yardımcı fonksiyon
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select(".movie-title").text() ?: return null
        val href = this.select("a").attr("href") ?: return null
        val posterUrl = this.select("img").attr("src")

        return if (href.contains("/serie/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    // 3. Detay Sayfası (Oyuncular, Özet, Bölümler)
    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val title = document.select(".full-title").text()
        val description = document.select(".story-text").text()
        val poster = document.select(".poster img").attr("src")

        return if (url.contains("/serie/")) {
            // Dizi ise bölümleri listeleme mantığı
            val episodes = document.select(".episode-link").map {
                Episode(data = it.attr("href"), name = it.text())
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            // Film ise direkt yükleme
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // 4. Video Linklerini Çekme (Stream Extractors)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val document = Jsoup.parse(html)
        
        // Sayfa içindeki iframe veya video oynatıcı (Voe, Doodstream, Streamtape vb.) linklerini buluyoruz
        document.select("iframe").forEach { iframe ->
            val videoUrl = iframe.attr("src")
            // Cloudstream'in hazır çözücülerini (Extractor) çağırıyoruz
            loadExtractor(videoUrl, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
