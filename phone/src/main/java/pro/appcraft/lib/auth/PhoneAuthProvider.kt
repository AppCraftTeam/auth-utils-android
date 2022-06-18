package pro.appcraft.lib.auth

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pro.appcraft.lib.auth.phone.R
import java.util.concurrent.TimeUnit

private const val PROVIDER_PHONE = "PhoneAuthProvider"
private const val REQUEST_TIMEOUT = 30L
private const val ERROR_INVALID_PHONE_NUMBER = "ERROR_INVALID_PHONE_NUMBER"

val AuthFactory.phone
    get() = register(PROVIDER_PHONE to { PhoneAuthProvider(fragment) })

class PhoneAuthProvider(private val fragment: Fragment) : AuthProvider {
    override val requestCode: Int = 767 // Spells "SMS"

    override val authFlow = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)

    private var verificationId: String? = null
    private var smsListener: OnSendSmsListener? = null
    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? =
        null
    private var activityLauncher: ActivityResultLauncher<Intent>? = null

    private val auth
        get() = Firebase.auth

    private var phoneNumber: String? = null
    private var username: String? = null

    private lateinit var signInOptions: PhoneAuthOptions

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> authFlow.tryEmit(AuthResult.Cancellation)
            is AuthResult.Error -> authFlow.tryEmit(throwable)
            else -> {
                val errorMessage = when {
                    (throwable is FirebaseAuthInvalidCredentialsException) && (throwable.errorCode == ERROR_INVALID_PHONE_NUMBER) ->
                        fragment.getString(R.string.auth_error_invalid_phone_number)
                    throwable is FirebaseTooManyRequestsException ->
                        fragment.getString(R.string.auth_error_too_many_requests)
                    throwable is FirebaseNetworkException ->
                        fragment.getString(R.string.auth_error_network)
                    throwable.message?.contains("[ 7: ]") == true ->
                        fragment.getString(R.string.auth_error_network)
                    else -> fragment.getString(R.string.auth_error_failure_authorization)
                }
                authFlow.tryEmit(AuthResult.Error(errorMessage, throwable))
            }
        }
    }

    private val credentialAuthHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> authFlow.tryEmit(AuthResult.Cancellation)
            is AuthResult.Error -> authFlow.tryEmit(throwable)
            else -> {
                val message = when {
                    throwable is FirebaseNetworkException -> fragment.getString(R.string.auth_error_network)
                    throwable.message?.contains("[ 7: ]") == true -> fragment.getString(R.string.auth_error_network)
                    else -> fragment.getString(R.string.auth_error_wrong_sms_code)
                }
                authFlow.tryEmit(AuthResult.Error(message, throwable))
            }
        }
    }

    override fun init(params: Map<String, Any>) {
        smsListener = params[RECEIVE_SMS_CALLBACK] as OnSendSmsListener?
    }

    override fun setActivityLauncher(launcher:ActivityResultLauncher<Intent>?) {
        activityLauncher = launcher
    }

    override fun login(params: Map<String, Any>) {
        when {
            params.containsKey(PHONE) -> {
                val phone = params[PHONE] as String?
                val forceResending = phone == phoneNumber
                phoneNumber = phone
                username = params[USERNAME] as String?

                if (!forceResending) {
                    logout()
                    forceResendingToken = null
                }

                fragment.lifecycleScope.launch(exceptionHandler) {
                    loginWithPhone()
                }
            }
            params.containsKey(SMS_CODE) -> {
                val smsCode = params[SMS_CODE] as String
                val verificationId = verificationId ?: return
                val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
                fragment.lifecycleScope.launch(credentialAuthHandler) {
                    proceedWithCredential(credential)
                }
            }
        }
    }

    override fun onActivityResult(activityResult: ActivityResult): Boolean {
        val smsCode = activityResult.data?.getStringExtra(SMS_CODE) ?: return false
        val verificationId = verificationId ?: return false
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
        fragment.lifecycleScope.launch(credentialAuthHandler) {
            proceedWithCredential(credential)
        }
        return true
    }

    override fun destroy() {
        super.destroy()
        phoneNumber = null
        username = null
        verificationId = null
        smsListener = null
        forceResendingToken = null
        // logout()
    }

    private suspend fun loginWithPhone() {
        val credential = callbackFlow {
            val callback = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(phoneAuthCredential: PhoneAuthCredential) {
                    trySend(phoneAuthCredential)
                    close()
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    close(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    forceResendingToken: PhoneAuthProvider.ForceResendingToken
                ) {
                    super.onCodeSent(verificationId, forceResendingToken)
                    smsListener?.let {
                        this@PhoneAuthProvider.verificationId = verificationId
                        this@PhoneAuthProvider.forceResendingToken = forceResendingToken
                        it.onSmsSent()
                    }
                    trySend(null)
                }
            }

            signInOptions = PhoneAuthOptions.newBuilder(auth).apply {
                setPhoneNumber(phoneNumber!!)
                setTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                setActivity(fragment.requireActivity())
                setCallbacks(callback)
                forceResendingToken?.let { setForceResendingToken(it) }
            }.build()

            PhoneAuthProvider.verifyPhoneNumber(signInOptions)

            awaitClose()
        }.first()

        credential?.let { proceedWithCredential(it) }
    }

    private suspend fun proceedWithCredential(credential: PhoneAuthCredential) {
        credential.smsCode?.let {
            smsListener?.onSmsReceived(it)
        }

        val authResult = auth.signInWithCredential(credential).await()
        val user = authResult?.user
        requireNotNull(user)
        val firebaseUid = user.uid
        val firebaseUsername = user.displayName
        val justCreated = user.metadata?.creationTimestamp == user.metadata?.lastSignInTimestamp
        if (!username.isNullOrBlank() && !justCreated)
            throw AuthResult.Error(message = fragment.getString(R.string.auth_error_user_exists))

        runCatching { user.linkWithCredential(credential).await() }

        val token = user.getIdToken(true).await()
        token?.token
            ?.also {
                val result = AuthResult.Success(
                    it,
                    firebaseUid,
                    firebaseUsername ?: username
                )
                authFlow.emit(result)
            }
            ?: throw AuthResult.Error(
                message = fragment.getString(R.string.auth_error_failure_authorization)
            )
    }

    private fun logout() {
        auth.signOut()
    }

    interface OnSendSmsListener {
        fun onSmsSent()
        fun onSmsReceived(code: String)
    }

    companion object {
        const val PHONE = "phone"
        const val SMS_CODE = "sms_code"
        const val USERNAME = "username"
        const val RECEIVE_SMS_CALLBACK = "receive_sms_callback"
    }
}
