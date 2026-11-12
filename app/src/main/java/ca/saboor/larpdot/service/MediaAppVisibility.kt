package ca.saboor.larpdot.service

/** Match the actual session owner, rather than any app categorized as a music player. */
fun isActiveMediaAppOpen(foregroundPackage: String?, playerPackage: String?): Boolean =
    !playerPackage.isNullOrBlank() && foregroundPackage == playerPackage

fun shouldHideMediaApp(enabled: Boolean, foregroundPackage: String?, playerPackage: String?): Boolean =
    enabled && isActiveMediaAppOpen(foregroundPackage, playerPackage)
