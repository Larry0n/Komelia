package snd.komelia.ui.platform

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import snd.komelia.settings.model.EInkRefreshMode
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Process lifetime circuit breaker: incompatible firmware is logged and disabled once. */
internal object BigmeEInkApi {
    private const val TAG = "KomeliaEInk"
    val isSupported = MutableStateFlow(false)
    private var initialized = false
    private var method: Method? = null
    private var receiver: Any? = null

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val device = "${Build.MANUFACTURER}/${Build.BRAND}/${Build.MODEL}/${Build.DEVICE}"
        try {
            // S6 firmware can identify itself as alps/k65v1_64_bsp. The vendor API,
            // rather than the marketing name, is the decisive capability check.
            val type = Class.forName("xrz.framework.manager.XrzEinkManager")
            val refresh = type.getMethod("forceGlobalRefresh", Int::class.javaPrimitiveType)
            check(refresh.returnType == Void.TYPE) { "Unexpected forceGlobalRefresh return type" }
            receiver = if (Modifier.isStatic(refresh.modifiers)) null else {
                try {
                    type.getConstructor().newInstance()
                } catch (_: NoSuchMethodException) {
                    type.getConstructor(Context::class.java).newInstance(context.applicationContext)
                }
            }
            method = refresh
            isSupported.value = true
            Log.i(TAG, "Bigme API available on $device; static=${Modifier.isStatic(refresh.modifiers)}")
        } catch (_: ClassNotFoundException) {
            Log.d(TAG, "No Bigme E-Ink API on $device")
        } catch (error: ReflectiveOperationException) {
            disable(error)
        } catch (error: LinkageError) {
            disable(error)
        } catch (error: RuntimeException) {
            disable(error)
        }
    }

    fun refresh(mode: EInkRefreshMode) {
        if (!isSupported.value) return
        try {
            method!!.invoke(receiver, mode.argument)
            Log.i(TAG, "Hardware refresh submitted: ${mode.name} (${mode.argument})")
        } catch (error: ReflectiveOperationException) {
            disable(error)
        } catch (error: LinkageError) {
            disable(error)
        } catch (error: RuntimeException) {
            disable(error)
        }
    }

    private fun disable(error: Throwable) {
        isSupported.value = false
        method = null
        receiver = null
        Log.w(TAG, "Bigme E-Ink refresh disabled until app restart", error)
    }
}
