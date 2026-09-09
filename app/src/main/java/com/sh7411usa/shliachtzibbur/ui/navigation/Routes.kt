package com.sh7411usa.shliachtzibbur.ui.navigation

/** All navigation destinations. Plain string routes keep the graph readable. */
object Routes {
    const val AUTH_GRAPH = "auth"
    const val AUTH_LANDING = "auth/landing"
    const val AUTH_PHONE = "auth/phone"
    const val AUTH_CODE = "auth/code"

    const val MAIN_GRAPH = "main"
    const val GROUPS = "groups"
    const val SETTINGS_HOME = "settings"

    const val MESSAGES = "group/{groupId}/messages"
    const val GROUP_SETTINGS = "group/{groupId}/settings"
    const val MEMBERS = "group/{groupId}/members"

    const val CREATE_GROUP = "groups/new"
    const val USER_SETTINGS = "settings/account"
    const val APP_SETTINGS = "settings/app"
    const val DEVICES = "settings/devices"
    const val LEGAL = "settings/legal/{kind}"

    fun messages(groupId: String) = "group/$groupId/messages"
    fun groupSettings(groupId: String) = "group/$groupId/settings"
    fun members(groupId: String) = "group/$groupId/members"
    fun legal(kind: String) = "settings/legal/$kind"
}
