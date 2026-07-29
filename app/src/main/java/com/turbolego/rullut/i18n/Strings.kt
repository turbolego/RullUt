package com.turbolego.rullut.i18n

/**
 * Supported app languages.
 * Add new entries here to support more languages.
 */
enum class Lang(val code: String, val displayName: String) {
    NB("nb", "Norsk Bokmål"),
    EN("en", "English"),
}

/**
 * App-wide string table. Add new strings to ALL language blocks.
 * To add a language: add a new Lang enum entry + a block in each lazy value below.
 */
object Strings {

    /** Current active language — set on app start from DataStore. */
    var lang: Lang = Lang.NB

    // ──────────────────────────────────────────────
    // App-level
    // ──────────────────────────────────────────────
    val appName get() = when (lang) {
        Lang.NB -> "RullUt"
        Lang.EN -> "RullUt"
    }

    val appTagline get() = when (lang) {
        Lang.NB -> "Tilgjengelig ruteplanlegger for Norge"
        Lang.EN -> "Wheelchair-accessible route planner for Norway"
    }

    // ──────────────────────────────────────────────
    // Map screen
    // ──────────────────────────────────────────────
    val mapContentDescription get() = when (lang) {
        Lang.NB -> "Tilgjengelighetskart over Norge. Viser rullestol-ruter og universell utforming."
        Lang.EN -> "Accessibility map of Norway. Showing wheelchair routes and universal design features."
    }

    val mapCurrentLocation get() = when (lang) {
        Lang.NB -> "Din posisjon"
        Lang.EN -> "Your location"
    }

    // ──────────────────────────────────────────────
    // FAB buttons
    // ──────────────────────────────────────────────
    val fabLocation get() = when (lang) {
        Lang.NB -> "Gå til min posisjon"
        Lang.EN -> "Go to my location"
    }

    val fabRoute get() = when (lang) {
        Lang.NB -> "Planlegg rute"
        Lang.EN -> "Plan route"
    }

    val fabSettings get() = when (lang) {
        Lang.NB -> "Innstillinger"
        Lang.EN -> "Settings"
    }

    val fabSearch get() = when (lang) {
        Lang.NB -> "Søk etter sted"
        Lang.EN -> "Search place"
    }

    // ──────────────────────────────────────────────
    // Feature Popup
    // ──────────────────────────────────────────────
    val featureInfoTitle get() = when (lang) {
        Lang.NB -> "Kartdata"
        Lang.EN -> "Map data"
    }

    val featureNoInfo get() = when (lang) {
        Lang.NB -> "Ingen kartdata på dette punktet."
        Lang.EN -> "No map data at this location."
    }

    val featureLoading get() = when (lang) {
        Lang.NB -> "Henter kartdata..."
        Lang.EN -> "Loading map data..."
    }

    val featureError get() = when (lang) {
        Lang.NB -> "Kunne ikke hente kartdata"
        Lang.EN -> "Could not fetch map data"
    }

    fun featureId(id: String) = when (lang) {
        Lang.NB -> "Objekt-ID: $id"
        Lang.EN -> "Feature ID: $id"
    }

    fun featureProperty(key: String, value: String) = when (lang) {
        Lang.NB -> "$key: $value"
        Lang.EN -> "$key: $value"
    }

    // ──────────────────────────────────────────────
    // Settings panel
    // ──────────────────────────────────────────────
    val settingsTitle get() = when (lang) {
        Lang.NB -> "Innstillinger"
        Lang.EN -> "Settings"
    }

    val settingsMapTitleDesc get() = when (lang) {
        Lang.NB -> "Innstillinger for kartet"
        Lang.EN -> "Map settings"
    }

    val settingsBasemap get() = when (lang) {
        Lang.NB -> "Bakgrunnskart"
        Lang.EN -> "Basemap"
    }

    val settingsLayers get() = when (lang) {
        Lang.NB -> "Kartlag"
        Lang.EN -> "Layers"
    }

    val settingsNoLayers get() = when (lang) {
        Lang.NB -> "Ingen kartlag funnet."
        Lang.EN -> "No layers found."
    }

