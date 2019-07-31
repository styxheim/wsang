package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.content.*;
import android.view.*;
import android.util.Log;
import java.util.Calendar;
import java.util.TimeZone;

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
    final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    final TextView tv = (TextView)findViewById(R.id.start_chrono);

    Runnable cron = new Runnable() {
      public void run() {
        long offsetMIllis = settings.getLong("chrono_offset", 0);
        cal.setTimeInMillis(System.currentTimeMillis() - offsetMIllis);
        String time = String.format("%02d:%02d:%02d",
                                    cal.get(Calendar.HOUR),
                                    cal.get(Calendar.MINUTE),
                                    cal.get(Calendar.SECOND));

        tv.setText(time);
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
