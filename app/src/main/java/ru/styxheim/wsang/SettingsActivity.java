package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import java.util.TimeZone;
import java.util.Calendar;

import java.io.*;

public class SettingsActivity extends Activity
{
  private SharedPreferences settings;

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    long timeInMillis = System.currentTimeMillis();

    if (keyCode == settings.getInt("chrono_key", KeyEvent.KEYCODE_VOLUME_UP))
    {
      SharedPreferences.Editor ed = settings.edit();
      ed.putLong("chrono_offset", timeInMillis);
      ed.apply();
      _update_chrono_offset_title();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

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
    final Runnable cron;
    final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    Log.d("wsa-ng", "SettingsActivity:onStart()");

    super.onStart();

    /* chronometer */
    final TextView tv = (TextView)findViewById(R.id.settings_chronometer);

    cron = new Runnable() {
      public void run() {
        long offsetMIllis = settings.getLong("chrono_offset", 0);
        cal.setTimeInMillis(System.currentTimeMillis() - offsetMIllis);
        String time = String.format("%02d:%02d:%02d.%03d",
                                    cal.get(Calendar.HOUR),
                                    cal.get(Calendar.MINUTE),
                                    cal.get(Calendar.SECOND),
                                    cal.get(Calendar.MILLISECOND));

        tv.setText(time);
        tv.postDelayed(this, 20);
      }
    };

    tv.post(cron);

    _setup_server_addr();
    _update_chrono_key_title();
    _update_chrono_offset_title();
  }

  @Override
  public void onStop()
  {
    Log.d("wsa-ng", "SettingsActivity:onStop()");

    super.onStop();
  }

  public void doneOnClick(View v)
  {
    finish();
  }

  public void _setup_server_addr()
  {
    final Button server_bt = (Button)findViewById(R.id.settings_server_addr_apply);
    final EditText server_ed = (EditText)findViewById(R.id.settings_server_edit);
    server_bt.setVisibility(server_bt.INVISIBLE);
    server_ed.setText(settings.getString("server_addr", ""));

    server_ed.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int st, int b, int c)
      {
      }

      @Override
      public void afterTextChanged(Editable s)
      {
        if( s.toString() != settings.getString("server_addr", "") )
          server_bt.setVisibility(server_bt.VISIBLE);
        else
          server_bt.setVisibility(server_bt.INVISIBLE);
      }

      @Override
      public void beforeTextChanged(CharSequence s, int st, int b, int c)
      {
      }
    });

    server_bt.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        SharedPreferences.Editor edit = settings.edit();
        edit.putString("server_addr", server_ed.getText().toString());
        edit.apply();
        Toast.makeText(SettingsActivity.this,
                       "NEW ADDR: " + server_ed.getText().toString(),
                       Toast.LENGTH_SHORT).show();
        v.setVisibility(v.INVISIBLE);
      }
    });
  }

  public void _update_chrono_offset_title()
  {
    final TextView tv = (TextView)findViewById(R.id.settings_chrono_offset);

    tv.setText(Long.toString(settings.getLong("chrono_offset", 0)));
  }

  public void _update_chrono_key_title()
  {
    final TextView tv = (TextView)findViewById(R.id.settings_chrono_key);
    final String key;
    switch( settings.getInt("chrono_key", KeyEvent.KEYCODE_VOLUME_UP) ) {
    case KeyEvent.KEYCODE_VOLUME_UP:
      key = "VOLUME_UP";
      break;
    case KeyEvent.KEYCODE_VOLUME_DOWN:
      key = "VOLUME_DOWN";
      break;
    default:
      key = "???";
      break;
    }

    tv.post(new Runnable() {
      @Override
      public void run() {
        tv.setText(key);
      }
    });
  }

  public void changeChronoKeyOnClick(View v)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final String[] keys = {"VOLUME_UP", "VOLUME_DOWN"};
    final int[] keys_no = {KeyEvent.KEYCODE_VOLUME_UP,
                           KeyEvent.KEYCODE_VOLUME_DOWN};


    builder.setTitle("Key choose");
    builder.setItems(keys, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item)
      {
        SharedPreferences.Editor ed = settings.edit();
        ed.putInt("chrono_key", keys_no[item]);
        ed.apply();
        _update_chrono_key_title();
      }
    });
    builder.create().show();
  }

  public void resetOnClick(View v) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Выход");
    builder.setMessage("Удалить все данные и остановить приложение?");
    builder.setPositiveButton("Выполнить", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int id) {
        Intent intent = new Intent(SettingsActivity.this, MainService.class);
        stopService(intent);

        StartList starts = new StartList();
        starts.Save(getApplicationContext());

        moveTaskToBack(true);
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
      }
    });
    builder.setNegativeButton("Одуматься", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int id) {
        dialog.dismiss();
      }
    });
    builder.create().show();
  }

  public void exportOnClick(View v)
  {
    StartList starts = new StartList();

    starts.Load(getApplicationContext());

    File file = new File(Environment.getExternalStorageDirectory(), "starts.csv");
    FileOutputStream fos;

    try {
      fos = new FileOutputStream(file);
    } catch( FileNotFoundException e ) {
     Toast.makeText(SettingsActivity.this,
                   "Ошибка экспорта: " + e.getMessage(),
                   Toast.LENGTH_SHORT).show();
     return;
    }

    try {
      fos.write(String.format("lap,crew,time\n").getBytes());
      for( StartRow row : starts ) {
        String s = String.format("%d,%d,%s\n",
                                 row.lapId, row.crewId, row.startAt);
        fos.write(s.getBytes());
      }
      fos.close();
    } catch( IOException e ) {
     Toast.makeText(SettingsActivity.this,
                   "Ошибка экспорта: " + e.getMessage(),
                   Toast.LENGTH_SHORT).show();
    }

    Toast.makeText(SettingsActivity.this,
                   "Сохранено в " + file.getAbsolutePath(),
                   Toast.LENGTH_SHORT).show();

  }
}
