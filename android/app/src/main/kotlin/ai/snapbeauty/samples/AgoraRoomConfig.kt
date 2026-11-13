package ai.snapbeauty.samples

/** Token remains session-only; deliberately no data-class toString. */
class AgoraRoomConfig(app: String, channel: String, user: String, token: String) {
    val appId = app.trim()
    val channel = channel.trim()
    val token = token.trim()
    val uid: Int
    init {
        require(appId.matches(Regex("[0-9a-fA-F]{32}"))) { "App ID 需要填写 32 位十六进制字符" }
        require(this.channel.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "频道名请使用 1–64 位字母、数字、下划线或短横线" }
        uid = requireNotNull(user.trim().toIntOrNull()?.takeIf { it > 0 }) { "UID 请输入 1–2147483647 之间的整数" }
        require(this.token.isNotEmpty()) { "请填写当前频道和 UID 对应的 Token" }
    }
}
