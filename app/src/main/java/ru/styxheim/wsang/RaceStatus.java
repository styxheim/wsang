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
  final static String CLASS_NAME = "RaceStatus";

  final static int INVALID_GATE = -1;
  final static int GATE_START = -2;
  final static int GATE_FINISH = -3;

  final static String COMPETITION_ID = "CompetitionId";
  final static String TIMESTAMP = "TimeStamp";
  final static String SYNCPOINT = "SyncPoint";
  final static String CREWS = "Crews";
  final static String GATES = "Gates";
  final static String PENALTIES = "Penalties";
  final static String DISCIPLINES = "Disciplines";
  final static String ID = "Id";
  final static String NAME = "Name";

  public long syncPoint;
  public long competitionId;
  public long timestamp;
  public ArrayList<Integer> gates = new ArrayList<Integer>(Arrays.asList(GATE_START, GATE_FINISH));
  public ArrayList<Integer> penalties = new ArrayList<Integer>(Arrays.asList(0));
  public ArrayList<Integer> crews = new ArrayList<Integer>();
  public ArrayList<Discipline> disciplines = new ArrayList<Discipline>();

  public class Discipline
  {
    public Integer id;
    public String name;
    public ArrayList<Integer> gates = new ArrayList<Integer>();

    public Discipline(JsonReader jr) throws IOException, IllegalStateException
    {
      this.fromJSON(jr);
    }

    public void fromJSON(JsonReader jr) throws IOException, IllegalStateException
    {
      this.id = null;
      this.name = null;
      this.gates.clear();

      if( !jr.hasNext() )
        return;

      jr.beginObject();
      while( jr.hasNext() ) {
        switch( jr.nextName() ) {
        case NAME:
          this.name = jr.nextString();
          break;
        case ID:
          this.id = jr.nextInt();
          break;
        case GATES:
          jr.beginArray();
          while( jr.hasNext() ) {
           this.gates.add(jr.nextInt());
          }
          jr.endArray();
          break;
        default:
          jr.skipValue();
          break;
        }
      }
      jr.endObject();
    }

    public void toJSON(JsonWriter jw) throws IOException
    {
      jw.beginObject();
      if( this.id != null )
        jw.name(ID).value(this.id);
      if( this.name != null )
        jw.name(NAME).value(this.name);
      if( this.gates.size() != 0 ) {
        jw.name(GATES);
        jw.beginArray();
        for( int gate : this.gates ) {
          jw.value(gate);
        }
        jw.endArray();
      }
      jw.endObject();
    }
  }

  public RaceStatus()
  {
  }

  public RaceStatus(SharedPreferences settings)
  {
    String jsonString = settings.getString("RaceStatus", "");
    StringReader r = new StringReader(jsonString);
    JsonReader jr = new JsonReader(r);

    if( jsonString.length() == 0 ) {
      Log.i("wsa-ng", "[RaceStatus] load empty from settings");
      return;
    }

    try {
      loadJSON(jr);
    } catch( Exception e ) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);

      e.printStackTrace(pw);
      Log.e("wsa-ng",
            String.format("[RaceStatus] load error: %s ->\n%s->\n'%s'",
                          e.getMessage(),
                          sw.toString(),
                          jsonString
                          ));
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

    if( !jr.hasNext() )
      return;

    jr.beginObject();
    while( jr.hasNext() ) {
      switch( jr.nextName() ) {
      case COMPETITION_ID:
        this.competitionId = jr.nextLong();
        break;
      case SYNCPOINT:
        this.syncPoint = jr.nextLong();
        break;
      case TIMESTAMP:
        this.timestamp = jr.nextLong();
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
        jr.skipValue();
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

    jw.name(COMPETITION_ID).value(this.competitionId);
    jw.name(TIMESTAMP).value(this.timestamp);
    jw.name(SYNCPOINT).value(this.syncPoint);

    jw.name(GATES);
    jw.beginArray();
    for( int i = 0; i < this.gates.size(); i++ ) {
      jw.value(this.gates.get(i));
    }
    jw.endArray();

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
    jw.endArray();
    jw.endObject();
  }
}
