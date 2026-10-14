package com.Photocator.core

/** A source of map tiles. [attribution] is shown in the app's map picker and in About, never stamped on the geotag. */
data class MapProvider(val id: String, val title: String, val blurb: String, val attribution: String)

/**
 * One selectable basemap. [base] and [labels] name tile layers (see [MapProviders.tileUrl]); [labels] is an optional
 * overlay used only by the map picker. [maxZoom] is the deepest level the provider serves for this style.
 */
data class MapStyle(
    val id: String,
    val title: String,
    val blurb: String,
    val base: String,
    val labels: String?,
    val maxZoom: Int,
    val provider: String = MapProviders.ESRI,
)

object MapProviders {
    const val ESRI = "esri"
    const val OSM = "osm"
    const val OTM = "otm"

    /** Only providers that serve tiles without an account or API key and allow light use by apps. Esri stays first and default. */
    val all: List<MapProvider> = listOf(
        MapProvider(ESRI, "Esri", "Default. Imagery, streets, topo and more", "Map © Esri and its data providers"),
        MapProvider(OSM, "OpenStreetMap", "Community street maps", "Map © OpenStreetMap contributors"),
        MapProvider(OTM, "OpenTopoMap", "Contour-line topographic map", "Map © OpenTopoMap (CC-BY-SA), data © OpenStreetMap contributors"),
    )

    fun byId(id: String): MapProvider = all.firstOrNull { it.id == id } ?: all[0]

    // Tile layers that are not Esri services. Anything not listed here is an Esri ArcGIS service path.
    private val templates = mapOf(
        "osm_standard" to "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        "osm_hot" to "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
        "osm_cycle" to "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png",
        "osm_fr" to "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png",
        "osm_de" to "https://tile.openstreetmap.de/{z}/{x}/{y}.png",
        "otm_topo" to "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
    )

    fun tileUrl(layer: String, z: Int, x: Int, y: Int): String {
        val t = templates[layer]
        return if (t != null) t.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
        else "https://server.arcgisonline.com/ArcGIS/rest/services/$layer/MapServer/tile/$z/$y/$x"
    }
}

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
        MapStyle("osm_std", "Standard", "The classic OpenStreetMap map", "osm_standard", null, 19, MapProviders.OSM),
        MapStyle("osm_hot", "Humanitarian", "Clear roads and buildings, bold colours", "osm_hot", null, 19, MapProviders.OSM),
        MapStyle("osm_cycle", "Cycling (CyclOSM)", "Bike lanes, routes and hills", "osm_cycle", null, 19, MapProviders.OSM),
        MapStyle("osm_fr", "Local names (France)", "Labels in each place's own language", "osm_fr", null, 19, MapProviders.OSM),
        MapStyle("osm_de", "Local names (Germany)", "Detailed street map, local-language labels", "osm_de", null, 19, MapProviders.OSM),
        MapStyle("otm_topo", "Topographic", "Contour lines, trails and peaks", "otm_topo", null, 17, MapProviders.OTM),
    )

    fun byId(id: String): MapStyle = all.firstOrNull { it.id == id } ?: all[0]

    fun forProvider(providerId: String): List<MapStyle> = all.filter { it.provider == providerId }
}
