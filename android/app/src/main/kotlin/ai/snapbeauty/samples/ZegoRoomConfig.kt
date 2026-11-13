package ai.snapbeauty.samples

/** Token authentication only; the client contains no AppSign or server secret. */
class ZegoRoomConfig(app: String, room: String, user: String, token: String) {
    val appId = requireNotNull(app.trim().toLongOrNull()?.takeIf { it in 1..0xffffffffL }) { "AppID 请输入 1–4294967295 之间的整数" }
    val room = room.trim()
    val userId = user.trim()
    val token = token.trim()
    val streamId: String
    init {
        require(this.room.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "房间名请使用 1–64 位字母、数字、下划线或短横线" }
        require(userId.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "用户 ID 请使用 1–32 位字母、数字、下划线或短横线" }
        require(this.token.isNotEmpty()) { "请填写当前用户对应的有效 Token" }
        streamId = "beauty_${this.room}_$userId"
    }
}
