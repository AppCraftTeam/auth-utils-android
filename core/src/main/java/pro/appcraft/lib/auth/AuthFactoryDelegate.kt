package pro.appcraft.lib.auth

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("unused")
fun Fragment.authFactory(): ReadOnlyProperty<Fragment, AuthFactory> = AuthFactoryDelegate()

private class AuthFactoryDelegate : ReadOnlyProperty<Fragment, AuthFactory> {
    private lateinit var authFactory: AuthFactory
    private var observer: LifecycleObserver? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): AuthFactory {
        if (!::authFactory.isInitialized) {
            authFactory = AuthFactory(thisRef)
        }
        
        if (observer == null) {
            observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> authFactory.init()
                    Lifecycle.Event.ON_DESTROY -> authFactory.destroy()
                    else -> {}
                }
            }.also(thisRef.lifecycle::addObserver)
        }

        return authFactory
    }
}