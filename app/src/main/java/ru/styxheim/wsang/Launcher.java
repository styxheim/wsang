package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;

public class Launcher extends Activity {

  enum Mode {
    UNKNOWN,
    START,
    DISTANCE,
    FINISH,
  }

  ;

  protected SharedPreferences settings;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d("wsa-ng", "Launcher:onCreate()");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);

    switch_activity();
  }

  @Override
  public void onStart() {
    Log.d("wsa-ng", "Launcher:onStart()");

    super.onStart();
  }

  @Override
  public void onStop() {
    Log.d("wsa-ng", "Launcher:onStop()");

    super.onStop();
  }

  protected boolean switch_activity() {
    final Intent intent;

    if (settings.getBoolean("newServerAddressRequired", true)) {
      intent = new Intent(this, ServerSetupActivity.class);
    } else {
      intent = new Intent(this, MainActivity.class);
    }

    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
        Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    return true;
}

  @Override
  protected void onResume() {
    Log.d("wsa-ng", "Launcher:onResume()");
    super.onResume();

    switch_activity();
  }
}

