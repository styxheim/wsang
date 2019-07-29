package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.util.Log;
import android.icu.util.Calendar;

public class SettingsActivity extends Activity
{
  private SharedPreferences settings;

  private int timeKey = -1;
  private boolean grabKeyMode = false;

  private AlertDialog dialog = null;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    Log.d("wsa-ng", "SettingsActivity:onCreate()");

    super.onCreate(savedInstanceState);

    settings = getSharedPreferences("main", Context.MODE_PRIVATE);
    setContentView(R.layout.settings);
  }

  @Override
  public void onStart()
  {
    final TextView tv = (TextView)findViewById(R.id.settings_chronometer);
    final TextView tv_serv = (TextView)findViewById(R.id.settings_server_addr);
    final Runnable cron;
    final Calendar cal = Calendar.getInstance();

    Log.d("wsa-ng", "SettingsActivity:onStart()");

    super.onStart();

    /* chronometer */
    cron = new Runnable() {
      public void run() {
        cal.setTimeInMillis(System.currentTimeMillis());
        String time = String.format("%02d:%02d:%02d.%03d",
                                    cal.get(Calendar.HOUR),
                                    cal.get(Calendar.MINUTE),
                                    cal.get(Calendar.SECOND),
                                    cal.get(Calendar.MILLISECOND));

        tv.setText(time);
        tv.postDelayed(this, 20);
      }
    };

    tv.postDelayed(cron, 1000);

    /* other fields */
    tv_serv.setText(settings.getString("server_addr", "???"));
    /*
    Editor edit = settings.edit();
    edit.putString("server_addr", "127.0.0.1");
    edit.apply();
    */
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "SettingsActivity:onStop()");

    super.onStop();
  }
/*
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    super.onKeyDown(keyCode, event);
    if( grabKeyMode ) {
      timeKey = keyCode;
      if( dialog != null )
        dialog.dismiss();
      return true;
    }
    return false;
  }
*/
  public void doneOnClick(View v)
  {
    finish();
  }

  public void chooseTimeButton(View v)
  {
    AlertDialog.Builder ab = new AlertDialog.Builder(this);

    ab.setTitle("Выбор клавиши отсечки");
    ab.setMessage("Нажмите на любую физическую клавишу.");
    ab.setCancelable(false);
    ab.setNegativeButton("Отмена",
                         new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, int id) {
                             Toast.makeText(SettingsActivity.this, "Отменено", Toast.LENGTH_SHORT).show();
                             dialog.cancel();
                           }
                         });
    dialog = ab.create();
    dialog.show();
    grabKeyMode = true;
  }

  public void chooseTimeOffset(View v)
  {
  }

}
