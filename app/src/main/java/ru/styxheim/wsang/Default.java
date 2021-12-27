package ru.styxheim.wsang;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class Default {
  final static public int disciplineId = -1;
  final static public long competitionId = 0;
  final static public String server_addr = "127.0.0.1";
  final static public String CHRONO_PREFS = "chrono";
  final static public long chrono_offset = 0;
  final static public int chrono_key = KeyEvent.KEYCODE_VOLUME_UP;
  final static public String time_empty = "00:00:00.00";
  final static public int chrono_vibro = 100;

  /** Filename for local competition data (notebook) */
  final static public String localRowsFile = "localRows";

  final static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

  public static String competitionConfig(String name, Long competitionId) {
    return String.format(Locale.US, "%s_CID_%s", name, Long.toHexString(competitionId));
  }

  public static String competitionConfig(String name, SharedPreferences mainSettings) {
    return competitionConfig(name, mainSettings.getLong("CompetitionId", Default.competitionId));
  }

  public static String competitionJson(String name, SharedPreferences mainSettigs) {
    return String.format(Locale.US, "%s.json", competitionConfig(name, mainSettigs));
  }

  public static SharedPreferences getCompetitionsSharedPreferences(Context context,
                                                                   String name,
                                                                   SharedPreferences mainSettings, int mode) {
    String configName = competitionConfig(name, mainSettings);

    return context.getSharedPreferences(configName, mode);
  }

  public static String millisecondsToString(long ms) {
    long millis = Math.abs(ms);

    cal.setTimeInMillis(millis);
    String time = String.format("%02d:%02d:%02d.%02d",
        millis / (60 * 60 * 1000),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
        (cal.get(Calendar.MILLISECOND) / 10));
    return time;
  }

  public static String millisecondsToStringShort(long ms) {
    long millis = Math.abs(ms);

    cal.setTimeInMillis(millis);
    String time = String.format("%02d:%02d:%02d",
        millis / (60 * 60 * 1000),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND));
    return time;
  }
};
