package pro.appcraft.lib.auth

import androidx.activity.result.ActivityResult
import androidx.fragment.app.Fragment

class AuthFactory(val fragment: Fragment) {
    private val providers: MutableMap<String, AuthProvider> = mutableMapOf()

    /**
     * Register a provider in the providers map
     * This is an internal method; do not call it directly
     */
    fun register(provider: Pair<String, () -> AuthProvider>): AuthProvider {
        if (!providers.containsKey(provider.first)) {
            providers += provider.first to provider.second()
        }
        return providers.getValue(provider.first)
    }

    fun init() = providers
        .values
        .forEach(AuthProvider::init)

    fun destroy() = providers
        .values
        .forEach(AuthProvider::destroy)
        .also { providers.clear() }

    fun onActivityResult(activityResult: ActivityResult): Boolean = providers.values
        .map { it.onActivityResult(activityResult) }
        .fold(false, Boolean::or)

    @Suppress("UNUSED_PARAMETER")
    fun <T : AuthProvider> with(vararg provider: T, callback: T.() -> Unit) {
        provider.forEach(callback)
    }
}
