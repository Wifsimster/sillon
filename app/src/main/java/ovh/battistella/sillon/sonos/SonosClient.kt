package ovh.battistella.sillon.sonos

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** SOAP over plain HTTP to a speaker on the LAN (port 1400). Nothing leaves the local network. */
class SonosClient(http: OkHttpClient) {
    private val http = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun call(baseUrl: String, call: SoapCall): Map<String, String> {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + call.service.path)
            .header("SOAPACTION", call.soapAction)
            .post(call.body().toRequestBody(XML))
            .build()
        val (code, body) = http.send(request)
        if (code !in 200..299) throw SonosException(Soap.faultCode(body), "${call.action}: HTTP $code")
        return Soap.values(body)
    }

    suspend fun zoneGroups(baseUrl: String): List<SonosGroup> =
        ZoneGroups.parse(call(baseUrl, AvTransport.zoneGroupState())["ZoneGroupState"].orEmpty())

    private suspend fun OkHttpClient.send(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { it.code to it.body?.string().orEmpty() } }
                    result.fold(cont::resume, cont::resumeWithException)
                }
            })
        }

    private companion object {
        val XML = "text/xml; charset=\"utf-8\"".toMediaType()
    }
}
