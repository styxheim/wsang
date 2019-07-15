package ru.styxheim.wsang;

public class EventMessage
{
  public boolean dataSynced = false;
  public boolean newData = false;

  public EventMessage(boolean dataSynced, boolean newData)
  {
    this.dataSynced = dataSynced;
    this.newData = newData;
  }
}
