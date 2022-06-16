package pro.appcraft.lib.auth

import androidx.activity.result.ActivityResult
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class AuthFactory(val fragment: Fragment) {
    private val providers: MutableMap<String, AuthProvider> = mutableMapOf()

    /**
     * Register a provider in the providers map
     * This is an internal method; do not call it directly
     */
    fun <T : AuthProvider> register(provider: Pair<String, () -> T>): AuthProvider {
        if (!providers.containsKey(provider.first)) {
            providers.plusAssign(provider.first to provider.second())
        }
        return providers
            .filterKeys { it == provider.first }
            .values
            .first()
    }

    fun init() = providers
        .values
        .forEach(AuthProvider::init)

    fun destroy() = providers
        .values
        .forEach(AuthProvider::destroy)
        .also { providers.clear() }

    fun authFlow(): Flow<AuthResult> = providers.values.map { it.authFlow }.merge()

    fun onActivityResult(activityResult: ActivityResult): Boolean = providers.values
        .map { it.onActivityResult(activityResult) }
        .fold(false, Boolean::or)

    @Suppress("UNUSED_PARAMETER")
    fun <T : AuthProvider> with(vararg provider: T, callback: T.() -> Unit) {
        provider.forEach(callback)
    }
}
