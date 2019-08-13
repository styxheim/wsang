package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.SharedPreferences;
import android.util.*;
import android.util.Log;
import java.util.*;
import java.io.IOException;
import java.io.*;

public class RaceStatus
{
  final static String SETTING_NAME = "competition";

  final static String TIMECODE = "TimeCode";
  final static String CREWS = "Crews";
  final static String GATES = "Gates";
  final static String PENALTIES = "Penalties";
  final static String DISCIPLINES = "Disciplines";
  final static String ID = "Id";
  final static String NAME = "Name";

  public long timeCode;
  public ArrayList<Integer> gates = new ArrayList<Integer>();
  public ArrayList<Integer> penalties = new ArrayList<Integer>();
  public ArrayList<Integer> crews = new ArrayList<Integer>();
  public ArrayList<Discipline> disciplines = new ArrayList<Discipline>();

  public class Discipline
  {
    public int id;
    public String name;

    public Discipline(JsonReader jr) throws IOException
    {
      this.fromJSON(jr);
    }

    public void fromJSON(JsonReader jr) throws IOException
    {
      jr.beginObject();
      while( jr.hasNext() ) {
        switch( jr.nextName() ) {
        case NAME:
          this.name = jr.nextString();
          break;
        case ID:
          this.id = jr.nextInt();
          break;
        default:
          break;
        }
      }
      jr.endObject();
    }

    public void toJSON(JsonWriter jw) throws IOException
    {
      jw.beginObject();
      jw.name(ID).value(this.id);
      jw.name(NAME).value(this.name);
      jw.endObject();
    }
  }

  public RaceStatus(SharedPreferences settings)
  {
    StringReader r = new StringReader(settings.getString("RaceStatus", ""));
    JsonReader jr = new JsonReader(r);

    try {
      loadJSON(jr);
    } catch( IOException e ) {
    }
  }

  private void _fillArrayList(ArrayList<Integer> ar, JsonReader jr) throws IOException
  {
    jr.beginArray();
    while( jr.hasNext() ) {
      ar.add(jr.nextInt());
    }
    jr.endArray();
  }

  public RaceStatus(JsonReader jr) throws IOException
  {
    loadJSON(jr);
  }

  public void loadJSON(JsonReader jr) throws IOException
  {
    this.penalties.clear();
    this.crews.clear();
    this.disciplines.clear();
    this.gates.clear();

    jr.beginObject();
    while( jr.hasNext() ) {
      switch( jr.nextName() ) {
      case TIMECODE:
        this.timeCode = jr.nextLong();
        break;
      case PENALTIES:
        _fillArrayList(this.penalties, jr);
        break;
      case GATES:
        _fillArrayList(this.gates, jr);
        break;
      case CREWS:
        _fillArrayList(this.crews, jr);
        break;
      case DISCIPLINES:
        jr.beginArray();
        while( jr.hasNext() ) {
          this.disciplines.add(new Discipline(jr));
        }
        jr.endArray();
        break;
      default:
        break;
      }
    }
    jr.endObject();
  }

  public void saveSettings(SharedPreferences settings)
  {
    StringWriter w = new StringWriter();
    JsonWriter jw = new JsonWriter(w);
    SharedPreferences.Editor ed;

    try {
      saveJSON(jw);
    } catch( IOException e ) {
    }

    ed = settings.edit();
    ed.putString("RaceStatus", w.toString());
    ed.commit();
  }

  public void saveJSON(JsonWriter jw) throws IOException
  {
    jw.beginObject();
    jw.name(TIMECODE).value(this.timeCode);

    jw.name(GATES);
    jw.beginArray();
    for( int i = 0; i < this.gates.size(); i++ ) {
      jw.value(this.gates.get(i));
    }
    jw.beginArray();

    jw.name(PENALTIES);
    jw.beginArray();
    for( int i = 0; i < this.penalties.size(); i++ ) {
      jw.value(this.penalties.get(i));
    }
    jw.endArray();

    jw.name(CREWS);
    jw.beginArray();
    for( int i = 0; i < this.crews.size(); i++ ) {
      jw.value(this.crews.get(i));
    }
    jw.endArray();

    jw.name(DISCIPLINES);
    jw.beginArray();
    for( int i = 0; i < this.disciplines.size(); i++ ) {
      this.disciplines.get(i).toJSON(jw);
    }
    jw.endObject();
  }
}
