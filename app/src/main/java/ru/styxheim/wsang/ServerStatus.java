package ru.styxheim.wsang;

import android.app.*;
import android.os.*;
import android.util.*;
import android.util.Log;

import java.util.*;
import java.io.IOException;
import java.io.*;

public class ServerStatus {
  public Error error;
  RaceStatus raceStatus;
  ArrayList<StartRow.SyncData> lap = new ArrayList<StartRow.SyncData>();
  ArrayList<TerminalStatus> terminalStatus = new ArrayList<TerminalStatus>();
  final static String ERROR = "Error";

  public static class Error {
    public String text;

    final static String TEXT = "Text";

    public Error() {
    }

    public Error(JsonReader jr) throws IOException {
      loadJSON(jr);
    }

    public void loadJSON(JsonReader jr) throws IOException, IllegalStateException {
      text = null;

      jr.beginObject();
      while (jr.hasNext()) {
        switch (jr.nextName()) {
          case TEXT:
            text = jr.nextString();
            break;
          default:
            jr.skipValue();
            break;
        }
      }
      jr.endObject();
    }
  }

  public ServerStatus() {
  }

  public ServerStatus(JsonReader jr) throws IOException {
    loadJSON(jr);
  }

  public void loadJSON(JsonReader jr) throws IOException, IllegalStateException {
    raceStatus = null;
    lap.clear();
    terminalStatus.clear();

    if (!jr.hasNext())
      return;

    jr.beginObject();
    while (jr.hasNext()) {
      switch (jr.nextName()) {
        case RaceStatus.CLASS_NAME:
          raceStatus = new RaceStatus(jr);
          break;
        case TerminalStatus.CLASS_NAME:
          jr.beginArray();
          while (jr.hasNext()) {
            terminalStatus.add(new TerminalStatus(jr));
          }
          jr.endArray();
          break;
        case StartRow.CLASS_NAME:
          jr.beginArray();
          while (jr.hasNext()) {
            lap.add(new StartRow.SyncData(jr));
          }
          jr.endArray();
          break;
        case ERROR:
          error = new Error(jr);
          break;
        default:
          jr.skipValue();
          break;
      }
    }
    jr.endObject();
  }

  public void saveJSON(JsonWriter jw) throws IOException {
    jw.beginObject();

    if (raceStatus != null) {
      jw.name(RaceStatus.CLASS_NAME);
      raceStatus.saveJSON(jw);
    }

    if (terminalStatus.size() != 0) {
      jw.name(TerminalStatus.CLASS_NAME);
      jw.beginArray();
      for (int i = 0; i < terminalStatus.size(); i++) {
        terminalStatus.get(i).saveJSON(jw);
      }
      jw.endArray();
    }

    if (lap.size() != 0) {
      jw.name(StartRow.CLASS_NAME);
      jw.beginArray();
      for (int i = 0; i < lap.size(); i++) {
        lap.get(i).toJSON(jw);
      }
      jw.endArray();
    }

    jw.endObject();
  }
}
