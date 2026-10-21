package com.apexstudio.app.domain.model

/** Original parametric looks; no third-party LUT, artwork or downloaded preset data. */
object WorkspaceFilterPresetAdditions {
    data class Parameters(
        val gainR: Float, val gainG: Float, val gainB: Float,
        val offsetR: Float, val offsetG: Float, val offsetB: Float,
        val contrast: Float, val saturation: Float, val gamma: Float
    )
    data class Entry(val preset: WorkspaceFilterPreset, val group: String, val parameters: Parameters)

    // Transform order is saturation, contrast, RGB gain/offset, gamma, then strength blend.
    // Each authored recipe has its own complete transform, not just a different label.
    val entries: List<Entry> = listOf(
        look("midnight_teal", "Midnight Teal", "Cinematic", "Low-key cyan-blue balance and muted color", 0xFF315967, Parameters(.88f, 1.02f, 1.10f, -.025f, .008f, .025f, 1.16f, .74f, 1.12f)),
        look("tungsten_noir", "Tungsten Noir", "Cinematic", "Dense warm interior-light color", 0xFF916A49, Parameters(1.10f, 1.00f, .84f, .018f, -.012f, -.020f, 1.22f, .62f, 1.08f)),
        look("bleach_bypass", "Bleach Bypass", "Cinematic", "High contrast with strongly reduced chroma", 0xFF899394, Parameters(1.02f, 1.01f, 1.00f, -.012f, -.010f, -.008f, 1.34f, .28f, 1.03f)),
        look("copper_dusk", "Copper Dusk", "Cinematic", "Copper-red cast and dense dusk tones", 0xFFB47758, Parameters(1.14f, .97f, .88f, .022f, -.006f, .008f, 1.12f, .86f, 1.07f)),
        look("silver_screen", "Silver Screen", "Cinematic", "Cool silver-tinted black-and-white", 0xFFA3AAB4, Parameters(.96f, 1.00f, 1.06f, .012f, .015f, .020f, 1.18f, 0f, .98f)),
        look("desert_scope", "Desert Scope", "Cinematic", "Dry ochre warmth and restrained color", 0xFFC59C68, Parameters(1.10f, 1.03f, .86f, .020f, .012f, -.012f, 1.14f, .69f, .97f)),
        look("velvet_crime", "Velvet Crime", "Cinematic", "Dark plum-biased, muted tones", 0xFF77506D, Parameters(1.05f, .90f, 1.04f, .008f, -.018f, .012f, 1.19f, .64f, 1.12f)),
        look("arctic_thriller", "Arctic Thriller", "Cinematic", "Crisp steel-blue contrast", 0xFF789DB6, Parameters(.88f, 1.00f, 1.14f, -.008f, .012f, .030f, 1.24f, .72f, 1.02f)),
        look("golden_hour_epic", "Golden Hour", "Cinematic", "Luminous warm-gold color", 0xFFD9AD62, Parameters(1.14f, 1.05f, .86f, .024f, .012f, -.014f, 1.08f, 1.08f, .91f)),
        look("neon_alley", "Neon Alley", "Cinematic", "Violet-magenta color separation", 0xFF8B58BA, Parameters(1.09f, .89f, 1.15f, .025f, -.018f, .035f, 1.23f, 1.18f, 1.04f)),

        look("honey_35mm", "Honey Print", "Analog", "Soft honey warmth with lifted blacks", 0xFFC69D60, Parameters(1.08f, 1.03f, .88f, .045f, .032f, .012f, .88f, .86f, .98f)),
        look("expired_slide", "Expired Slide", "Analog", "Faded cyan-green color cast", 0xFF79A69C, Parameters(.90f, 1.05f, 1.02f, .052f, .064f, .045f, .82f, .72f, 1.04f)),
        look("amber_negative", "Amber Negative", "Analog", "Dense amber-biased film-like color", 0xFFB47A41, Parameters(1.13f, 1.02f, .82f, .015f, .008f, -.015f, 1.17f, .91f, 1.06f)),
        look("portra_mist", "Portrait Mist", "Analog", "Low-contrast, restrained portrait warmth", 0xFFD4AC9A, Parameters(1.04f, 1.00f, .96f, .035f, .025f, .023f, .85f, .78f, .94f)),
        look("cross_process", "Cross Process", "Analog", "Punchy green-cyan shifted color", 0xFF7AA171, Parameters(.96f, 1.12f, 1.04f, .012f, .035f, -.022f, 1.27f, 1.20f, 1.04f)),
        look("faded_contact", "Faded Contact", "Analog", "Muted color, matte blacks and soft contrast", 0xFFB4AA92, Parameters(1.00f, .99f, .94f, .072f, .064f, .054f, .76f, .56f, 1.02f)),
        look("sepia_postcard", "Sepia Postcard", "Analog", "Warm brown monochrome print tint", 0xFFB9986E, Parameters(1.13f, .99f, .77f, .038f, .024f, .012f, .92f, 0f, .97f)),
        look("chrome_pop", "Chrome Pop", "Analog", "Strong saturation and compact slide-like contrast", 0xFFDA735B, Parameters(1.02f, 1.00f, .98f, -.006f, -.008f, -.012f, 1.26f, 1.30f, .96f)),
        look("dusty_olive", "Dusty Olive", "Analog", "Muted olive bias with lifted shadows", 0xFFA3A16C, Parameters(1.02f, 1.06f, .84f, .045f, .044f, .024f, .89f, .66f, 1.02f)),
        look("rosewood_print", "Rosewood Print", "Analog", "Muted red-brown print color", 0xFFAD7774, Parameters(1.09f, .94f, .95f, .039f, .018f, .023f, .94f, .67f, 1.04f)),

        look("cotton_candy", "Cotton Candy", "Pastel", "Airy pink-cyan pastel color", 0xFFE1AED4, Parameters(1.06f, .96f, 1.06f, .070f, .052f, .071f, .78f, .68f, .96f)),
        look("lavender_dream", "Lavender Dream", "Pastel", "Soft lavender cast with raised blacks", 0xFFBCAADB, Parameters(1.01f, .93f, 1.10f, .053f, .045f, .075f, .82f, .70f, .98f)),
        look("peach_sorbet", "Peach Sorbet", "Pastel", "Soft peach warmth and low chroma", 0xFFE6B49B, Parameters(1.09f, 1.00f, .91f, .063f, .043f, .031f, .81f, .66f, .95f)),
        look("sea_glass", "Sea Glass", "Pastel", "Pale aqua-green color", 0xFF9DCBC1, Parameters(.91f, 1.07f, 1.05f, .047f, .067f, .061f, .80f, .71f, .97f)),
        look("buttercream", "Buttercream", "Pastel", "Creamy pale-yellow warmth", 0xFFE0D0A2, Parameters(1.06f, 1.05f, .90f, .068f, .060f, .030f, .79f, .60f, .94f)),
        look("lilac_haze", "Lilac Haze", "Pastel", "Low-chroma violet haze", 0xFFC5B0D1, Parameters(1.02f, .94f, 1.08f, .073f, .065f, .084f, .75f, .49f, 1.01f)),
        look("mint_macaron", "Mint Macaron", "Pastel", "Soft mint bias and pastel contrast", 0xFFA8D1B8, Parameters(.94f, 1.08f, .98f, .054f, .074f, .058f, .77f, .64f, .96f)),
        look("powder_blue", "Powder Blue", "Pastel", "Lifted, pale blue balance", 0xFFA5C1DD, Parameters(.92f, 1.00f, 1.11f, .050f, .064f, .083f, .83f, .62f, .97f)),
        look("apricot_film", "Apricot Film", "Pastel", "Gentle apricot warmth with muted color", 0xFFE1B68A, Parameters(1.10f, 1.03f, .90f, .045f, .035f, .022f, .87f, .74f, .98f)),
        look("blush_paper", "Blush Paper", "Pastel", "Warm pink paper-like color wash", 0xFFE0B5C0, Parameters(1.07f, .96f, 1.01f, .080f, .058f, .063f, .74f, .55f, 1.00f)),

        look("concrete_mono", "Concrete Mono", "Urban", "Hard, neutral black-and-white contrast", 0xFF747A7C, Parameters(1.00f, 1.00f, 1.00f, -.012f, -.012f, -.012f, 1.40f, 0f, 1.06f)),
        look("sodium_vapor", "Sodium Vapor", "Urban", "Strong orange-amber night color", 0xFFBD8A3B, Parameters(1.17f, 1.04f, .72f, .026f, .008f, -.028f, 1.22f, .82f, 1.12f)),
        look("rainy_cobalt", "Rainy Cobalt", "Urban", "Dense cool cobalt-blue cast", 0xFF5C7DAB, Parameters(.84f, .97f, 1.17f, -.015f, .004f, .030f, 1.18f, .77f, 1.14f)),
        look("rusted_steel", "Rusted Steel", "Urban", "Desaturated, red-warm industrial tones", 0xFFAB8271, Parameters(1.09f, .98f, .90f, .012f, .000f, -.004f, 1.21f, .48f, 1.05f)),
        look("graphite_matte", "Graphite Matte", "Urban", "Charcoal monochrome with a lifted floor", 0xFF85878D, Parameters(.98f, 1.00f, 1.03f, .060f, .061f, .064f, .86f, 0f, 1.10f)),
        look("city_sunset", "City Sunset", "Urban", "Dense orange-pink urban color", 0xFFC9879A, Parameters(1.13f, .95f, 1.02f, .030f, .002f, .016f, 1.16f, 1.08f, 1.02f)),
        look("subway_green", "Subway Green", "Urban", "Industrial green-cyan cast", 0xFF73A68D, Parameters(.90f, 1.13f, 1.02f, .000f, .028f, .012f, 1.18f, .87f, 1.08f)),
        look("chrome_night", "Chrome Night", "Urban", "Dark cool color with compact contrast", 0xFF667C9B, Parameters(.91f, .98f, 1.11f, -.020f, -.012f, .006f, 1.30f, .63f, 1.16f)),
        look("brick_dust", "Brick Dust", "Urban", "Muted brick-red warmth", 0xFFB97968, Parameters(1.12f, .94f, .87f, .035f, .012f, .004f, 1.03f, .72f, 1.05f)),
        look("electric_indigo", "Electric Indigo", "Urban", "Saturated indigo-violet color", 0xFF8661C1, Parameters(1.03f, .85f, 1.20f, .014f, -.014f, .039f, 1.25f, 1.22f, 1.04f)),

        look("mosswood", "Mosswood", "Nature", "Muted moss greens and earthy warmth", 0xFF859767, Parameters(1.00f, 1.08f, .86f, .015f, .026f, .001f, 1.04f, .79f, 1.05f)),
        look("glacier_lake", "Glacier Lake", "Nature", "Clear cyan-blue water tones", 0xFF6FB3C9, Parameters(.89f, 1.07f, 1.14f, .007f, .026f, .037f, 1.13f, 1.08f, .98f)),
        look("fern_shade", "Fern Shade", "Nature", "Deep shaded green balance", 0xFF548467, Parameters(.90f, 1.10f, .94f, -.016f, .008f, -.008f, 1.17f, .91f, 1.12f)),
        look("canyon_ochre", "Canyon Ochre", "Nature", "Rich red-ochre earth color", 0xFFC79458, Parameters(1.14f, 1.03f, .82f, .022f, .008f, -.016f, 1.19f, 1.04f, 1.03f)),
        look("wildflower", "Wildflower", "Nature", "Vivid floral pink-purple balance", 0xFFB77CB5, Parameters(1.07f, .97f, 1.07f, .014f, .004f, .018f, 1.09f, 1.27f, .96f)),
        look("pine_morning", "Pine Morning", "Nature", "Fresh cool-green color and lifted light", 0xFF80AF98, Parameters(.94f, 1.08f, 1.00f, .022f, .036f, .027f, 1.01f, 1.06f, .94f)),
        look("coral_reef", "Coral Reef", "Nature", "Bright coral-red color with cool blue balance", 0xFFD58F91, Parameters(1.12f, .98f, 1.07f, .022f, .006f, .018f, 1.12f, 1.24f, .97f)),
        look("alpine_fog", "Alpine Fog", "Nature", "Cool, low-chroma lifted haze", 0xFFADBEC4, Parameters(.95f, 1.02f, 1.08f, .066f, .073f, .081f, .78f, .43f, 1.02f)),
        look("autumn_maple", "Autumn Maple", "Nature", "Rich red-gold foliage color", 0xFFCB9552, Parameters(1.16f, 1.03f, .81f, .018f, .004f, -.018f, 1.16f, 1.23f, 1.02f)),
        look("moonlit_meadow", "Moonlit Meadow", "Nature", "Muted blue-green night color", 0xFF5E8D98, Parameters(.86f, 1.02f, 1.12f, -.010f, .008f, .024f, 1.10f, .54f, 1.18f))
    )

    private fun look(id: String, label: String, group: String, description: String, swatch: Long, parameters: Parameters) =
        Entry(WorkspaceFilterPreset("filter_$id", label, description, swatch), group, parameters)

    val presets: List<WorkspaceFilterPreset> = entries.map { it.preset }
    val ids: Set<String> = entries.mapTo(linkedSetOf()) { it.preset.id }
    private val byId: Map<String, Entry> = entries.associateBy { it.preset.id }
    fun find(id: String): Entry? = byId[id]
}
