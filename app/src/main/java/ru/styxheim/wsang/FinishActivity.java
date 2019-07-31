package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.util.Log;

public class FinishActivity extends StartFinish
{
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "Launcher:onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_finish);
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

  public void startOnClick(View v)
  {
  }
}

