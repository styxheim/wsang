package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.content.*;
import android.view.*;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class FinishActivity extends StartFinish
{
  protected ArrayList<Long> times = new ArrayList<Long>();

  public FinishActivity() {

  }

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

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    long timeInMillis = System.currentTimeMillis();

    timeInMillis -= settings.getLong("chrono_offset", Default.chrono_offset);

    if (keyCode == settings.getInt("chrono_key", Default.chrono_key))
    {
      times.add(0, timeInMillis);
      if( times.size() > 40 )
        times.remove(times.size() - 1);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void selectFinishTimeOnClick(View v)
  {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    if( times.size() == 0 ) {
      Toast.makeText(FinishActivity.this,
                     "Используйте кнопку секундомера для отсечки времени",
                     Toast.LENGTH_SHORT).show();

      return;
    }

    String[] Stimes = new String[times.size()];

    for( int i = 0; i < times.size(); i++ ) {
      cal.setTimeInMillis(times.get(i));
      String time = String.format("%2d) %02d:%02d:%02d.%02d",
                                  i + 1,
                                  cal.get(Calendar.HOUR),
                                  cal.get(Calendar.MINUTE),
                                  cal.get(Calendar.SECOND),
                                  (cal.get(Calendar.MILLISECOND) / 10));

      Stimes[i] = time;
    }

    builder.setItems(Stimes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item)
      {
        Long time = times.get(item);

        Toast.makeText(FinishActivity.this,
                       "Выбрано: " + Long.toString(time),
                       Toast.LENGTH_SHORT).show();

      }
    });
    builder.create().show();
  }

  public void startOnClick(View v)
  {
  }
}

