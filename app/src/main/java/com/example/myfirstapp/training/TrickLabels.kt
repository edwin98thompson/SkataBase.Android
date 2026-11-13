package com.example.myfirstapp.training

/**
 * Stable key is persisted with annotations and used as the classified folder name.
 * Add the project’s trick labels here.
 */
data class TrickLabel(
    val key: String,
    val title: String
)

object TrickLabelCatalog {
    val labels: List<TrickLabel> = listOf(
        TrickLabel(key = "ollie", title = "Ollie"),
        TrickLabel(key = "kickflip", title = "Kickflip"),
        TrickLabel(key = "heelflip", title = "Heelflip"),
        TrickLabel(key = "tre_flip", title = "Tre Flip")
    )
}
