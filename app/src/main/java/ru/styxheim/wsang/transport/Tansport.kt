package ru.styxheim.wsang.transport

import com.squareup.moshi.Moshi
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException

class Transport(
  private val serverAddress: String,
  private val TerminalId: String,
  private val TerminalVersion: String,
) {
  private val mediaType = "application/json; charset=utf8".toMediaType()
  private val serverScheme: String = "http://"

  private val httpClient = OkHttpClient()
  private val moshi: Moshi = Moshi.Builder().build()

  private val competitionListRequestAdapter = moshi.adapter(API.CompetitionListRequest::class.java)
  private val competitionListResponseAdapter =
    moshi.adapter(API.CompetitionListResponse::class.java)

  class TransportCall(private val call: Call, private val onEnd: () -> Unit) {
    fun cancel() {
      onEnd()
      call.cancel()
    }
  }

  private fun <T : API.Response> enqueue(
    httpResource: String,
    requestJsonAdapter: () -> String,
    responseJsonAdapter: (BufferedSource) -> T?,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onFail: (message: String) -> Unit,
    onResult: (adminResponse: T) -> Unit
  ): TransportCall {
    val body = requestJsonAdapter().toRequestBody(mediaType)
    val httpRequest = Request.Builder()
      .url("${serverScheme}${serverAddress}${httpResource}")
      .post(body)
      .build()
    val call = httpClient.newCall(httpRequest)

    onBegin()
    call.enqueue(object : Callback {
      override fun onResponse(call: Call, response: Response) {
        onEnd()
        when (response.code) {
          200 -> {
            responseJsonAdapter(response.body!!.source())?.let {
              (it as API.Response).Error?.let { error ->
                onFail(error.Text)
              } ?: run {
                onResult(it)
              }
            } ?: run {
              onFail("received json not parsed: unknown error")
            }
          }
          else -> {
            onFail("response code: ${response.code} (expected 200)")
          }
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        if (call.isCanceled()) return
        onEnd()
        onFail(e.toString())
      }
    })
    return TransportCall(call, onEnd)
  }

  fun getCompetitionList(
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onFail: (message: String) -> Unit,
    onResult: (terminalActivityList: API.CompetitionListResponse) -> Unit
  ): TransportCall {
    val descriptor = API.TerminalDescriptor(TerminalId, TerminalVersion)
    val request = API.CompetitionListRequest(descriptor)

    return enqueue(
      "/api/competition/list",
      { competitionListRequestAdapter.toJson(request) },
      { source -> competitionListResponseAdapter.fromJson(source) },
      onBegin = onBegin,
      onEnd = onEnd,
      onFail = onFail,
      onResult = onResult
    )
  }
}