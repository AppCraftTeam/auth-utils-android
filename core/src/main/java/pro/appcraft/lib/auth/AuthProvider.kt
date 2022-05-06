package pro.appcraft.lib.auth

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.flow.SharedFlow

interface AuthProvider {
    val requestCode: Int

    val authFlow: SharedFlow<AuthResult>

    fun init() {}

    fun login(params: Map<String, Any> = emptyMap())

    fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>?) {}

    fun onActivityResult(activityResult: ActivityResult): Boolean = false

    fun destroy() {}
}
