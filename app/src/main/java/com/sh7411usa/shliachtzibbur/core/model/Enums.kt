package com.sh7411usa.shliachtzibbur.core.model

/**
 * Domain enums. Each carries the lowercase [wire] token used by the Tzibbur API
 * so that mapping to and from JSON stays in one place. Unknown tokens fall back
 * to a defined default rather than throwing, because the server may add values.
 */

enum class Role(val wire: String) {
    ADMIN("admin"),
    MEMBER("member");

    companion object {
        fun fromWire(value: String?): Role =
            entries.firstOrNull { it.wire == value } ?: MEMBER
    }
}

enum class GroupKind(val wire: String) {
    STANDARD("standard"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String?): GroupKind =
            entries.firstOrNull { it.wire == value } ?: STANDARD
    }
}

enum class SenderKind(val wire: String) {
    PERSON("person"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String?): SenderKind =
            entries.firstOrNull { it.wire == value } ?: PERSON
    }
}

enum class WhoCanPost(val wire: String) {
    EVERYONE("everyone"),
    ADMINS("admins");

    companion object {
        fun fromWire(value: String?): WhoCanPost =
            entries.firstOrNull { it.wire == value } ?: EVERYONE
    }
}

enum class WhoCanAddMembers(val wire: String) {
    EVERYONE("everyone"),
    ADMINS("admins");

    companion object {
        fun fromWire(value: String?): WhoCanAddMembers =
            entries.firstOrNull { it.wire == value } ?: ADMINS
    }
}

/** Auth platform. The client always registers as [ANDROID]; others are listed for completeness. */
enum class AuthPlatform(val wire: String) {
    KOSHER("kosher"),
    ANDROID("android"),
    IOS("ios"),
    WEB("web");
}

/** User-selected light/dark preference. [SYSTEM] follows the OS setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
