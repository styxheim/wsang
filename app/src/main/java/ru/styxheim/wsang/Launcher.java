package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;

public class Launcher extends Activity
{

  enum Mode {
    START,
    DISTANCE,
    FINISH,
  };

  protected Mode launch_mode = Mode.START;
  //protected Mode launch_mode = Mode.FINISH;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    final Intent intent;

    Log.d("wsa-ng", "Launcher:onCreate()");
    super.onCreate(savedInstanceState);

    switch( launch_mode ) {
    case START:
      intent = new Intent(this, StartActivity.class);
      break;
    case FINISH:
      intent = new Intent(this, FinishActivity.class);
      break;
    default:
      /* ??? */
      return;
    }
    startActivity(intent);
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

  @Override
  protected void onResume()
  {
    Log.d("wsa-ng", "Launcher:onResume()");
    super.onResume();
    finish();
  }

}

