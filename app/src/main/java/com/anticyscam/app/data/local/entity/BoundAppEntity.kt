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
 */
@Entity(tableName = "bound_apps")
data class BoundAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "bound_at")
    val boundAt: Long = System.currentTimeMillis()
)
