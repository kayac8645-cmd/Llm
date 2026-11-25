package app.freedombrowser.shared

import kotlinx.serialization.Serializable

/** Capabilities a site can request. Not every engine supports every permission. */
enum class SitePermission(val id: String) {
    CAMERA("camera"),
    MICROPHONE("microphone"),
    LOCATION("location"),
    NOTIFICATIONS("notifications"),
    POPUPS("popups"),
    ;

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

enum class PermissionDecision(val id: String) {
    ASK("ask"), ALLOW("allow"), BLOCK("block");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ASK
    }
}

/** Per-site settings keyed by [WebUrl.siteKey]. */
@Serializable
data class SiteSettings(
    val site: String,
    val permissions: Map<String, String> = emptyMap(),
    val adBlockException: Boolean = false,
) {
    fun decision(permission: SitePermission): PermissionDecision = PermissionDecision.fromId(permissions[permission.id])

    fun with(permission: SitePermission, decision: PermissionDecision): SiteSettings =
        copy(permissions = if (decision == PermissionDecision.ASK) permissions - permission.id else permissions + (permission.id to decision.id))

    val isDefault: Boolean get() = permissions.isEmpty() && !adBlockException
}
