# auth-utils-android
Использование провайдеров аутентификации (SMS, Google, Apple и другие) по отдельности и совместно через делегаты.

## Установка
Добавьте в app/build.gradle:
```
// Базовая установка (с классами для имплементации своих провайдеров):
implementation 'com.github.AppCraftTeam.auth-utils-android:core:TAG'
// Авторизация через Google аккаунт:
implementation 'com.github.AppCraftTeam.auth-utils-android:google:TAG'
// Авторизация по SMS (Firebase Auth):
implementation 'com.github.AppCraftTeam.auth-utils-android:phone:TAG'
// Авторизация по SMS (внешняя авторизация, поддерживает SMSAero):
implementation 'com.github.AppCraftTeam.auth-utils-android:phone-ext:TAG'
```

## Использование
Использование делегата для фабрики провайдеров:
```
private val authFactory: AuthFactory by authFactory()
```

<br />
Добавление провайдеров авторизации (когда необходим вызов провайдеров для ленивой инициализации):

```
authFactory.with(authFactory.google, authFactory.phone) {
    init()
}
```
Примечание: при использовании делегата `init()` вызывается автоматически

<br />
Очистка инициализированных провайдеров:

```
authFactory.destroy()
```
Примечание: при использовании делегата `destroy()` вызывается автоматически

<br />
Flow результатов авторизации для всех провайдеров

```
authFactory.authFlow()
```

<br />
Обработка `onActivityResult` для провадеров, где он используется:

```
authFactory.onActivityResult(activityResult: ActivityResult)
```

<br />
Методы провайдеров (реализация специфична для каждого провайдера):

```
fun login(vararg params: Pair<String, Any>)

fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>?) {}
```

## Примеры
### Инициализация SMS авторизации (Fragment)
```
private val authFactory: AuthFactory by authFactory()

private fun initAuth() {
    viewModel.setAuthProvider(authFactory.phone)
    viewModel.setAltAuthProvider(authFactory.phoneExternal)
}
```

### Использование авторизации через интерфейс (ViewModel)
```
private lateinit var authProvider: AuthProvider
private lateinit var altAuthProvider: AuthProvider

val authLiveData: LiveData<AuthResult>
    get() = merge(
        authProvider.authFlow,
        altAuthProvider.authFlow
    ).asLiveData(context)
    
fun setAuthProvider(authProvider: AuthProvider) {
    this.authProvider = authProvider.apply {
        init(
            PhoneAuthProvider.RECEIVE_SMS_CALLBACK to object :
                PhoneAuthProvider.OnSendSmsListener {
                override fun onSmsSent() {
                    phoneAuth.smsSent()
                    codeAuth.startOtpTimer()
                }

                override fun onSmsReceived(code: String) {
                }
            }
        )
    }
}

fun setAltAuthProvider(authProvider: AuthProvider) {
    this.altAuthProvider = authProvider.apply {
        init(
            ExternalPhoneAuthProvider.RECEIVE_SMS_CALLBACK to ExternalPhoneAuthProvider.OnSendSmsCallback {
                phoneAuth.smsSent()
            },
            ExternalPhoneAuthProvider.AUTHENTICATOR_CALLBACK to sendSmsAeroMessageUseCase::invoke,
            ExternalPhoneAuthProvider.CONFIRMATOR_CALLBACK to getSmsAeroAuthorizationTokenUseCase::invoke
        )
    }
}

fun login() {
    viewModelScope.launch {
        if (useAltProvider) {
            altAuthProvider.login(ExternalPhoneAuthProvider.PHONE to phone)
        } else {
            val phoneWithPlus = phone?.let { "+$it" }
            authProvider.login(PhoneAuthProvider.PHONE to phoneWithPlus)
        }
    }
}
```
