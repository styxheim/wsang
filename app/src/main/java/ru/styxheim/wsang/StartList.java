package ru.styxheim.wsang;

import java.util.ArrayList;
import java.util.Iterator;

import android.util.*;
import android.content.*;

import java.io.*;

public class StartList implements Iterable<StartRow> {
  private ArrayList<StartRow> Rows = new ArrayList<StartRow>();
  private int genId = 0;
  private String _FILE_NAME = "_list_start.txt";
  /* for safe write: write to this file and rename to FILE_NAME */
  private String _FILE_NAME_NEW = "_list_start_write.txt";
  private String _FILE_NAME_OLD = "_list_start_old.txt";

  private String FILE_NAME;
  private String FILE_NAME_NEW;
  private String FILE_NAME_OLD;

  public StartList() {
    setOutput(null);
  }

  public void setOutput(String file) {
    if (file != null) {
      FILE_NAME = file;
      FILE_NAME_NEW = file + ".new";
      FILE_NAME_OLD = file + ".old";
    } else {
      FILE_NAME = _FILE_NAME;
      FILE_NAME_OLD = _FILE_NAME_OLD;
      FILE_NAME_NEW = _FILE_NAME_NEW;
    }
  }


  @Override
  public Iterator<StartRow> iterator() {
    return Rows.iterator();
  }

  public void removeRecord(int rowId) {
    for (StartRow row : Rows) {
      if (row.getRowId() == rowId) {
        Rows.remove(row);
        break;
      }
    }
  }

  /* Add new record to list
   */
  public StartRow addRecord(int crewId, int lapId, int disciplineId) {
    StartRow row = new StartRow(genId);
    genId++;
    row.setIdentify(crewId, lapId, disciplineId);
    Rows.add(row);
    return row;
  }

  /* Add parsed record to array
   */
  public StartRow addRecord(StartRow row) {
    if (row.getRowId() >= genId) {
      genId = row.getRowId() + 1;
    }
    Rows.add(row);
    Log.d("wsa-ng", String.format("load record id#%d -> %s",
        row.getRowId(),
        row.toString()));
    return row;
  }

  public StartRow addRecord(StartRow.SyncData data) {
    final int rowId;
    StartRow row;

    if (data.rowId != null && data.rowId >= genId) {
      genId = data.rowId + 1;
      rowId = data.rowId;
    } else {
      rowId = genId;
      genId++;
    }

    row = new StartRow(rowId);
    row.update(data);
    Log.d("wsa-ng", "load record id from SyncData: " + Integer.toString(rowId));
    Rows.add(row);
    return row;
  }

  /* Get record by Id
   * @param rowId when -1 return last row or null
   */
  public StartRow getRecord(int rowId) {
    if (rowId != -1) {
      for (StartRow row : Rows) {
        if (row.getRowId() == rowId)
          return row;
      }
    } else if (Rows.size() > 0) {
      return Rows.get(Rows.size() - 1);
    }

    return null;
  }

  public void saveJSON(JsonWriter jw) throws IOException {
    jw.beginArray();
    for (StartRow row : Rows) {
      row.saveJSON(jw);
    }
    jw.endArray();
  }

  /* Save data to file
   */
  public void Save(Context ctx) {
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);

    Log.d("wsa-ng", "save to " + FILE_NAME);

    try {
      jw.setIndent("  ");
      saveJSON(jw);
    } catch (Exception e) {
      e.printStackTrace();
      /* TODO: print exception to UI */
      return;
    }

    String json = sw.toString();

    Log.d("wsa-ng", "save to " + FILE_NAME_NEW);

    try {
      FileOutputStream fos;

      if (FILE_NAME_NEW.startsWith("/")) {
        fos = new FileOutputStream(new File(FILE_NAME_NEW));
      } else {
        fos = ctx.openFileOutput(FILE_NAME_NEW, Context.MODE_PRIVATE);
      }

      OutputStreamWriter ow = new OutputStreamWriter(fos);
      ow.write(json);
      ow.close();
    } catch (Exception e) {
      Log.e("wsa-ng", "write failed: " + e.getMessage());
      /* TODO: print exception to UI */
      return;
    }

    Log.d("wsa-ng", "delete " + FILE_NAME_OLD);

    if (FILE_NAME_OLD.startsWith("/")) {
      (new File(FILE_NAME_OLD)).delete();
    } else {
      ctx.deleteFile(FILE_NAME_OLD);
    }

    File f;
    File p = ctx.getFilesDir();

    Log.d("wsa-ng", "Rename " + FILE_NAME + " to " + FILE_NAME_OLD);

    try {
      f = ctx.getFileStreamPath(FILE_NAME);
      if (f != null)
        f.renameTo(new File(p, FILE_NAME_OLD));
    } catch (Exception e) {
      Log.e("wsa-ng", "Cannot rename " + FILE_NAME + " to " + FILE_NAME_OLD + ": " + e.getMessage());
    }

    Log.d("wsa-ng", "Rename " + FILE_NAME_NEW + " to " + FILE_NAME);

    try {
      if (FILE_NAME_NEW.startsWith("/")) {
        f = new File(FILE_NAME_NEW);
        f.renameTo(new File(FILE_NAME));
      } else {
        f = ctx.getFileStreamPath(FILE_NAME_NEW);
        f.renameTo(new File(p, FILE_NAME));
      }
    } catch (Exception e) {
      Log.e("wsa-ng", "rename failed: " + e.getMessage());
      /* TODO: print exception to UI */
      return;
    }
    Log.d("wsa-ng", "save to " + FILE_NAME + " is ok");
  }

  public void Load(Context ctx) {
    Log.d("wsa-ng", "load from " + FILE_NAME);
    this.Flush();
    try {
      FileInputStream fos;

      if (FILE_NAME.startsWith("/")) {
        fos = new FileInputStream(new File(FILE_NAME));
      } else {
        fos = ctx.openFileInput(FILE_NAME);
      }
      InputStreamReader ir = new InputStreamReader(fos);
      JsonReader jr = new JsonReader(ir);

      jr.beginArray();

      while (jr.hasNext()) {
        StartRow row = new StartRow(-1);
        row.loadJSON(jr);
        addRecord(row);
      }

      jr.endArray();
    } catch (Exception e) {
      this.Flush();
      Log.e("wsa-ng", "load from " + FILE_NAME + " failed: " + e.getMessage());
    }
    Log.d("wsa-ng", "load from " + FILE_NAME + " is ok");
  }

  public void Flush() {
    Rows.clear();
    genId = 0;
  }
}
