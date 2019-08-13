package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;

public class Launcher extends Activity
{

  enum Mode {
    UNKNOWN,
    START,
    DISTANCE,
    FINISH,
  };

  protected SharedPreferences settings;
  protected Mode launch_mode = Mode.UNKNOWN;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "Launcher:onCreate()");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);

    switch_activity();
  }

  @Override
  public void onStart()
  {
    Log.d("wsa-ng", "Launcher:onStart()");

    super.onStart();
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "Launcher:onStop()");

    super.onStop();
  }

  protected boolean switch_activity()
  {
    final Intent intent;
    final Mode new_launch_mode;

    new_launch_mode = Mode.valueOf(settings.getString("mode", Default.mode));

    if( launch_mode != new_launch_mode ) {
      Log.i("wsa-ng", "Launcher: Switch from " + launch_mode.name() +
            " to " + new_launch_mode.name());
      launch_mode = new_launch_mode;
      switch( new_launch_mode ) {
      case START:
        intent = new Intent(this, StartActivity.class);
        break;
      case FINISH:
        intent = new Intent(this, FinishActivity.class);
        break;
      default:
        /* ??? */
        return true;
      }
      launch_mode = new_launch_mode;
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                      Intent.FLAG_ACTIVITY_CLEAR_TASK);
      startActivity(intent);
      return true;
    }
    return false;
  }

  @Override
  protected void onResume()
  {
    Log.d("wsa-ng", "Launcher:onResume()");
    super.onResume();

    if( !switch_activity() ) {
      Log.i("wsa-ng", "Launcher: Exit");
      finish();
    }
  }
}

