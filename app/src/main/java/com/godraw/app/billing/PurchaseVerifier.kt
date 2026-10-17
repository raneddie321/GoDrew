package com.godraw.app.billing

import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class PurchaseToVerify(val packageName: String, val productId: String, val purchaseToken: String)

sealed interface VerificationResult {
    /** The server confirmed an active subscription granting [tier] until [expiryTimeMillis]. */
    data class Verified(val tier: Tier, val expiryTimeMillis: Long) : VerificationResult

    /** The server rejected the purchase (forged, refunded, expired...). */
    data object Invalid : VerificationResult

    /** Verification could not run (not configured, offline, server error). */
    data class Unavailable(val reason: String) : VerificationResult
}

/**
 * Secure purchase verification design: the app never trusts a purchase on its own. It sends the
 * purchase token to the developer's server, which calls the Google Play Developer API
 * (purchases.subscriptionsv2.get) with service-account credentials and answers with the
 * entitlement. See README "Purchase verification server" for the request/response contract.
 */
interface PurchaseVerifier {
    val isConfigured: Boolean
    suspend fun verify(purchase: PurchaseToVerify): VerificationResult
}

class ServerPurchaseVerifier(private val endpoint: String) : PurchaseVerifier {

    @Serializable
    private data class Request(val packageName: String, val productId: String, val purchaseToken: String)

    @Serializable
    private data class Response(val valid: Boolean, val tier: String? = null, val expiryTimeMillis: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }

    override val isConfigured: Boolean get() = endpoint.startsWith("https://")

    override suspend fun verify(purchase: PurchaseToVerify): VerificationResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext VerificationResult.Unavailable("not_configured")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = json.encodeToString(
                Request.serializer(),
                Request(purchase.packageName, purchase.productId, purchase.purchaseToken),
            )
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) return@withContext VerificationResult.Unavailable("http_$code")
            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val response = json.decodeFromString(Response.serializer(), text)
            val tier = response.tier?.let { t -> Tier.entries.firstOrNull { it.name == t } }
            if (response.valid && tier != null && tier != Tier.FREE) {
                VerificationResult.Verified(tier, response.expiryTimeMillis)
            } else {
                VerificationResult.Invalid
            }
        } catch (e: Exception) {
            VerificationResult.Unavailable(e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }
}
