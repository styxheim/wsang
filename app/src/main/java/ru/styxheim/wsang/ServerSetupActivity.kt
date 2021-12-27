package ru.styxheim.wsang

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.squareup.moshi.Moshi
import ru.styxheim.wsang.databinding.ActivityServerSetupBinding
import ru.styxheim.wsang.transport.Transport
import ru.styxheim.wsang.transport.API
import java.util.concurrent.ThreadLocalRandom

class ServerSetupActivity : AppCompatActivity() {
  private var binding: ActivityServerSetupBinding? = null
  private lateinit var mainSettings: SharedPreferences
  private lateinit var terminalId: String
  private lateinit var terminalVersion: String
  private val moshi: Moshi = Moshi.Builder().build()
  private val competitionAdapter = moshi.adapter(API.RaceStatus::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityServerSetupBinding.inflate(layoutInflater)
    setContentView(binding!!.root)

    stopService()
    initVars()
    setupView()
  }

  private fun stopService() {
    val intent = Intent(this, MainService::class.java)

    stopService(intent)
  }

  private fun initVars() {
    mainSettings = getSharedPreferences("main", MODE_PRIVATE)
    terminalVersion = packageManager.getPackageInfo(packageName, 0).versionName

    initMainPreferences()
  }

  private fun initMainPreferences() {
    mainSettings.getString("TerminalId", null)?.let {
      terminalId = it
    } ?: run {
      terminalId = java.lang.Long.toHexString(ThreadLocalRandom.current().nextLong())
      with(mainSettings.edit()) {
        putString("TerminalId", terminalId)
        apply()
      }
      Log.i("wsa-ng-setup", "Generate new TerminalId")
    }
    Log.i("wsa-ng-setup", "TerminalId: ".plus(terminalId))
  }

  private fun setServer(serverAddress: String, competition: API.RaceStatus) {
    val intent = Intent(this, Launcher::class.java)
    val raceSettings = getSharedPreferences("race", MODE_PRIVATE)

    with(mainSettings.edit()) {
      putString("server_addr", serverAddress)
      putLong("CompetitionId", competition.CompetitionId)
      putString("TerminalId", terminalId)
      putBoolean("newServerAddressRequired", false)
      apply()
    }

    with(raceSettings.edit()) {
      putString("RaceStatus", competitionAdapter.toJson(competition))
      apply()
    }

    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
  }

  private fun chooseCompetitionIdFromList(
    serverAddress: String,
    competitions: MutableList<API.RaceStatus>
  ) {
    val builder = AlertDialog.Builder(this)
    val competitionsNames = competitions.map { it.CompetitionName }.toTypedArray()

    builder.setTitle(R.string.server_choose_competition)

    builder.setItems(competitionsNames) { _, which ->
      setServer(serverAddress, competitions[which])
    }

    val dialog = builder.create()
    dialog.show()
  }

  private fun loadCompetition(serverAddress: String) {
    Transport(serverAddress, terminalId, terminalVersion).getCompetitionList(
      onBegin = {
        runOnUiThread {
          clearError()
          binding!!.serverAddress.isEnabled = false
          binding!!.serverApply.isEnabled = false
        }
      },
      onEnd = {
        runOnUiThread {
          binding!!.serverAddress.isEnabled = true
          binding!!.serverApply.isEnabled = true
        }
      },
      onFail = { message -> runOnUiThread { setError(message) } },
      onResult = { terminalActivityList ->
        runOnUiThread {

          if (terminalActivityList.Competitions.count() == 0) {
            setError(getString(R.string.server_no_active_competitions))
          } else {
            chooseCompetitionIdFromList(serverAddress, terminalActivityList.Competitions)
          }
        }
      })
  }

  private fun clearError() {
    binding!!.serverSetupErrorMessage.visibility = View.GONE
  }

  private fun setError(message: String) {
    binding!!.serverSetupErrorMessage.text = message
    binding!!.serverSetupErrorMessage.visibility = View.VISIBLE
  }

  private fun setupView() {
    clearError()
    binding!!.serverApply.setOnClickListener {
      if (binding!!.serverAddress.text.isNotEmpty()) {
        val server_addr = binding!!.serverAddress.text.toString()

        loadCompetition(server_addr)
        with(mainSettings.edit()) {
          putString("server_addr", server_addr)
          apply()
        }
      }
    }

    mainSettings.getString("server_addr", null)?.let {
      binding!!.serverAddress.setText(it)
    }
  }


}