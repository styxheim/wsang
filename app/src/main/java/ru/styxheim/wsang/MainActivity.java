package ru.styxheim.wsang;

import android.view.View;
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;

public class MainActivity extends Activity 
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start);
    }

	public void startOnClick(View v)
	{
		//TableLayout tl_crew = findViewById(R.id.start_table_crew);
		TableLayout tl_time = findViewById(R.id.start_table_time);
		LayoutInflater inflater = this.getLayoutInflater();
		//View tr_crew = inflater.inflate(R.layout.start_row_crew, null);
    View tr_time = inflater.inflate(R.layout.start_row_time, null);
		//tl_crew.addView(tr_crew);
		tl_time.addView(tr_time);
	}
}
