package com.example.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.ToolAjanEnd
import com.example.ui.theme.ToolAjanStart
import com.example.ui.theme.ToolCevirmenEnd
import com.example.ui.theme.ToolCevirmenStart
import com.example.ui.theme.ToolDolandiricilikEnd
import com.example.ui.theme.ToolDolandiricilikStart
import com.example.ui.theme.ToolGoruntuEnd
import com.example.ui.theme.ToolGoruntuStart
import com.example.ui.theme.ToolGorselEnd
import com.example.ui.theme.ToolGorselStart
import com.example.ui.theme.ToolMuzikEnd
import com.example.ui.theme.ToolMuzikStart
import com.example.ui.theme.ToolPersonalarEnd
import com.example.ui.theme.ToolPersonalarStart
import com.example.ui.theme.ToolSesliEnd
import com.example.ui.theme.ToolSesliStart
import com.example.ui.theme.ToolTTSEnd
import com.example.ui.theme.ToolTTSStart
import com.example.ui.theme.ToolTranskripsiyonEnd
import com.example.ui.theme.ToolTranskripsiyonStart
import com.example.ui.theme.ToolVibeCodeEnd
import com.example.ui.theme.ToolVibeCodeStart
import com.example.ui.theme.ToolYazmaEnd
import com.example.ui.theme.ToolYazmaStart

data class ToolCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color,
    val isLocked: Boolean = false,
    val systemPrompt: String,
    val quickPrompts: List<String>
)

