package tools.mo3ta.salo.data.media

enum class MediaType { VIDEO, PLAYLIST, CHANNEL }

data class MediaItem(
    val title: String,
    val url: String,
    val type: MediaType,
    val language: String,
)

object MediaStore {
    val ITEMS: List<MediaItem> = listOf(
        MediaItem(
            title = "قناة فضل الصلاة على النبي ﷺ",
            url = "https://www.youtube.com/channel/UCBVYnAMkRMugVXFp5Qfexgw",
            type = MediaType.CHANNEL,
            language = "Arabic",
        ),
        MediaItem(
            title = "مقاطع قصيرة عن فضل الصلاة على النبي ﷺ",
            url = "https://www.youtube.com/playlist?list=PLmoyBs1grLtI1N_83tA7qOdAhGf7JkZYd",
            type = MediaType.PLAYLIST,
            language = "Arabic",
        ),
        MediaItem(
            title = "Durood Shareef (Salawat on Prophet Muhammad PBUH)",
            url = "https://www.youtube.com/playlist?list=PLq5MEfJNT7L4nMFQImpvCkL5lO1jHT7Gb",
            type = MediaType.PLAYLIST,
            language = "English/Urdu",
        ),
        MediaItem(
            title = "فضل الصلاة على النبي ﷺ - للشيخ رجب عسيري",
            url = "https://www.youtube.com/watch?v=04_M_ecDbqU",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "من فضائل الصلاة على النبي الكريم ﷺ - د. محمد خير الشعال",
            url = "https://www.youtube.com/watch?v=mccrGiOEbqk",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "محاضرة شاملة في فضل الصلاة على النبي - د. عبدالله البيومي",
            url = "https://www.youtube.com/watch?v=G6OxFBa8PBU",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "د. عائض القرني - فضل الصلاة على النبي ﷺ",
            url = "https://www.youtube.com/watch?v=p-3vovtOc2w",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "سلسلة رأيت النبي - قصص فضل الصلاة على النبي",
            url = "https://www.youtube.com/watch?v=-yy-RzHOFoY",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "فضل الصلاة على النبي ﷺ يوم الجمعة - د. عمرو خالد",
            url = "https://www.youtube.com/shorts/lllbm8kmXnw",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "فضل الصلاة على النبي ﷺ بعد الدعاء - أ.د. عبدالله الركبان",
            url = "https://www.youtube.com/watch?v=n7dZ5v0Atpc",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "فضل الصلاة على رسول الله ﷺ وراحة القلوب - د. هالة سمير",
            url = "https://www.youtube.com/watch?v=Ir6rIa_6kfY",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "1 Hour Durood Salawat - Omar Hisham (عمر هشام العربي)",
            url = "https://www.youtube.com/watch?v=maHPe1byTfk",
            type = MediaType.VIDEO,
            language = "Arabic",
        ),
        MediaItem(
            title = "Salawat 1000 Times - Powerful Durood Sharif",
            url = "https://www.youtube.com/watch?v=Trvi-fjz-9w",
            type = MediaType.VIDEO,
            language = "Arabic/English",
        ),
        MediaItem(
            title = "Best Darood Sharif 100 Times - Saad Al Qureshi",
            url = "https://www.youtube.com/watch?v=9ojKLO2R7k4",
            type = MediaType.VIDEO,
            language = "Arabic/English",
        ),
        MediaItem(
            title = "Send Salawat on the Prophet ﷺ - Most Beautiful",
            url = "https://www.youtube.com/watch?v=lGoAOD9OinY",
            type = MediaType.VIDEO,
            language = "Arabic/English",
        ),
        MediaItem(
            title = "The Meaning of Sending Salawat upon the Prophet ﷺ - Episode 7",
            url = "https://www.youtube.com/watch?v=eyS1rMtFfkY",
            type = MediaType.VIDEO,
            language = "English",
        ),
    )
}
