package ru.styxheim.wsang;

import android.util.JsonWriter;
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.media.MediaPlayer;

import android.net.Uri;

import java.io.*;

import java.util.ArrayList;

public class SettingsActivity extends Activity
{
  private SharedPreferences settings;
  private SharedPreferences settings_chrono;
  private MediaPlayer mPlayer;

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

      mPlayer.start();
      int vtime = settings_chrono.getInt("vibro", Default.chrono_vibro);
      if( vtime > 0 ) {
        Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(vtime, VibrationEffect.DEFAULT_AMPLITUDE));
      }
      _update_chrono_offset_title();
      mPlayer.seekTo(0);
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
    this.mPlayer = MediaPlayer.create(SettingsActivity.this, R.raw.lap);
    mPlayer.seekTo(0);

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
    _update_race_info();
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
    v.setEnabled(false);
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

  public void _update_race_info()
  {
    final TextView tv = findViewById(R.id.settings_race_id);
    final TextView tv_t = findViewById(R.id.settings_race_title);
    SharedPreferences race_settings = getSharedPreferences("race", Context.MODE_PRIVATE);
    RaceStatus raceStatus = new RaceStatus(race_settings);

    tv.setText(Long.toString(raceStatus.competitionId));
    if( raceStatus.competitionName != null )
      tv_t.setText(raceStatus.competitionName);
    else
      tv_t.setText("");
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

  public void importOnClick(View v)
  {
   Toast.makeText(SettingsActivity.this,
                 "Unimplemented",
                 Toast.LENGTH_SHORT).show();

  }

  public void resetOnClick(View v)
  {
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

        StartList starts_local = new StartList();
        starts_local.setOutput("localRows.json");
        starts_local.Save(getApplicationContext());

        SharedPreferences.Editor ed;
        ed = getSharedPreferences("chrono_data", Context.MODE_PRIVATE).edit();
        ed.clear();
        ed.commit();

        SharedPreferences race_settings = getSharedPreferences("race", Context.MODE_PRIVATE);
        RaceStatus race = new RaceStatus();
        race.saveSettings(race_settings);

        TerminalStatus oldTerm = new TerminalStatus(race_settings);
        TerminalStatus term = new TerminalStatus();
        term.terminalId = oldTerm.terminalId;
        term.saveSettings(race_settings);

        ed = settings.edit();
        ed.putLong(RaceStatus.TIMESTAMP, 0L);
        ed.commit();

        ed = settings_chrono.edit();
        ed.putLong("offset", 0L);
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
    StartList starts = new StartList();
    StartList starts_local = new StartList();
    ServerStatus ss = new ServerStatus();
    File raceFile;
    SharedPreferences race_settings = getSharedPreferences("race", Context.MODE_PRIVATE);
    ss.terminalStatus.add(new TerminalStatus(race_settings));
    ss.raceStatus = new RaceStatus(race_settings);
    raceFile = new File(Environment.getExternalStorageDirectory(), "tid_" + ss.terminalStatus.get(0).terminalId + ".json");
    starts.Load(getApplicationContext());
    starts_local.setOutput("localRows.json");
    starts_local.Load(this);

    try {
      try( StringWriter sw = new StringWriter();
           JsonWriter jw = new JsonWriter(sw) ) {

        jw.setIndent("  ");
        jw.beginObject();
        jw.name("Configuration");
        ss.saveJSON(jw);
        jw.name("Lap");
        starts.saveJSON(jw);
        jw.name("LapLocal");
        starts_local.saveJSON(jw);
        jw.endObject();

        try( FileOutputStream fos = new FileOutputStream(raceFile) ) {
          fos.write(sw.toString().getBytes());
        }
      }
    } catch( Exception e ) {
      StringWriter psw = new StringWriter();
      PrintWriter pw = new PrintWriter(psw);

      e.printStackTrace(pw);

      Toast.makeText(SettingsActivity.this,
                     "Dump error: " + e.getMessage(),
                     Toast.LENGTH_SHORT).show();
      Log.e("wsa-ng", "Dump error: " + psw.toString());
      return;
    }

    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/*");
    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(raceFile));
    this.startActivity(Intent.createChooser(intent, "Send to"));

    Toast.makeText(SettingsActivity.this,
                   "See result in: " + raceFile.getPath().toString(),
                   Toast.LENGTH_SHORT).show();
  }
}
