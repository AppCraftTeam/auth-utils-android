package pro.appcraft.lib.auth

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import pro.appcraft.either.AsyncCatching
import pro.appcraft.either.getOrHandle

private const val PROVIDER_PHONE_EXT = "ExternalPhoneProvider"
private typealias Authenticator = suspend (String) -> AsyncCatching<String>
private typealias Confirmator = suspend (Pair<String, String>) -> AsyncCatching<String>

val AuthFactory.phoneExternal
    get() = register(PROVIDER_PHONE_EXT to { ExternalPhoneAuthProvider(fragment) })

class ExternalPhoneAuthProvider(private val fragment: Fragment) : AuthProvider {
    override val requestCode: Int = 372 // Spells "EPA"

    override val authFlow = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)

    private var authenticator: Authenticator? = null
    private var confirmator: Confirmator? = null
    private var smsSentCallback: OnSendSmsCallback? = null

    private lateinit var phone: String
    private lateinit var code: String
    private var authKey: String? = null

    override fun init(params: Map<String, Any>) {
        super.init(params)
        // todo: do something with the unchecked casts
        (params[AUTHENTICATOR_CALLBACK] as Authenticator?)?.let(::setExternalAuthentication)
        (params[CONFIRMATOR_CALLBACK] as Confirmator?)?.let(::setExternalConfirmation)
        smsSentCallback = params[RECEIVE_SMS_CALLBACK] as OnSendSmsCallback?
    }

    override fun login(params: Map<String, Any>) {
        when {
            params.containsKey(PHONE) -> {
                phone = params.getValue(PHONE) as String
                fragment.lifecycleScope.launch {
                    sendCode(phone)
                }
            }
            params.containsKey(SMS_CODE) -> {
                code = params.getValue(SMS_CODE) as String
                fragment.lifecycleScope.launch {
                    confirmCode(code)
                }
            }
        }
    }

    fun setExternalAuthentication(method: Authenticator) {
        authenticator = method
    }

    fun setExternalConfirmation(method: Confirmator) {
        confirmator = method
    }

    private suspend fun sendCode(phone: String) {
        requireNotNull(authenticator)
        authenticator?.invoke(phone)?.fold(
            {
                authKey = it
                smsSentCallback?.onSmsSent()
            },
            { authFlow.emit(AuthResult.Error(it.message.orEmpty(), it)) }
        )
    }

    private suspend fun confirmCode(code: String) {
        requireNotNull(confirmator)
        confirmator?.invoke(authKey.orEmpty() to code)
            ?.map { AuthResult.Success(token = it, "") }
            ?.getOrHandle { AuthResult.Error(it.message.orEmpty(), it) }
            ?.also { authFlow.emit(it) }
    }

    fun interface OnSendSmsCallback {
        fun onSmsSent()
    }

    companion object {
        const val PHONE = "phone"
        const val SMS_CODE = "sms_code"
        const val AUTHENTICATOR_CALLBACK = "authenticator_callback"
        const val CONFIRMATOR_CALLBACK = "confirmator_callback"
        const val RECEIVE_SMS_CALLBACK = "receive_sms_callback"
    }
}