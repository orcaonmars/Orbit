package com.hydrogen.ai

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import com.hydrogen.ai.data.AndroidKeystoreCredentialStore
import com.hydrogen.ai.data.CredentialBinding
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig

/**
 * Runs only on the separate, disposable OrbitSprint_API35 emulator.
 * This is a framework instrumentation (not a production receiver or debug backdoor).
 * Never run on a phone/profile containing real saved credentials.
 */
class CredentialStoreInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val report = Bundle()
        var checks = 0
        val context = targetContext
        val prefs = context.getSharedPreferences("hydrogen_encrypted_credentials", Context.MODE_PRIVATE)
        val openai = RelayConfig(
            route = ProviderRoute.GEMINI_HYDROGEN_RELAY,
            baseUrl = "https://api.openai.com",
            modelId = "fake-model"
        )
        val router = openai.copy(baseUrl = "https://openrouter.ai/api", modelId = "another-fake-model")
        val third = openai.copy(baseUrl = "https://api.groq.com/openai")
        val google = openai.copy(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, baseUrl = "")
        val local = openai.copy(baseUrl = "http://127.0.0.1:1234")
        val tokenA = "synthetic-A-not-an-actual-key"
        val tokenB = "synthetic-B-not-an-actual-key"
        val store = AndroidKeystoreCredentialStore(context)
        try {
            // Scoped storage and Keystore restoration with a new object instance.
            store.setToken(openai, tokenA)
            store.setToken(router, tokenB)
            check(store.getToken(openai) == tokenA); checks++
            check(store.getToken(router) == tokenB); checks++
            check(store.getToken(third) == null); checks++
            val fresh = AndroidKeystoreCredentialStore(context)
            check(fresh.getToken(openai.copy(modelId = "model-switch")) == tokenA); checks++
            check(fresh.getToken(router) == tokenB); checks++
            val aKey = CredentialBinding.preferenceKey(CredentialBinding.forConfig(openai)!!)
            val bKey = CredentialBinding.preferenceKey(CredentialBinding.forConfig(router)!!)
            val encryptedA = prefs.getString(aKey, null)
            val encryptedB = prefs.getString(bKey, null)
            check(!encryptedA.isNullOrBlank() && !encryptedB.isNullOrBlank()); checks++
            check(!encryptedA.contains(tokenA) && !encryptedB.contains(tokenB)); checks++

            // Copying an encrypted record across providers fails GCM AAD authentication.
            check(prefs.edit().putString(bKey, encryptedA).commit()); checks++
            check(fresh.getToken(router) == null); checks++
            check(prefs.edit().putString(bKey, encryptedB).commit()); checks++
            check(fresh.getToken(router) == tokenB); checks++

            // Ambiguous legacy route keys cannot be picked up by an unrelated provider.
            store.setToken(ProviderRoute.GEMINI_HYDROGEN_RELAY, "synthetic-legacy-ambiguous")
            check(fresh.getToken(third) == null); checks++
            store.setToken(ProviderRoute.GEMINI_HYDROGEN_RELAY, null)

            // Google's legacy slot can migrate because its origin is fixed in RelayConfig.
            store.setToken(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT, "synthetic-google")
            check(fresh.getToken(google) == "synthetic-google"); checks++
            check(fresh.getToken(google) == "synthetic-google"); checks++
            check(store.getToken(ProviderRoute.GOOGLE_AI_STUDIO_DIRECT) == null); checks++
            store.setToken(google, null)

            // Delete only one service, leave another untouched.
            store.setToken(openai, null)
            check(fresh.getToken(openai) == null); checks++
            check(fresh.getToken(router) == tokenB); checks++
            store.setToken(router, null)
            check(fresh.getToken(router) == null); checks++
            check(fresh.getToken(local) == null); checks++
            report.putString("stream", "ORBIT_CREDENTIAL_INSTRUMENTATION_PASS: $checks checks; no real keys.")
            finish(Activity.RESULT_OK, report)
        } catch (t: Throwable) {
            // Never print token values or ciphertext, including on failure.
            report.putString("stream", "ORBIT_CREDENTIAL_INSTRUMENTATION_FAIL after $checks: " +
                t.javaClass.simpleName)
            finish(Activity.RESULT_CANCELED, report)
        }
    }
}
