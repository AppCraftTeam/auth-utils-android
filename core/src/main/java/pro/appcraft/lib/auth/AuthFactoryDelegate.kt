package pro.appcraft.lib.auth

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("unused")
fun Fragment.authFactory(): ReadOnlyProperty<Fragment, AuthFactory> = AuthFactoryDelegate()

private class AuthFactoryDelegate : ReadOnlyProperty<Fragment, AuthFactory> {
    private lateinit var authFactory: AuthFactory

    override fun getValue(thisRef: Fragment, property: KProperty<*>): AuthFactory {
        authFactory = AuthFactory(thisRef)
        thisRef.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_CREATE -> authFactory.init()
                    Lifecycle.Event.ON_DESTROY -> authFactory.destroy()
                    else -> {}
                }
            }
        })

        return authFactory
    }
}