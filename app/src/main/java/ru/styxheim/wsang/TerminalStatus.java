package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.content.SharedPreferences;
import android.util.*;
import android.util.Log;
import java.util.*;
import java.io.IOException;
import java.io.*;

public class TerminalStatus
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

    public Discipline(String serialized)
    {
      StringReader r = new StringReader(serialized);
      JsonReader jr = new JsonReader(r);
      try {
        this.fromJSON(jr);
      } catch( IOException e ) {
      }
    }
    
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

    public String toString()
    {
      StringWriter w = new StringWriter();
      JsonWriter jw = new JsonWriter(w);
      try {
        this.toJSON(jw);
      } catch( IOException e ) {
      }
      return w.toString();
    }
  }

  public TerminalStatus(SharedPreferences settings)
  {
    int c = 0;
 
    this.timeCode = settings.getLong("time_code", 0);

    c = settings.getInt("gates", 0);
    for( int i = 0; i < c; i++ ) {
      String name = "gate_" + Integer.toString(i);
      this.gates.add(new Integer(settings.getInt(name, 0)));
    }

    c = settings.getInt("penalties", 0);
    for( int i = 0; i < c; i++ ) {
      String name = "penalty_" + Integer.toString(i);
      this.penalties.add(new Integer(settings.getInt(name, 0)));
    }

    c = settings.getInt("crews", 0);
    for( int i = 0; i < c; i++ ) {
      String name = "crew_" + Integer.toString(i);
      this.crews.add(new Integer(settings.getInt(name, 0)));
    }

    c = settings.getInt("disciplines", 0);
    for( int i = 0; i < c; i++ ) {
      String name = "discipline_" + Integer.toString(i);
      this.disciplines.add(new Discipline(settings.getString(name, "")));
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

  public TerminalStatus(JsonReader jr) throws IOException
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
    SharedPreferences.Editor ed;
 
    ed = settings.edit();
    ed.clear();

    ed.putLong("time_code", this.timeCode);

    ed.putInt("gates", this.gates.size());
    for( int i = 0; i < this.gates.size(); i++ ) {
      String name = "gate_" + Integer.toString(i);
      ed.putInt(name, this.gates.get(i));
    }

    ed.putInt("penalties", this.penalties.size());
    for( int i = 0; i < this.penalties.size(); i++ ) {
      String name = "penalty_" + Integer.toString(i);
      ed.putInt(name, this.penalties.get(i));
    }

    ed.putInt("crews", this.crews.size());
    for( int i = 0; i < this.crews.size(); i++ ) {
      String name = "crew_" + Integer.toString(i);
      ed.putInt(name, this.crews.get(i));
    }

    ed.putInt("disciplines", this.disciplines.size());
    for( int i = 0; i < this.disciplines.size(); i++ ) {
      String name = "discipline_" + Integer.toString(i);
      ed.putString(name, this.disciplines.get(i).toString());
    }

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
