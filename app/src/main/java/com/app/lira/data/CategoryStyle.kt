package com.app.lira.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.app.lira.ui.theme.CategoryPalette

/** One selectable category icon. [key] is what's persisted. */
data class CategoryIcon(val key: String, val vector: ImageVector)

/** Resolved look of a category: colour + icon. Always shown with its label too. */
data class CategoryLook(val color: Color, val icon: ImageVector)

/**
 * The `category` table only has id/name, so colour and icon are a local, on-device choice.
 * Unset categories get a sensible default from their name ("Coffee" → coffee cup, brown).
 * Observable: composables reading [look] recompose when a style changes.
 */
object CategoryStyle {
    private const val PREFS = "lira_category_style"
    private var prefs: SharedPreferences? = null
    private val overrides = mutableStateMapOf<Int, Pair<Int, String>>() // id → (paletteIndex, iconKey)

    val icons: List<CategoryIcon> = listOf(
        CategoryIcon("cart", Icons.Outlined.ShoppingCart),
        CategoryIcon("food", Icons.Outlined.Restaurant),
        CategoryIcon("coffee", Icons.Outlined.LocalCafe),
        CategoryIcon("transport", Icons.Outlined.DirectionsBus),
        CategoryIcon("car", Icons.Outlined.DirectionsCar),
        CategoryIcon("bag", Icons.Outlined.ShoppingBag),
        CategoryIcon("home", Icons.Outlined.Home),
        CategoryIcon("bolt", Icons.Outlined.Bolt),
        CategoryIcon("health", Icons.Outlined.FavoriteBorder),
        CategoryIcon("fun", Icons.Outlined.Celebration),
        CategoryIcon("stay", Icons.Outlined.Hotel),
        CategoryIcon("flight", Icons.Outlined.Flight),
        CategoryIcon("gift", Icons.Outlined.CardGiftcard),
        CategoryIcon("pet", Icons.Outlined.Pets),
        CategoryIcon("sport", Icons.Outlined.FitnessCenter),
        CategoryIcon("book", Icons.Outlined.MenuBook),
        CategoryIcon("phone", Icons.Outlined.PhoneAndroid),
        CategoryIcon("subscription", Icons.Outlined.Subscriptions),
        CategoryIcon("salary", Icons.Outlined.Payments),
        CategoryIcon("other", Icons.Outlined.Category)
    )

    private val iconByKey = icons.associateBy { it.key }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) ->
            val id = k.toIntOrNull() ?: return@forEach
            val parts = (v as? String)?.split('|') ?: return@forEach
            if (parts.size == 2) overrides[id] = (parts[0].toIntOrNull() ?: 9) to parts[1]
        }
    }

    fun set(categoryId: Int, paletteIndex: Int, iconKey: String) {
        overrides[categoryId] = paletteIndex to iconKey
        prefs?.edit()?.putString(categoryId.toString(), "$paletteIndex|$iconKey")?.apply()
    }

    fun remove(categoryId: Int) {
        overrides.remove(categoryId)
        prefs?.edit()?.remove(categoryId.toString())?.apply()
    }

    /** (paletteIndex, iconKey) currently in effect for a category. */
    fun selection(categoryId: Int?, name: String?): Pair<Int, String> =
        categoryId?.let { overrides[it] } ?: defaultFor(name)

    fun look(categoryId: Int?, name: String?): CategoryLook {
        val (idx, key) = selection(categoryId, name)
        return CategoryLook(
            color = CategoryPalette.all.getOrElse(idx) { CategoryPalette.Other },
            icon = (iconByKey[key] ?: iconByKey.getValue("other")).vector
        )
    }

    private fun defaultFor(name: String?): Pair<Int, String> {
        val n = name.orEmpty().lowercase()
        fun has(vararg words: String) = words.any { n.contains(it) }
        return when {
            has("grocer", "supermarket", "courses", "food shop", "market") -> 0 to "cart"
            has("coffee", "café", "cafe") -> 2 to "coffee"
            has("eat", "restaurant", "food", "lunch", "dinner", "resto", "bar") -> 1 to "food"
            has("transport", "bus", "train", "metro", "taxi", "uber", "travel") -> 3 to "transport"
            has("car", "fuel", "gas", "petrol", "essence") -> 3 to "car"
            has("shop", "cloth", "amazon") -> 4 to "bag"
            has("rent", "home", "bill", "house", "loyer") -> 5 to "home"
            has("electric", "utilit", "internet", "water") -> 5 to "bolt"
            has("health", "pharma", "doctor", "medic", "sant") -> 6 to "health"
            has("fun", "entertain", "movie", "cinema", "game", "party", "sortie") -> 7 to "fun"
            has("hotel", "stay", "airbnb") -> 8 to "stay"
            has("flight", "plane") -> 8 to "flight"
            has("gift", "cadeau") -> 6 to "gift"
            has("pet", "dog", "cat") -> 0 to "pet"
            has("sport", "gym", "fitness") -> 3 to "sport"
            has("sub", "netflix", "spotify", "abonnement") -> 4 to "subscription"
            has("phone", "mobile") -> 3 to "phone"
            has("salary", "salaire", "income", "pay") -> 9 to "salary"
            else -> 9 to "other"
        }
    }
}
