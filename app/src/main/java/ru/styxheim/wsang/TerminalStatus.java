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
  public ArrayList<Integer> gates = new ArrayList<Integer>();
  protected boolean finishGate = false;
  protected boolean startGate = false;
  protected long timestamp = 0;

  final static String CLASS_NAME = "TerminalStatus";

  final static String TERMINAL_ID = "TerminalId";
  final static String GATES = "Gates";
  final static int INVALID_GATE = -1;
  final static int FINISH_GATE = -3;
  final static int START_GATE = -2;

  public boolean hasFinishGate()
  {
    return this.finishGate;
  }

  public boolean hasStartGate()
  {
    return this.startGate;
  }

  public void addGate(int gate)
  {
    switch( gate ) {
    case START_GATE:
      this.startGate = true;
      break;
    case FINISH_GATE:
      this.finishGate = true;
      break;
    default:
      this.gates.add(gate);
      break;
    }
  }

  public TerminalStatus(SharedPreferences settings)
  {
    StringReader r = new StringReader(settings.getString("TerminalStatus", ""));
    JsonReader jr = new JsonReader(r);

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

    jw.name(GATES);
    jw.beginArray();
    for( int i = 0; i < this.gates.size(); i++ ) {
      jw.value(this.gates.get(i));
    }

    if( this.finishGate )
      jw.value(FINISH_GATE);
    if( this.startGate )
      jw.value(START_GATE);

    jw.endArray();

    jw.endObject();
  }

  public void loadJSON(JsonReader jr) throws IOException, IllegalStateException
  {
    this.gates.clear();
    this.finishGate = false;
    this.startGate = false;
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
      case GATES:
        jr.beginArray();
        while( jr.hasNext() ) {
          int gate = jr.nextInt();
          this.addGate(gate);
        }
        jr.endArray();
        break;
      default:
        jr.skipValue();
      }
    }
    jr.endObject();
  }
}
