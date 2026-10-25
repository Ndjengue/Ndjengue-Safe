package com.familia.controloparental.comum

/** Entrada do catálogo pré-definido de redes sociais / plataformas (secção 4). */
data class RedeSocial(
    val id: String,
    val nome: String,
    val dominios: List<String>,
    val pacotes: List<String>
)

object CatalogoRedes {
    val todas: List<RedeSocial> = listOf(
        RedeSocial(
            "instagram", "Instagram",
            listOf("instagram.com", "cdninstagram.com", "instagr.am"),
            listOf("com.instagram.android", "com.instagram.lite", "com.instagram.barcelona")
        ),
        RedeSocial(
            "tiktok", "TikTok",
            listOf("tiktok.com", "tiktokcdn.com", "tiktokv.com", "musical.ly", "byteoversea.com", "ibytedtos.com", "tiktokcdn-us.com"),
            listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        ),
        RedeSocial(
            "youtube", "YouTube",
            listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "youtube-nocookie.com", "youtubei.googleapis.com"),
            listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.google.android.apps.youtube.music")
        ),
        RedeSocial(
            "facebook", "Facebook",
            listOf("facebook.com", "fbcdn.net", "fb.com", "fbsbx.com", "facebook.net"),
            listOf("com.facebook.katana", "com.facebook.lite")
        ),
        RedeSocial(
            "messenger", "Messenger",
            listOf("messenger.com"),
            listOf("com.facebook.orca", "com.facebook.mlite")
        ),
        RedeSocial(
            "whatsapp", "WhatsApp",
            listOf("whatsapp.com", "whatsapp.net", "wa.me"),
            listOf("com.whatsapp", "com.whatsapp.w4b")
        ),
        RedeSocial(
            "snapchat", "Snapchat",
            listOf("snapchat.com", "sc-cdn.net", "snap-dev.net", "snapkit.com"),
            listOf("com.snapchat.android")
        ),
        RedeSocial(
            "x", "X (Twitter)",
            listOf("twitter.com", "x.com", "twimg.com", "t.co"),
            listOf("com.twitter.android", "com.twitter.android.lite")
        ),
        RedeSocial(
            "telegram", "Telegram",
            listOf("telegram.org", "t.me", "telegra.ph", "telegram.me"),
            listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
        ),
        RedeSocial(
            "discord", "Discord",
            listOf("discord.com", "discordapp.com", "discord.gg", "discordapp.net", "discord.media"),
            listOf("com.discord")
        ),
        RedeSocial(
            "twitch", "Twitch",
            listOf("twitch.tv", "ttvnw.net", "jtvnw.net", "twitchcdn.net"),
            listOf("tv.twitch.android.app")
        ),
        RedeSocial(
            "reddit", "Reddit",
            listOf("reddit.com", "redd.it", "redditstatic.com", "redditmedia.com"),
            listOf("com.reddit.frontpage")
        ),
        RedeSocial(
            "pinterest", "Pinterest",
            listOf("pinterest.com", "pinimg.com"),
            listOf("com.pinterest")
        ),
        RedeSocial(
            "netflix", "Netflix",
            listOf("netflix.com", "nflxvideo.net", "nflximg.net", "nflxso.net", "nflxext.com"),
            listOf("com.netflix.mediaclient")
        ),
        RedeSocial(
            "roblox", "Roblox",
            listOf("roblox.com", "rbxcdn.com", "robloxlabs.com"),
            listOf("com.roblox.client")
        )
    )

    fun porId(id: String): RedeSocial? = todas.firstOrNull { it.id == id }

    /** Domínios de DNS-over-HTTPS/TLS conhecidos: bloqueá-los obriga os browsers a usarem o DNS do sistema (filtrado). */
    val dominiosDoh: List<String> = listOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
        "one.one.one.one", "dns.quad9.net", "doh.opendns.com", "dns.nextdns.io",
        "dns.adguard.com", "doh.cleanbrowsing.org", "use-application-dns.net",
        "chromium.dns.nextdns.io", "doh.dns.sb", "dns.sb"
    )
}
