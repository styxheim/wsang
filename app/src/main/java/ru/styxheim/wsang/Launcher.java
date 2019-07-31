package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.util.Log;

public class Launcher extends Activity
{
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "Launcher:onCreate()");

    super.onCreate(savedInstanceState);

    Intent intent = new Intent(this, StartActivity.class);
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

