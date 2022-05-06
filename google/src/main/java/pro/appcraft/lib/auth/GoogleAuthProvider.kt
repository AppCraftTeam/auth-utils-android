package pro.appcraft.lib.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pro.appcraft.lib.auth.google.R

private const val PROVIDER_GOOGLE = "GoogleAuthProvider"

val AuthFactory.google
    get() = register(PROVIDER_GOOGLE to { GoogleAuthProvider(fragment) })

class GoogleAuthProvider(private val fragment: Fragment) : AuthProvider {
    override val requestCode: Int = 474 // Spells "GSI"

    private val auth
        get() = Firebase.auth

    override val authFlow = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)

    private lateinit var signInOptions: GoogleSignInOptions

    private var activityLauncher: ActivityResultLauncher<Intent>? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> authFlow.tryEmit(AuthResult.Cancellation)
            is AuthResult.Error -> authFlow.tryEmit(throwable)
            else -> authFlow.tryEmit(
                AuthResult.Error(
                    throwable.message.toString(),
                    throwable
                )
            )
        }
    }

    override fun init() {
        super.init()
        signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(fragment.getString(R.string.auth_google_client_id))
            .requestEmail()
            .build()
    }

    override fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>?) {
        activityLauncher = launcher
    }

    override fun login(params: Map<String, Any>) {
        requireNotNull(activityLauncher)

        GoogleSignIn.getLastSignedInAccount(fragment.requireActivity())
            ?.takeUnless { it.isExpired }
            ?.idToken
            ?.let { token ->
                fragment.lifecycleScope
                    .launch(exceptionHandler) { proceedWithFirebase(token) }
            }
            ?: run {
                val client = GoogleSignIn.getClient(fragment.requireActivity(), signInOptions)
                activityLauncher?.launch(client.signInIntent)
            }
    }

    private suspend fun proceedWithFirebase(idToken: String) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val userId = authResult.user?.uid
        val token = authResult.user?.getIdToken(true)?.await()
        token?.token?.let {
            authFlow.emit(AuthResult.Success(token = it, userId = userId!!))
        } ?: run {
            authFlow.emit(AuthResult.Error("Got empty token"))
        }
    }

    override fun onActivityResult(activityResult: ActivityResult): Boolean {
        return if (requestCode == this.requestCode) {
            if (activityResult.resultCode == Activity.RESULT_OK) {
                fragment.lifecycleScope.launch(exceptionHandler) {
                    val currentUser =
                        GoogleSignIn.getSignedInAccountFromIntent(activityResult.data).await()
                    proceedWithFirebase(idToken = currentUser.idToken!!)
                }
            } else authFlow.tryEmit(AuthResult.Error("Failed onActivityResult"))
            true
        } else false
    }
}
