package pro.appcraft.lib.auth

sealed interface AuthResult {
    data class Success(
        val token: String,
        val userId: String,
        val username: String? = null
    ) : AuthResult

    class Error(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause), AuthResult

    object Cancellation : AuthResult
}