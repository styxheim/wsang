package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;

import java.io.*;

public class SettingsActivity extends Activity
{
  private SharedPreferences settings;
  private SharedPreferences settings_chrono;

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    long timeInMillis;
    long off = System.currentTimeMillis() - SystemClock.uptimeMillis();
    timeInMillis = event.getEventTime() + off;

    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP )
    {
      SharedPreferences.Editor ed = settings_chrono.edit();
      ed.putLong("offset", timeInMillis);
      ed.apply();

      int vtime = settings_chrono.getInt("vibro", Default.chrono_vibro);
      if( vtime > 0 ) {
        Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(vtime, VibrationEffect.DEFAULT_AMPLITUDE));
      }
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
    settings_chrono = getSharedPreferences("chrono", Context.MODE_PRIVATE);
    setContentView(R.layout.settings);
  }

  @Override
  public void onStart()
  {
    final Runnable cron;

    Log.d("wsa-ng", "SettingsActivity:onStart()");

    super.onStart();

    /* chronometer */
    final TextView tv = (TextView)findViewById(R.id.settings_chronometer);

    cron = new Runnable() {
      public void run() {
        long offsetMillis = settings_chrono.getLong("offset", Default.chrono_offset);

        tv.setText(Default.millisecondsToString(System.currentTimeMillis() - offsetMillis));
        tv.postDelayed(this, 20);
      }
    };

    tv.post(cron);

    _setup_mode();
    _setup_server_addr();
    _update_terminal_id();
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

  public void _setup_mode()
  {
    final TextView v = (TextView)findViewById(R.id.settings_mode);
    final Launcher.Mode mode;

    mode = Launcher.Mode.valueOf(settings.getString("mode", Default.mode));
    v.post(new Runnable() {
      public void run() {
        switch( mode ) {
        case START:
          v.setText("Судья на старте");
          break;
        case FINISH:
          v.setText("Судья на финише");
          break;
        default:
          v.setText("Неизвестный");
          break;
        }
      }
    });
  }

  public void _setup_server_addr()
  {
    final Button server_bt = (Button)findViewById(R.id.settings_server_addr_apply);
    final EditText server_ed = (EditText)findViewById(R.id.settings_server_edit);
    server_bt.setVisibility(server_bt.INVISIBLE);
    server_ed.setText(settings.getString("server_addr", Default.server_addr));

    server_ed.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int st, int b, int c)
      {
      }

      @Override
      public void afterTextChanged(Editable s)
      {
        if( s.toString() != settings.getString("server_addr", Default.server_addr) )
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

  public void _update_terminal_id()
  {
    final TextView tv = findViewById(R.id.settings_terminal_id);
    SharedPreferences race_settings = getSharedPreferences("race", Context.MODE_PRIVATE);
    TerminalStatus terminalStatus = new TerminalStatus(race_settings);

    tv.setText(terminalStatus.terminalId);
  }

  public void _update_chrono_offset_title()
  {
    final TextView tv = (TextView)findViewById(R.id.settings_chrono_offset);

    tv.setText(Long.toString(settings_chrono.getLong("offset", Default.chrono_offset)));
  }

  public void _update_chrono_key_title()
  {
    final TextView tv = (TextView)findViewById(R.id.settings_chrono_key);
    final String key = "VOLUME UP/DOWN";

    tv.post(new Runnable() {
      @Override
      public void run() {
        tv.setText(key);
      }
    });
  }

  public void importOnClick(View v) {
    String jsonf;
    final Launcher.Mode mode = Launcher.Mode.valueOf(settings.getString("mode", Default.mode));

    switch( mode ) {
      case FINISH:
        jsonf = "START";
        break;
      case START:
        jsonf = "FINISH";
        break;
      default:
        jsonf = "UNK";
    }

    jsonf = "funny_starts." + jsonf + ".json";
    final File file_starts = new File(Environment.getExternalStorageDirectory(), jsonf);
    
    if( !file_starts.canRead() ) {
      Toast.makeText(SettingsActivity.this,
                     "Невозможно прочесть " + file_starts.getAbsolutePath(),
                     Toast.LENGTH_SHORT).show();
      return;
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Импорт");
    builder.setMessage("Попытаться импортировать данные? В случае ошибки всё потерять всё. После импорта приложение остановится.");
    builder.setPositiveButton("Выполнить", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          _import(mode, file_starts);

          moveTaskToBack(true);
          android.os.Process.killProcess(android.os.Process.myPid());
          System.exit(1);
        }
      });
    builder.setNegativeButton("Одуматься", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
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

        SharedPreferences.Editor ed;
        ed = getSharedPreferences("chrono_data", Context.MODE_PRIVATE).edit();
        ed.clear();
        ed.commit();

        ed = settings.edit();
        ed.putLong(RaceStatus.TIMESTAMP, 0L);
        ed.commit();

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

  public void modeOnClick(View v)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final String[] keys = { "Судья на старте", "Судья на финише" };
    final Launcher.Mode[] vals = { Launcher.Mode.START, Launcher.Mode.FINISH };
    final Launcher.Mode mode;

    mode = Launcher.Mode.valueOf(settings.getString("mode", Default.mode));

    builder.setTitle("Выберите режим работы");
    builder.setItems(keys, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, final int item) {
        if( vals[item] != mode ) {
          SharedPreferences.Editor ed;

          ed = settings.edit();
          ed.putString("mode", vals[item].name());
          ed.commit();

          /* switch to new mode throug Launcher */
          Intent intent = new Intent(SettingsActivity.this, Launcher.class);
          startActivity(intent);
        }
      }
    });
    builder.create().show();
  }

  public void exportOnClick(View v)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final String[] keys = {",", ";"};

    builder.setTitle("Тип разделителя:");
    builder.setItems(keys, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item)
      {
        _export(keys[item]);
      }
    });
    builder.create().show();
  }
  
  public void _import(Launcher.Mode mode, File file_starts)
  {
     StartList lstarts = new StartList();
     StartList rstarts = new StartList();
     StartRow lrow;
     
     lstarts.Load(getApplicationContext());
     rstarts.setOutput(file_starts.getAbsolutePath());
     rstarts.Load(getApplicationContext());
     
     for( StartRow rrow : rstarts ) {
       lrow = lstarts.getRecord(rrow.getRowId());
       if( lrow == null ) {
         if( mode == Launcher.Mode.FINISH )
           rrow.finishAt = 0;
         else
           rrow.startAt = 0;
           
         lstarts.addRecord(rrow);
       }
       else {
         if( mode == Launcher.Mode.FINISH )
           lrow.startAt = rrow.startAt;
         else
           lrow.finishAt = rrow.finishAt;
       }
     }
     lstarts.Save(getApplicationContext());
  }

  public void _export(String separator)
  {
    StartList starts = new StartList();

    starts.Load(getApplicationContext());

    File file = new File(Environment.getExternalStorageDirectory(), "funny_starts.csv");
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
      fos.write(String.format("lap%screw%s\"start time\"%s\"finish time\"%sdelta\n",
                              separator, separator, separator, separator).getBytes());
      for( StartRow row : starts ) {
        String finishAt = Default.millisecondsToString(row.finishAt);
        String startAt = Default.millisecondsToString(row.startAt);
        String timeDelta = "";

        if( row.finishAt != 0 && row.startAt != 0 ) {
          timeDelta = Default.millisecondsToString((row.finishAt / 10 - row.startAt / 10) * 10);
        }

        if( separator.compareTo(";") == 0 ) {
          finishAt = finishAt.replace('.', ',');
          startAt = startAt.replace('.', ',');
          timeDelta = timeDelta.replace('.', ',');
        }

        String s = String.format("%d%s%d%s\"%s\"%s\"%s\"%s\"%s\"\n",
                                 row.lapId, separator,
                                 row.crewId, separator,
                                 startAt, separator,
                                 finishAt, separator,
                                 timeDelta);
        fos.write(s.getBytes());
      }
      fos.close();
    } catch( IOException e ) {
     Toast.makeText(SettingsActivity.this,
                   "Ошибка экспорта: " + e.getMessage(),
                   Toast.LENGTH_SHORT).show();
    }
   
    String jsonf = "funny_starts." + settings.getString("mode", Default.mode) + ".json";
    
    File file_starts = new File(Environment.getExternalStorageDirectory(), jsonf);
    starts.setOutput(file_starts.getAbsolutePath());
    starts.Save(getApplicationContext());

    Toast.makeText(SettingsActivity.this,
                   "Сохранено в " + file.getAbsolutePath(),
                   Toast.LENGTH_SHORT).show();

  }
}