    val settingsClose get() = when (lang) {
        Lang.NB -> "Lukk innstillinger"
        Lang.EN -> "Close settings"
    }

    val settingsCloseLabel get() = when (lang) {
        Lang.NB -> "Lukk"
        Lang.EN -> "Close"
    }

    val settingsLanguage get() = when (lang) {
        Lang.NB -> "Språk"
        Lang.EN -> "Language"
    }

    fun settingsToggleLayer(title: String) = when (lang) {
        Lang.NB -> "Slå på/av lag: $title"
        Lang.EN -> "Toggle layer: $title"
    }

    // Basemap options
    val basemapLiberty get() = when (lang) {
        Lang.NB -> "OpenStreetMap (Liberty)"
        Lang.EN -> "OpenStreetMap (Liberty)"
    }

    val basemapTopo get() = when (lang) {
        Lang.NB -> "Topografisk"
        Lang.EN -> "Topographic"
    }

    val basemapNone get() = when (lang) {
        Lang.NB -> "Ingen bakgrunn"
        Lang.EN -> "No basemap"
    }

    // ──────────────────────────────────────────────
    // Route planner
    // ──────────────────────────────────────────────
    val routeTitle get() = when (lang) {
        Lang.NB -> "Planlegg rute"
        Lang.EN -> "Plan route"
    }

    val routeFrom get() = when (lang) {
        Lang.NB -> "Fra"
        Lang.EN -> "From"
    }

    val routeTo get() = when (lang) {
        Lang.NB -> "Til"
        Lang.EN -> "To"
    }

    val routeCalculate get() = when (lang) {
        Lang.NB -> "Beregn rute"
        Lang.EN -> "Calculate route"
    }

    val routeCalculating get() = when (lang) {
        Lang.NB -> "Beregner rute..."
        Lang.EN -> "Calculating route..."
    }

    val routeNoRoute get() = when (lang) {
        Lang.NB -> "Fant ingen rute mellom disse stedene."
        Lang.EN -> "No route found between these locations."
    }

    val routeFromLocation get() = when (lang) {
        Lang.NB -> "Fra (min posisjon)"
        Lang.EN -> "From (my location)"
    }

    val routeDistance get() = when (lang) {
        Lang.NB -> "Avstand"
        Lang.EN -> "Distance"
    }

    val routeDuration get() = when (lang) {
        Lang.NB -> "Tid"
        Lang.EN -> "Duration"
    }

    val routeAccessibility get() = when (lang) {
        Lang.NB -> "Tilgjengelighet"
        Lang.EN -> "Accessibility"
    }

    val routeSource get() = when (lang) {
        Lang.NB -> "Rutekilde"
        Lang.EN -> "Route source"
    }

    fun routeAccessible(pct: Int) = when (lang) {
        Lang.NB -> "Tilgjengelig: $pct%"
        Lang.EN -> "Accessible: $pct%"
    }

    fun routePartially(pct: Int) = when (lang) {
        Lang.NB -> "Delvis: $pct%"
        Lang.EN -> "Partially: $pct%"
    }

    fun routeNotAccessible(pct: Int) = when (lang) {
        Lang.NB -> "Ikke tilgjengelig: $pct%"
        Lang.EN -> "Not accessible: $pct%"
    }

    fun routeUnknown(pct: Int) = when (lang) {
        Lang.NB -> "Ukjent: $pct%"
        Lang.EN -> "Unknown: $pct%"
    }

    // ──────────────────────────────────────────────
    // Search
    // ──────────────────────────────────────────────
    val searchTitle get() = when (lang) {
        Lang.NB -> "Søk etter sted"
        Lang.EN -> "Search place"
    }

    val searchPlaceholder get() = when (lang) {
        Lang.NB -> "Søk etter sted i Norge..."
        Lang.EN -> "Search for a place in Norway..."
    }

    val searchNoResults get() = when (lang) {
        Lang.NB -> "Ingen resultater."
        Lang.EN -> "No results."
    }

    val searchError get() = when (lang) {
        Lang.NB -> "Søk feilet. Prøv igjen."
        Lang.EN -> "Search failed. Try again."
    }

