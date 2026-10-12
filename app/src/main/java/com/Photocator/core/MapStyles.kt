package com.Photocator.core

/** One selectable Esri basemap. [labels] is an optional overlay used only by the map picker. */
data class MapStyle(
    val id: String,
    val title: String,
    val blurb: String,
    val base: String,
    val labels: String?,
    val maxZoom: Int,
)

object MapStyles {
    val all: List<MapStyle> = listOf(
        MapStyle("sat", "Satellite", "Aerial imagery with place labels in the picker", "World_Imagery", "Reference/World_Boundaries_and_Places", 19),
        MapStyle("sat_plain", "Satellite, no labels", "Clean aerial imagery", "World_Imagery", null, 19),
        MapStyle("streets", "Streets", "Roads and buildings", "World_Street_Map", null, 19),
        MapStyle("topo", "Topographic", "Contours and trails", "World_Topo_Map", null, 19),
        MapStyle("natgeo", "National Geographic", "Warm cartographic style", "NatGeo_World_Map", null, 16),
        MapStyle("light", "Light Gray", "Quiet pale canvas", "Canvas/World_Light_Gray_Base", "Canvas/World_Light_Gray_Reference", 16),
        MapStyle("dark", "Dark Gray", "Quiet dark canvas", "Canvas/World_Dark_Gray_Base", "Canvas/World_Dark_Gray_Reference", 16),
        MapStyle("terrain", "Terrain", "Land cover and elevation tints", "World_Terrain_Base", "Reference/World_Reference_Overlay", 13),
        MapStyle("relief", "Shaded Relief", "Hill shading", "World_Shaded_Relief", null, 13),
        MapStyle("physical", "Physical", "Natural world overview", "World_Physical_Map", null, 8),
        MapStyle("ocean", "Ocean", "Sea floor and coasts", "Ocean/World_Ocean_Base", "Ocean/World_Ocean_Reference", 13),
    )

    fun byId(id: String): MapStyle = all.firstOrNull { it.id == id } ?: all[0]
}
