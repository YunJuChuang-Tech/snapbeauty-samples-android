package ai.snapbeauty.samples

/** UserSig is session-only and excluded from diagnostics. */
class TrtcRoomConfig(app: String, room: String, user: String, sig: String) {
    val appId = positiveInt(app, "SDKAppID")
    val roomId = positiveInt(room, "房间号")
    val userId = user.trim()
    val userSig = sig.trim()
    init {
        require(userId.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "用户 ID 请使用 1–32 位字母、数字、下划线或短横线" }
        require(userSig.isNotEmpty()) { "请填写与用户 ID 对应的 UserSig" }
    }
    private fun positiveInt(input: String, label: String) = requireNotNull(input.trim().toIntOrNull()?.takeIf { it > 0 }) { "$label 请输入 1–2147483647 之间的整数" }
}
