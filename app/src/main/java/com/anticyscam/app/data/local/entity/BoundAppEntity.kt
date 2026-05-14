package com.anticyscam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app the user has chosen to "bind" to the anti-fraud guard. When a bound
 * app comes to the foreground without being launched via [package launch from
 * within AntiCyScam], the accessibility service triggers the warning flow.
 *
 * We cache the display label and load the icon on demand via PackageManager
 * (icons are not persisted — they can change with theme/update).
 *
 * Cooldown columns (v4) encode the bind-maturation + cooldown-unbind state
 * machine:
 *
 *   UNBOUND
 *     → INSERT (boundAt = wall, boundAtElapsedNanos = monotonic)
 *
 *   PENDING_MATURATION (0–24h)
 *     accumulatedBoundMillis < 24h ; unbindRequestedAtWall is NULL
 *
 *   MATURED (>= 24h accumulated)
 *     accumulatedBoundMillis >= 24h ; unbindRequestedAtWall is NULL
 *
 *   PENDING_UNBIND (0–48h after request)
 *     unbindRequestedAtWall is NOT NULL ; accumulatedUnbindMillis < 48h
 *
 *   UNBOUND again
 *     row deleted when accumulatedUnbindMillis >= 48h
 *
 * The double-anchor (wall + monotonic) lets [BindingSettleEngine] clamp
 * progress to min(wallDelta, elapsedDelta) so that fast-forwarding the system
 * clock cannot shorten the cooldown — see the engine for the full rule.
 */
@Entity(tableName = "bound_apps")
data class BoundAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "bound_at")
    val boundAt: Long = System.currentTimeMillis(),

    /** Monotonic anchor taken at bind time; pairs with [boundAt]. */
    @ColumnInfo(name = "bound_at_elapsed_nanos")
    val boundAtElapsedNanos: Long = 0L,

    /** Total time accumulated toward the 24h maturation gate. */
    @ColumnInfo(name = "accumulated_bound_millis")
    val accumulatedBoundMillis: Long = 0L,

    /** Last wall-time the settle engine wrote to this row. */
    @ColumnInfo(name = "last_settled_wall")
    val lastSettledWall: Long = 0L,

    /** Last monotonic-time the settle engine wrote to this row. */
    @ColumnInfo(name = "last_settled_elapsed_nanos")
    val lastSettledElapsedNanos: Long = 0L,

    /** NULL = no unbind requested; non-NULL = cooldown is running. */
    @ColumnInfo(name = "unbind_requested_at_wall")
    val unbindRequestedAtWall: Long? = null,

    /** Monotonic anchor at unbind-request time. */
    @ColumnInfo(name = "unbind_requested_at_elapsed_nanos")
    val unbindRequestedAtElapsedNanos: Long? = null,

    /** Total time accumulated toward the 48h auto-unbind threshold. */
    @ColumnInfo(name = "accumulated_unbind_millis")
    val accumulatedUnbindMillis: Long = 0L
)
