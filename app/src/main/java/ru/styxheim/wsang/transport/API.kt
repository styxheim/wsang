package ru.styxheim.wsang.transport

import com.squareup.moshi.JsonClass

class API {
  @JsonClass(generateAdapter = true)
  data class Error(
    var Text: String = ""
  )

  @JsonClass(generateAdapter = true)
  open class Response(
    open val Error: Error? = null
  )

  @JsonClass(generateAdapter = true)
  data class RaceStatus(
    val CompetitionId: Long = 0,
    var CompetitionName: String? = "",
    var SyncPoint: Long?,
    val TimeStamp: Long = 0,
    var Gates: MutableList<Int>? = mutableListOf(),
    var Penalties: MutableList<Int>? = mutableListOf(),
    var Crews: MutableList<Int>? = mutableListOf(),
    var Disciplines: MutableList<Discipline>? = mutableListOf(),
    var IsActive: Boolean = false
  )

  @JsonClass(generateAdapter = true)
  data class Discipline(
    var Id: Int = 0,
    var Name: String = "",
    var Gates: MutableList<Int> = mutableListOf()
  )

  @JsonClass(generateAdapter = true)
  data class TerminalDescriptor(
    var TerminalId: String,
    var TerminalVersion: String,
  )

  @JsonClass(generateAdapter = true)
  open class CompetitionListRequest(
    var TerminalDescriptor: TerminalDescriptor,
  )

  @JsonClass(generateAdapter = true)
  open class CompetitionListResponse(
    val ServerId: String,
    val Competitions: MutableList<RaceStatus> = mutableListOf(),
  ) : Response()
}