package ru.styxheim.wsang;

import java.util.Iterator;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;
import android.view.KeyEvent;
import android.content.SharedPreferences;
import java.util.ArrayList;

public class Chrono implements Iterable<Chrono.Record>
{
  protected ArrayList<Record> times = new ArrayList<Record>();
  protected int times_limit = 1000;
  protected SharedPreferences settings;
  protected SharedPreferences chrono_data;

  protected Vibrator vibro_service;

  static public class Record
  {
    protected boolean selected;
    protected long value;

    public Record(long value)
    {
      this.value = value;
    }

    public long getValue()
    {
      return value;
    }

    public boolean isSelected()
    {
      return selected;
    }

    public void select()
    {
      selected = true;
    }

    public void deselect()
    {
      selected = false;
    }
  }

  @Override
  public Iterator<Record> iterator()
  {
    return times.iterator();
  }

  public Chrono(SharedPreferences settings,
                SharedPreferences chrono_data,
                Vibrator vibro_service)
  {
    this.settings = settings;
    this.chrono_data = chrono_data;

    this.vibro_service = vibro_service;
    reload();
  }

  protected void _save()
  {
    SharedPreferences.Editor ed = chrono_data.edit();
    for( int i = 0; i < times.size(); i++ ) {
      ed.putLong(Integer.toString(i), times.get(i).getValue());
    }
    ed.apply();
  }

  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    long timeInMillis;
    long off = System.currentTimeMillis() - SystemClock.uptimeMillis();
    timeInMillis = event.getEventTime() + off;

    timeInMillis -= settings.getLong("offset", Default.chrono_offset);

    /*if (keyCode == settings.getInt("chrono_key", Default.chrono_key))*/
    if( keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
       keyCode == KeyEvent.KEYCODE_VOLUME_UP )
    {
      times.add(0, new Record(timeInMillis));
      if( times.size() > times_limit )
        times.remove(times.size() - 1);

      int vtime = settings.getInt("vibro", Default.chrono_vibro);
      if( vtime > 0 ) {
        vibro_service.vibrate(VibrationEffect.createOneShot(vtime, VibrationEffect.DEFAULT_AMPLITUDE));
      }
      _save();
      return true;
    }
    return false;
  }

  public int getSize()
  {
    return times.size();
  }

  public void reload()
  {
    times.clear();
    for( int i = 0; i < times_limit; i++ ) {
      if( !chrono_data.contains(Integer.toString(i)) )
        break;
      times.add(new Record(chrono_data.getLong(Integer.toString(i), 0)));
    }
  }

  public Record getRecord(int i)
  {
    return times.get(i);
  }

  public Record getRecord(long ms)
  {
    for( Record r : times ) {
      if( r.getValue() == ms )
        return r;
    }
    return null;
  }
}