    val searchClose get() = when (lang) {
        Lang.NB -> "Lukk søk"
        Lang.EN -> "Close search"
    }

    // ──────────────────────────────────────────────
    // Toilets
    // ──────────────────────────────────────────────
    val toiletTitle get() = when (lang) {
        Lang.NB -> "Toaletter i nærheten"
        Lang.EN -> "Nearby toilets"
    }

    val toiletNoResults get() = when (lang) {
        Lang.NB -> "Ingen toaletter funnet i nærheten."
        Lang.EN -> "No toilets found nearby."
    }

    val toiletsLoading get() = when (lang) {
        Lang.NB -> "Søker etter toaletter..."
        Lang.EN -> "Searching for toilets..."
    }

    val toiletsNone get() = when (lang) {
        Lang.NB -> "Ingen toaletter funnet i nærheten."
        Lang.EN -> "No toilets found nearby."
    }

    val toiletsError get() = when (lang) {
        Lang.NB -> "Kunne ikke søke etter toaletter."
        Lang.EN -> "Could not search for toilets."
    }

    fun toiletDistance(dist: Int) = when (lang) {
        Lang.NB -> "${dist}m unna"
        Lang.EN -> "${dist}m away"
    }

    // ──────────────────────────────────────────────
    // Accessibility assessment
    // ──────────────────────────────────────────────
    val assessmentTitle get() = when (lang) {
        Lang.NB -> "Tilgjengelighetsvurdering"
        Lang.EN -> "Accessibility assessment"
    }

    val assessmentAccessible get() = when (lang) {
        Lang.NB -> "Tilgjengelig"
        Lang.EN -> "Accessible"
    }

    val assessmentPartially get() = when (lang) {
        Lang.NB -> "Delvis tilgjengelig"
        Lang.EN -> "Partially accessible"
    }

    val assessmentNotAccessible get() = when (lang) {
        Lang.NB -> "Ikke tilgjengelig"
        Lang.EN -> "Not accessible"
    }

    val assessmentUnknown get() = when (lang) {
        Lang.NB -> "Ukjent"
        Lang.EN -> "Unknown"
    }

    // ──────────────────────────────────────────────
    // Permissions
    // ──────────────────────────────────────────────
    val permissionLocationTitle get() = when (lang) {
        Lang.NB -> "Posisjonstilgang"
        Lang.EN -> "Location access"
    }

    val permissionLocationBody get() = when (lang) {
        Lang.NB -> "RullUt trenger tilgang til din posisjon for å vise deg på kartet og planlegge ruter."
        Lang.EN -> "RullUt needs your location to show you on the map and plan routes."
    }

    val permissionGrant get() = when (lang) {
        Lang.NB -> "Gi tilgang"
        Lang.EN -> "Grant access"
    }

    val permissionDeny get() = when (lang) {
        Lang.NB -> "Avslå"
        Lang.EN -> "Deny"
    }

    // ──────────────────────────────────────────────
    // Accessibility highlights from WMS tiles
    // ──────────────────────────────────────────────
    val highscoreTitle get() = when (lang) {
        Lang.NB -> "Tilgjengelighet i området"
        Lang.EN -> "Accessibility in the area"
    }

    val highscoreLoading get() = when (lang) {
        Lang.NB -> "Henter tilgjengelighetsdata..."
        Lang.EN -> "Loading accessibility data..."
    }

    val highscoreError get() = when (lang) {
        Lang.NB -> "Kunne ikke hente tilgjengelighetsdata."
        Lang.EN -> "Could not load accessibility data."
    }

    // ──────────────────────────────────────────────
    // Errors
    // ──────────────────────────────────────────────
    val errorGeneral get() = when (lang) {
        Lang.NB -> "Noe gikk galt. Prøv igjen."
        Lang.EN -> "Something went wrong. Try again."
    }

    val errorNetwork get() = when (lang) {
        Lang.NB -> "Ingen nettverkstilkobling."
        Lang.EN -> "No network connection."
    }

    val errorTimeout get() = when (lang) {
        Lang.NB -> "Forespørselen tok for lang tid."
        Lang.EN -> "The request timed out."
    }
}
