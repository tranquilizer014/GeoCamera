package com.geocamera.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteLocation(val name: String, val lat: Double, val lng: Double)

class FavoritesManager(context: Context) {

    private val prefs = context.getSharedPreferences("geocamera_favorites", Context.MODE_PRIVATE)

    fun getFavorites(): List<FavoriteLocation> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<FavoriteLocation>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(FavoriteLocation(obj.getString("name"), obj.getDouble("lat"), obj.getDouble("lng")))
        }
        return list
    }

    fun addFavorite(fav: FavoriteLocation) {
        val list = getFavorites().toMutableList()
        list.add(fav)
        save(list)
    }

    fun removeFavorite(fav: FavoriteLocation) {
        val list = getFavorites().filterNot { it.name == fav.name && it.lat == fav.lat && it.lng == fav.lng }
        save(list)
    }

    private fun save(list: List<FavoriteLocation>) {
        val arr = JSONArray()
        for (f in list) {
            val obj = JSONObject()
            obj.put("name", f.name)
            obj.put("lat", f.lat)
            obj.put("lng", f.lng)
            arr.put(obj)
        }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }
}
