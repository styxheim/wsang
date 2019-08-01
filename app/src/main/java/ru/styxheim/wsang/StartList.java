package ru.styxheim.wsang;

import java.util.ArrayList;
import java.util.Iterator;
import android.util.*;
import android.content.*;
import java.io.*;

public class StartList implements Iterable<StartRow>
{
  private ArrayList<StartRow> Rows;
  private int genId = 0;
  final private String FILE_NAME = "_list_start.txt";
  /* for safe write: write to this file and rename to FILE_NAME */
  final private String FILE_NAME_NEW = "_list_start_write.txt";
  final private String FILE_NAME_OLD = "_list_start_old.txt";

  public StartList()
  {
    Rows = new ArrayList<StartRow>();
  }

  @Override
  public Iterator<StartRow> iterator() {
    return Rows.iterator();
  }

  /* Add new record to list
   */
  public StartRow addRecord(int crewId, int lapId)
  {
    StartRow row = new StartRow(genId);
    genId++;
    row.crewId = crewId;
    row.lapId = lapId;
    Rows.add(row);
    return row;
  }

  /* Add parsed record to array
   */
  private StartRow addRecord(StartRow row)
  {
    if( row.getRowId() >= genId ) {
      genId = row.getRowId() + 1;
    }
    Rows.add(row);
    Log.d("wsa-ng", "load record id: " + Integer.toString(row.getRowId()));
    return row;
  }

  /* Get record by Id
   * @param rowId when -1 return last row or null
   */
  public StartRow getRecord(int rowId)
  {
    if( rowId != -1 ) {
      for( StartRow row : Rows ) {
        if( row.getRowId() == rowId )
          return row;
      }
    }
    else if( Rows.size() > 0 ) {
      return Rows.get(Rows.size() - 1);
    }

    return null;
  }

  /* Save data to file
   */
  public void Save(Context ctx)
  {
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);

    Log.d("wsa-ng", "save to " + FILE_NAME);

    try {
      jw.beginArray();
      for( StartRow row: Rows ) {
        row.saveJSON(jw, true /* system save */);
      }
      jw.endArray();
    } catch( Exception e ) {
      e.printStackTrace();
      /* TODO: print exception to UI */
      return;
    }

    String json = sw.toString();

    try {
      FileOutputStream fos = ctx.openFileOutput(FILE_NAME_NEW, Context.MODE_PRIVATE);

      OutputStreamWriter ow = new OutputStreamWriter(fos);
      ow.write(json);
      ow.close();
    } catch( Exception e ) {
      e.printStackTrace();
      /* TODO: print exception to UI */
      return;
    }

    ctx.deleteFile(FILE_NAME_OLD);

    File f;
    File p = ctx.getFilesDir();

    try {
      f = ctx.getFileStreamPath(FILE_NAME);
      if( f != null )
        f.renameTo(new File(p, FILE_NAME_OLD));
    } catch( Exception e ) {
      Log.e("wsa-ng", "Cannot rename " + FILE_NAME + " to " + FILE_NAME_OLD);
    }

    try {
      f = ctx.getFileStreamPath(FILE_NAME_NEW);
      f.renameTo(new File(p, FILE_NAME));
    } catch( Exception e ) {
      e.printStackTrace();
      /* TODO: print exception to UI */
      return;
    }
    Log.d("wsa-ng", "save to " + FILE_NAME + " is ok");
  }

  public void Load(Context ctx)
  {
    Log.d("wsa-ng", "load from " + FILE_NAME);
    this.Flush();
    try {
      FileInputStream fos = ctx.openFileInput(FILE_NAME);
      InputStreamReader ir = new InputStreamReader(fos);
      JsonReader jr = new JsonReader(ir);

      jr.beginArray();

      while( jr.hasNext() ) {
        StartRow row = new StartRow(-1);
        row.loadJSON(jr, true /* system */);
        addRecord(row);
      }

      jr.endArray();
    } catch( Exception e ) {
      this.Flush();
      e.printStackTrace();
    }
    Log.d("wsa-ng", "load from " + FILE_NAME + " is ok");
  }

  public void Flush()
  {
    Rows.clear();
    genId = 0;
  }
}
