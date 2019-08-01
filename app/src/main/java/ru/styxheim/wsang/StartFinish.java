package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.content.*;
import android.view.*;
import android.util.Log;

public class StartFinish extends Activity
{
  protected SharedPreferences settings;
  protected boolean countDownMode = false;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "StartFinish:onCreate()");
    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
  }

  @Override
  public void onStart()
  {
    Log.d("wsa-ng", "StartFinish:onStart()");

    super.onStart();

    /* chronometer */
    final TextView tv = (TextView)findViewById(R.id.start_chrono);

    Runnable cron = new Runnable() {
      public void run() {
        long offsetMillis = settings.getLong("chrono_offset", Default.chrono_offset);

        tv.setText(Default.millisecondsToString(System.currentTimeMillis() - offsetMillis));
        tv.postDelayed(this, 20);
      }
    };

    tv.post(cron);

    Intent intent = new Intent(this, MainService.class);
    startService(intent); /* boot data records */
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "StartFinish:onStop()");

    super.onStop();
  }

  public void settingsOnClick(View v)
  {
    if( countDownMode ) {
      Toast.makeText(this,
                     "Дождитесь окончания отсчёта",
                     Toast.LENGTH_SHORT).show();
    }
    else {
      Intent intent = new Intent(this, SettingsActivity.class);
      startActivity(intent);
    }
  }
}
