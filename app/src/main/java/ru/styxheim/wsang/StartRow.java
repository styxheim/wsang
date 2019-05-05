package ru.styxheim.wsang;

import java.io.IOException;
import android.util.*;

public class StartRow
{
  private int rowId;

  public int crewId;
  public int lapId;
  public String startAt;

  /* UI settings */
  public boolean visible;

  public StartRow(int rowId)
  {
    this.rowId = rowId;
  }

  public int getRowId()
  {
    return rowId;
  }

  public void saveJSON(JsonWriter w) throws IOException
  {
    w.beginObject();
    w.name("id").value(this.rowId);
    w.name("crewId").value(this.crewId);
    w.name("lapId").value(this.lapId);
    w.name("start_time").value(this.startAt);
    w.endObject();
  }

  public void loadJSON(JsonReader r) throws IOException
  {
    r.beginObject();
    while( r.hasNext() ) {
      switch( r.nextName() ) {
      case "id":
        this.rowId = r.nextInt();
        break;
      case "crewId":
        this.crewId = r.nextInt();
        break;
      case "lapId":
        this.lapId = r.nextInt();
        break;
      case "start_time":
        this.startAt = r.nextString();
        break;
      default:
        r.skipValue();
      }
    }
    r.endObject();
  }
}