object ToolCategoryCatalog {
    val categories: List<ToolCategory> = listOf(
        ToolCategory(
            id = "yazma",
            title = "Yazma",
            subtitle = "Makale, e-posta, metin yazımı ve hikaye oluşturucu",
            icon = Icons.Default.Edit,
            startColor = ToolYazmaStart,
            endColor = ToolYazmaEnd,
            isLocked = false,
            systemPrompt = "Sen profesyonel bir yazarlık ve içerik asistanısın. Akıcı, dilbilgisi hatasız, etkileyici ve amaca uygun makaleler, e-postalar, yaratıcı hikayeler ve raporlar yazarsın.",
            quickPrompts = listOf(
                "Profesyonel bir iş e-postası hazırla",
                "Akademik makale özeti oluştur",
                "Etkileyici bir bilim kurgu hikayesi yaz"
            )
        ),
        ToolCategory(
            id = "cevirmen",
            title = "Çevirmen",
            subtitle = "Anında 100+ dilde akıcı ve bağlamsal çeviri",
            icon = Icons.Default.Language,
            startColor = ToolCevirmenStart,
            endColor = ToolCevirmenEnd,
            isLocked = false,
            systemPrompt = "Sen uzman bir çok dilli çevirmensin. Metinleri sadece kelimesi kelimesine değil, kültürel nüansları, deyimleri ve tonu koruyarak en doğal şekilde çevirirsin.",
            quickPrompts = listOf(
                "İngilizce metni akıcı Türkçe'ye çevir",
                "İş İngilizcesine uygun resmi çeviri yap",
                "Almanca teknik metin çevirisi yap"
            )
        ),
        ToolCategory(
            id = "transkripsiyon",
            title = "Transkripsiyon",
            subtitle = "Ses kayıtlarını düzenle, özetle ve notlara dönüştür",
            icon = Icons.Default.Mic,
            startColor = ToolTranskripsiyonStart,
            endColor = ToolTranskripsiyonEnd,
            isLocked = false,
            systemPrompt = "Sen ses kayıtları ve konuşma metinleri uzmanısın. Ham ses transkriptlerindeki dolgu kelimeleri temizler, anlam bütünlüğünü korur ve maddeli yönetici özetleri çıkarırsın.",
            quickPrompts = listOf(
                "Toplantı ses kaydı metnini düzenle",
                "Konuşma metninden önemli kararları özetle",
                "Ders ses notlarını sınav çalışma kağıdına dök"
            )
        ),
        ToolCategory(
            id = "dolandiricilik",
            title = "Dolandırıcılık",
            subtitle = "Şüpheli mesaj, link ve teklif güvenlik analizi",
            icon = Icons.Default.Security,
            startColor = ToolDolandiricilikStart,
            endColor = ToolDolandiricilikEnd,
            isLocked = false,
            systemPrompt = "Sen bir siber güvenlik ve dolandırıcılık tespiti uzmanısın. Kullanıcının paylaştığı SMS, e-posta, yatırım teklifi veya şüpheli linkleri phishing, sosyal mühendislik ve sahtekarlık risklerine karşı analiz eder, güvenlik tavsiyeleri verirsin.",
            quickPrompts = listOf(
                "Gelen şüpheli banka SMS'ini analiz et",
                "Kripto yatırım teklifinde risk var mı?",
                "Phishing ve sosyal mühendislik kontrolü yap"
            )
        ),
        ToolCategory(
            id = "gorsel",
            title = "Görsel",
            subtitle = "Midjourney, DALL-E ve Stable Diffusion prompt stüdyosu",
            icon = Icons.Default.Palette,
            startColor = ToolGorselStart,
            endColor = ToolGorselEnd,
            isLocked = true,
            systemPrompt = "Sen uzman bir yapay zeka görsel prompt mühendisisin. Kullanıcının fikirlerini Midjourney v6, Flux ve DALL-E 3 için 8K sinematik, ışıklandırma, kamera açısı ve stil parametreleriyle zenginleştirilmiş İngilizce promptlara dönüştürürsün.",
            quickPrompts = listOf(
                "Midjourney için sinematik 8K portre promptu",
                "Minimalist logo ve kurumsal kimlik konsepti",
                "Cyberpunk neon şehir atmosferi promptu"
            )
        ),
        ToolCategory(
            id = "vibecode",
            title = "VibeCode",
            subtitle = "Modern yazılım geliştirme, mimari ve hata ayıklama",
            icon = Icons.Default.Code,
            startColor = ToolVibeCodeStart,
            endColor = ToolVibeCodeEnd,
            isLocked = true,
            systemPrompt = "Sen kıdemli bir Yazılım Mühendisi ve Sistem Mimarısın. Temiz kod (Clean Code), SOLID prensipleri, modern algoritmalar ve en iyi pratiklerle Kotlin, Python, TypeScript ve Rust kodları yazarsın, hataları ayıklarsın.",
            quickPrompts = listOf(
                "Jetpack Compose animasyonlu bileşen yaz",
                "Python FastAPI REST API şablonu oluştur",
                "Algoritma zaman karmaşıklığını optimize et"
            )
        ),
        ToolCategory(
            id = "personalar",
            title = "Personalar",
            subtitle = "Filozof, kıdemli hoca, eleştirmen ve uzman karakterler",
            icon = Icons.Default.Psychology,
            startColor = ToolPersonalarStart,
            endColor = ToolPersonalarEnd,
            isLocked = true,
            systemPrompt = "Sen kullanıcının seçtiği uzman kişiliğe bürünen çok yönlü bir simülasyon uzmanısın. Sokratik bir filozof, sert bir kod incelemecisi veya sabırlı bir özel öğretmen olarak derin diyaloglar yürütürsün.",
            quickPrompts = listOf(
                "Sokratik yöntemle felsefi tartışma başlat",
                "Kıdemli CTO olarak fikirlerimi eleştir",
                "İngilizce konuşma pratiği yaptıran öğretmen ol"
            )
        ),
        ToolCategory(
            id = "goruntu",
            title = "Görüntü",
            subtitle = "Doküman, görsel ve şema mantıksal çözümlemesi",
            icon = Icons.Default.AutoFixHigh,
            startColor = ToolGoruntuStart,
            endColor = ToolGoruntuEnd,
            isLocked = false,
            systemPrompt = "Sen görsel ve doküman analiz asistanısın. Ekran görüntüleri, fatura şablonları, teknik diyagramlar ve formları detaylı şekilde açıklar ve JSON veri formatına dökersin.",
            quickPrompts = listOf(
                "Ekran görüntüsündeki hata kodunu analiz et",
                "Fatura dökümünü tablo haline getir",
                "Teknik mimari şemasını adım adım açıkla"
            )
        ),
        ToolCategory(
            id = "sesli",
            title = "Sesli sohbet",
            subtitle = "Doğal akışlı sesli diyalog ve telaffuz pratiği",
            icon = Icons.Default.GraphicEq,
            startColor = ToolSesliStart,
            endColor = ToolSesliEnd,
            isLocked = true,
            systemPrompt = "Sen sesli konuşma asistanısın. Kısa, dinamik, konuşma diline uygun ve samimi yanıtlar vererek kullanıcıyla akıcı bir sesli sohbet deneyimi sürdürürsün.",
            quickPrompts = listOf(
                "Sesli mülakat simülasyonu yapalım",
                "Günün nasıl geçtiğini konuşalım",
                "Yabancı dil telaffuz pratiği yapalım"
            )
        ),
        ToolCategory(
            id = "ajan",
            title = "Ajan",
            subtitle = "Çok adımlı görev planlama, araştırma ve icraat",
            icon = Icons.Default.SmartToy,
            startColor = ToolAjanStart,
            endColor = ToolAjanEnd,
            isLocked = true,
            systemPrompt = "Sen otonom bir AI ajanısın. Karmaşık hedefleri alt görevlere böler, adım adım araştırma yapar, hipotezleri test eder ve eksiksiz bir eylem raporu sunarsın.",
            quickPrompts = listOf(
                "Pazar araştırması ve rakip analizi raporu çıkar",
                "Yeni mobil uygulama için 4 haftalık sprint planı yap",
                "Teknik mimari ve güvenlik denetim listesi hazırla"
            )
        ),
        ToolCategory(
            id = "metindensese",
            title = "Metinden Sese",
            subtitle = "Seslendirme, diksiyon ve podcast metin tonlaması",
            icon = Icons.Default.VolumeUp,
            startColor = ToolTTSStart,
            endColor = ToolTTSEnd,
            isLocked = false,
            systemPrompt = "Sen profesyonel bir seslendirme yönetmeni ve metin tonlama uzmanısın. Metinleri doğru duraklamalar, vurgulamalar ve fon müzik önerileriyle seslendirmeye hazır hale getirirsin.",
            quickPrompts = listOf(
                "Reklam filmi için seslendirme metni yaz",
                "Podcast giriş metni ve fon tonu hazırla",
                "Doğal nefes duraklamalarıyla konuşma hazırla"
            )
        ),
        ToolCategory(
            id = "muzik",
            title = "Müzik",
            subtitle = "Şarkı sözü, akor dizilimi ve melodi yapılandırma",
            icon = Icons.Default.MusicNote,
            startColor = ToolMuzikStart,
            endColor = ToolMuzikEnd,
            isLocked = true,
            systemPrompt = "Sen ödüllü bir şarkı yazarı ve müzik prodüktörüsün. Pop, Rock, Rap, Trap ve Akustik tarzlarda kafiyeli sözler, akor dizilimleri (BPM, ton) ve şarkı yapıları üretirsin.",
            quickPrompts = listOf(
                "Akustik indie parça için şarkı sözü ve akor yaz",
                "Trap beat için ritmik kafiye dizilimi çıkar",
                "Duygusal bir piyano baladı için nakarat yaz"
            )
        )
    )

    fun findById(id: String): ToolCategory? {
        return categories.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
