package com.anticyscam.app.domain.model

data class BoundApp(
    val packageName: String,
    val label: String,
    val boundAt: Long = 0L
) {
    companion object {
        // Plan v4 Item 6: once an app is bound, the user cannot unbind it
        // for 24h. This stops a coerced user from being walked through
        // "unbind your bank app, then transfer" in real time.
        const val UNBIND_LOCK_MS: Long = 24L * 60 * 60 * 1000
    }
}
