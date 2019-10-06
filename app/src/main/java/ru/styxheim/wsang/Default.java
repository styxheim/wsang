package ru.styxheim.wsang;

import android.view.KeyEvent;

import java.util.Calendar;
import java.util.TimeZone;

public class Default {
  final static public int disciplineId = -1;
  final static public String mode = Launcher.Mode.START.name();
  final static public String server_addr = "127.0.0.1";
  final static public String CHRONO_PREFS = "chrono";
  final static public long chrono_offset = 0;
  final static public int chrono_key = KeyEvent.KEYCODE_VOLUME_UP;
  final static public String time_empty = "00:00:00.00";
  final static public int chrono_vibro = 100;

  final static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

  public static String millisecondsToString(long ms)
  {
    long millis = Math.abs(ms);

    cal.setTimeInMillis(millis);
    String time = String.format("%02d:%02d:%02d.%02d",
                                millis / (60 * 60 * 1000),
                                cal.get(Calendar.MINUTE),
                                cal.get(Calendar.SECOND),
                                (cal.get(Calendar.MILLISECOND) / 10));
    return time;
  }

  public static String millisecondsToStringShort(long ms)
  {
    long millis = Math.abs(ms);

    cal.setTimeInMillis(millis);
    String time = String.format("%02d:%02d:%02d",
                                millis / (60 * 60 * 1000),
                                cal.get(Calendar.MINUTE),
                                cal.get(Calendar.SECOND));
    return time;
  }
};
