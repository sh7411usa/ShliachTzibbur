package com.sh7411usa.shliachtzibbur.core.model

/**
 * An in-band coordination message. It is a normal group message whose body is
 * human-readable (so non-Shliach-Tzibbur clients see something sensible) and
 * ends with a machine marker ` #ShliachTzibbur/<verb>`.
 *
 * Shliach Tzibbur recognises the marker and renders a small tag instead of a
 * bubble; other clients just see the sentence, which points them at the app.
 *
 * Service messages are always sent unencrypted — they carry no secret.
 */
enum class ServiceMessage(val verb: String) {
    EncryptionOn("enc-on"),
    EncryptionOff("enc-off"),
    KeyChanged("key-changed");

    companion object {
        private const val GITHUB = "https://github.com/sh7411usa/ShliachTzibbur"
        private val MARKER = Regex("#ShliachTzibbur/([a-z-]+)")

        fun parse(text: String): ServiceMessage? {
            val verb = MARKER.find(text)?.groupValues?.get(1) ?: return null
            return entries.firstOrNull { it.verb == verb }
        }

        /** The full message body to send. Stays well under the 1000-char limit. */
        fun body(kind: ServiceMessage): String {
            val sentence = when (kind) {
                EncryptionOn ->
                    "🔒 Encryption was turned on for this group using Shliach Tzibbur. " +
                        "New messages are encrypted with a shared key that members enter in the app. " +
                        "Get Shliach Tzibbur: $GITHUB"
                EncryptionOff ->
                    "Encryption was turned off for this group. New messages are no longer encrypted. " +
                        "Shliach Tzibbur: $GITHUB"
                KeyChanged ->
                    "🔑 The encryption key for this group was changed. Enter the new key in " +
                        "Shliach Tzibbur to keep reading messages: $GITHUB"
            }
            return "$sentence #ShliachTzibbur/${kind.verb}"
        }
    }
}
