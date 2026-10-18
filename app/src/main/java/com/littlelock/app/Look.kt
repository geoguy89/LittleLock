package com.littlelock.app

/**
 * How the child's home screen looks: the size of the app tiles and the wallpaper colours the parent can pick from.
 * Plain data so it can be tested without a phone.
 */
object Look {

    /** One tile size: the icon, the width of a grid cell, and the label text, all chosen by the parent. */
    data class Tile(val name: String, val iconDp: Int, val cellDp: Int, val textSp: Int)

    val TILES = listOf(
        Tile("Small", 64, 96, 12),
        Tile("Medium", 88, 124, 14),
        Tile("Large", 112, 152, 16),
        Tile("Extra large", 144, 190, 18),
    )

    /** Large by default - the first version's small tiles were hard to see and tap. */
    const val DEFAULT_TILE = 2

    fun tile(index: Int): Tile = TILES[index.coerceIn(0, TILES.lastIndex)]

    /** Wallpaper colours: a name for the parent, an ARGB colour for the screen. */
    val COLORS = listOf(
        "Lavender" to 0xFFD9D0FF.toInt(),
        "Sky" to 0xFFBFE3FF.toInt(),
        "Mint" to 0xFFC8F2DD.toInt(),
        "Sunshine" to 0xFFFFE9A8.toInt(),
        "Peach" to 0xFFFFD3C2.toInt(),
        "Bubblegum" to 0xFFFFC9E3.toInt(),
        "Night sky" to 0xFF23224A.toInt(),
        "Forest" to 0xFF1E5B4A.toInt(),
    )

    /** The cartoon animals (key, name) - the pictures come from tools/gen_wallpapers.py; [Wallpapers] maps a key to its drawable. */
    val ANIMALS = listOf("cat" to "Cat", "dog" to "Puppy", "bunny" to "Bunny", "bear" to "Bear", "panda" to "Panda",
        "fox" to "Fox", "penguin" to "Penguin", "owl" to "Owl", "elephant" to "Elephant", "frog" to "Frog", "monkey" to "Monkey")

    /** Photos built into the app (key, name): public-domain (CC0) pictures, credited in assets/PHOTO_CREDITS.txt. */
    val PHOTOS = listOf(
        "kitten" to "Kitten",
        "labpup" to "Chocolate puppy",
        "puppy" to "Puppy",
        "ducklings" to "Ducklings",
        "chicks" to "Fluffy ducklings",
        "rabbit" to "Rabbit",
        "panda" to "Panda",
        "pandas" to "Baby pandas",
        "elephant" to "Baby elephant",
        "giraffe" to "Giraffe",
        "penguin" to "Penguin",
        "lioncub" to "Lion cub",
        "goat" to "Baby goat",
        "lamb" to "Lamb",
        "hedgehog" to "Hedgehog",
        "fawn" to "Fawn",
        "butterflies" to "Butterflies",
        "rainbow" to "Rainbow",
        "monkeylook" to "Monkey gazing up",
        "monkeyface" to "Macaque",
        "monkeytrio" to "Three monkeys",
        "babymonkey" to "Baby monkey",
        "monkeyhug" to "Monkey hug",
        "babyorang" to "Baby orangutan",
        "orangutans" to "Orangutan family",
        "capuchin" to "Capuchin monkey",
        "monkeybrow" to "Monkey")

    private const val PHOTO_PREFIX = "photo:"
    private const val ANIMAL_PREFIX = "animal:"
    private const val COLOR_PREFIX = "color:"
    const val IMAGE = "image"

    /** The stored wallpaper value for a colour. */
    fun colorValue(argb: Int): String = COLOR_PREFIX + Integer.toHexString(argb)

    fun photoValue(key: String): String = PHOTO_PREFIX + key

    /** The photo key inside a stored wallpaper value, or null when it isn't a known photo. */
    fun photoOf(value: String?): String? {
        if (value == null || !value.startsWith(PHOTO_PREFIX)) return null
        return value.removePrefix(PHOTO_PREFIX).takeIf { k -> PHOTOS.any { it.first == k } }
    }

    /** The stored wallpaper value for an animal. */
    fun animalValue(key: String): String = ANIMAL_PREFIX + key

    /** The animal key inside a stored wallpaper value, or null when it isn't a known animal. */
    fun animalOf(value: String?): String? {
        if (value == null || !value.startsWith(ANIMAL_PREFIX)) return null
        return value.removePrefix(ANIMAL_PREFIX).takeIf { k -> ANIMALS.any { it.first == k } }
    }

    /** The colour inside a stored wallpaper value, or null when it isn't a colour. */
    fun colorOf(value: String?): Int? {
        if (value == null || !value.startsWith(COLOR_PREFIX)) return null
        return value.removePrefix(COLOR_PREFIX).toLongOrNull(16)?.toInt()
    }

    /** Is a wallpaper colour dark? Then the app names are drawn in white; on light colours, in dark text. */
    fun isDark(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) < 140
    }

    /** "Hi, Ava!" when the parent entered a name, otherwise the plain title. */
    fun greeting(name: String?, fallback: String): String {
        val n = name?.trim().orEmpty()
        return if (n.isEmpty()) fallback else "Hi, $n!"
    }
}
