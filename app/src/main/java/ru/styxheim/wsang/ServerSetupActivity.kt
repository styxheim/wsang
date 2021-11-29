package ru.styxheim.wsang

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import ru.styxheim.wsang.databinding.ActivityServerSetupBinding

class ServerSetupActivity : AppCompatActivity() {
  private var binding: ActivityServerSetupBinding? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityServerSetupBinding.inflate(layoutInflater)
    setContentView(binding!!.root)

    setupView()
  }

  private fun setupView() {
    binding!!.serverApply.setOnClickListener {
      if( binding!!.serverAddress.text.isNotEmpty() ) {
        /* TODO: check server */
      }
    }
  }


}