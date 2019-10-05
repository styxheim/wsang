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
  public String terminalId = "";
  public ArrayList<Discipline> disciplines = new ArrayList<Discipline>();
  protected long timestamp = 0;

  final static String CLASS_NAME = "TerminalStatus";
  final static String DISCIPLINES = "Disciplines";

  final static String TERMINAL_ID = "TerminalId";
  final static String GATES = "Gates";

  public Discipline getDiscipline(int disciplineId)
  {
    for( Discipline d : disciplines ) {
      if( d.id == disciplineId ) {
        return d;
      }
    }

    return getDiscipline();
  }

  public Discipline getDiscipline()
  {
    if( disciplines.size() != 0 ) {
      return disciplines.get(0);
    }
    // return invalid discipline
    return new Discipline();
  }

  public String toString()
  {
    return String.format("<TerminlStatus id=%s, timestamp=%d, disciplines=%d>",
                         terminalId, timestamp, disciplines.size());
  }

  public TerminalStatus()
  {
  }

  public TerminalStatus(SharedPreferences settings)
  {
    String jsonString = settings.getString("TerminalStatus", "");
    StringReader r = new StringReader(jsonString);
    JsonReader jr = new JsonReader(r);

    if( jsonString.length() == 0 ) {
      Log.i("wsa-ng", "[TerminalStatus] load empty from settings");
      return;
    }

    try {
      loadJSON(jr);
    } catch( IOException e ) {
    }
  }

  public TerminalStatus(JsonReader jr) throws IOException, IllegalStateException
  {
    loadJSON(jr);
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
    ed.putString("TerminalStatus", w.toString());
    ed.commit();
  }

  public void saveJSON(JsonWriter jw) throws IOException
  {
    jw.beginObject();
    jw.name(TERMINAL_ID).value(this.terminalId);
    jw.name(RaceStatus.TIMESTAMP).value(this.timestamp);

    jw.name(DISCIPLINES);
    jw.beginArray();
    for( int i = 0; i < this.disciplines.size(); i++ ) {
      this.disciplines.get(i).toJSON(jw);
    }
    jw.endArray();
    jw.endObject();
  }

  public void loadJSON(JsonReader jr) throws IOException, IllegalStateException
  {
    this.disciplines.clear();
    this.terminalId = "";

    if( !jr.hasNext() )
      return;

    jr.beginObject();
    while( jr.hasNext() ) {
      switch( jr.nextName() ) {
      case RaceStatus.TIMESTAMP:
        this.timestamp = jr.nextLong();
        break;
      case TERMINAL_ID:
        this.terminalId = jr.nextString();
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
      }
    }
    jr.endObject();
  }

  public class Discipline
  {
    public int id = -1;
    public ArrayList<Integer> gates = new ArrayList<Integer>();

    public boolean startGate = false;
    public boolean finishGate = false;

    public Discipline()
    {
    }

    public Discipline(JsonReader jr) throws IOException, IllegalStateException
    {
      this.fromJSON(jr);
    }

    public void fromJSON(JsonReader jr) throws IOException, IllegalStateException
    {
      this.id = 0;
      this.gates.clear();
      this.startGate = false;
      this.finishGate = false;

      if( !jr.hasNext() )
        return;

      jr.beginObject();
      while( jr.hasNext() ) {
        switch( jr.nextName() ) {
        case RaceStatus.ID:
          this.id = jr.nextInt();
          break;
        case GATES:
          jr.beginArray();
          while( jr.hasNext() ) {
            int gate = jr.nextInt();
            if( gate == RaceStatus.GATE_START )
              this.startGate = true;
            else if( gate == RaceStatus.GATE_FINISH )
              this.finishGate = true;
            else
              this.gates.add(gate);
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
      jw.name(RaceStatus.ID).value(this.id);
      if( this.gates.size() != 0 || this.startGate || this.finishGate ) {
        jw.name(GATES);
        jw.beginArray();
        for( int gate : gates ) {
          jw.value(gate);
        }
        if( this.startGate )
          jw.value(RaceStatus.GATE_START);
        if( this.finishGate )
          jw.value(RaceStatus.GATE_FINISH);
        jw.endArray();
      }
      jw.endObject();
    }
  }

}
