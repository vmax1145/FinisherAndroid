package com.example.finisher4;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.finisher4.server.FinisherListener;
import com.example.finisher4.server.FinisherServer;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements FinisherListener {

    volatile private long startedAt = System.currentTimeMillis();
    volatile  private long stoppedAt = System.currentTimeMillis();

    volatile private String startedBy = "";
    volatile private String stoppedBy = "";



    private ListView list;
    private ArrayList<String> listItems=new ArrayList<>();

    //DEFINING A STRING ADAPTER WHICH WILL HANDLE THE DATA OF THE LISTVIEW
    private ArrayAdapter<String> adapter;

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(
                new Intent(getApplicationContext(),FinisherServer.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) {
            // Get the previous state of the stopwatch
            // if the activity has been
            // destroyed and recreated.

            startedAt  = savedInstanceState
                    .getLong("startedAt");
            stoppedAt  = savedInstanceState
                    .getLong("stoppedAt");
            startedBy = savedInstanceState.getString("startedBy");
            stoppedBy = savedInstanceState.getString("stoppedBy");
            listItems = savedInstanceState.getStringArrayList("storedResults");
        }
        list = (ListView) findViewById(R.id.listView);
        adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                listItems);
        list.setAdapter(adapter);

        statusView =findViewById(R.id.status_view);
        statusView.setBackgroundColor(0x990000);
        runTimer();
    }


    // Save the state of the stopwatch
    // if it's about to be destroyed.
    @Override
    public void onSaveInstanceState(
            @NotNull Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState
                .putLong("startedAt",startedAt);
        savedInstanceState
                .putLong("stoppedAt",stoppedAt);
        savedInstanceState.putString("startedBy",startedBy);
        savedInstanceState.putString("stoppedBy",stoppedBy);
        savedInstanceState.putStringArrayList("storedResults",listItems);
    }





    synchronized public void onClickReset(View view) {
       if(stoppedBy.length() > 0) {
           String time = convertMillisToString(stoppedAt - startedAt);
           adapter.add(time);
       }
       startedBy = "";
       stoppedBy = "";
    }

    // Sets the NUmber of seconds on the timer.
    // The runTimer() method uses a Handler
    // to increment the seconds and
    // update the text view.
    private void runTimer()
    {

        // Get the text view.
        final TextView timeView
                = (TextView)findViewById(
                R.id.time_view);

        // Creates a new Handler
        final Handler handler = new Handler();

        // Call the post() method,
        // passing in a new Runnable.
        // The post() method processes
        // code without a delay,
        // so the code in the Runnable
        // will run almost immediately.
        handler.post(new Runnable() {
            @Override

            public void run()
            {
                long milliseconds = 0;
                if(startedBy.length() > 0) {
                    if(stoppedBy.length() > 0) {
                        milliseconds = stoppedAt - startedAt;
                    }
                    else {
                        milliseconds = System.currentTimeMillis()-startedAt;
                    }
                }

                String time = convertMillisToString(milliseconds);

                // Set the text view text.
                timeView.setText(time);

                // Post the code again
                // with a delay of 1 second.
                handler.postDelayed(this, 10);
            }
        });
    }

    @NonNull
    private String convertMillisToString(long milliseconds) {
        int minutes = (int) ((milliseconds % 3600000) / 60000);
        int secs = (int) ((milliseconds % 60000)/1000);
        int centiSecs = (int) (milliseconds %1000/10);

        // Format the seconds into hours, minutes,
        // and seconds.
        String time= String.format("%d:%02d.%02d", minutes,secs,centiSecs);
        return time;
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            FinisherServer.MyBinder binder = (FinisherServer.MyBinder) iBinder;
            FinisherServer server = binder.getService();
            server.addListener(MainActivity.this);
            List<String> ips = server.getIpAddress();

            getSupportActionBar().setTitle(TextUtils.join(", ",ips));



        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

            getSupportActionBar().setTitle("Service not started");
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.RED));
        }

    };




    @Override
    synchronized public void onSensorEvent(String host, int count) {
        if(startedBy.length()==0) {
            startedAt = System.currentTimeMillis();
            startedBy = host;
            Log.d("Finisher","started by "+host);
        }
        else if(stoppedBy.length()==0) {
            if(!startedBy.equals(host)) {
                stoppedAt = System.currentTimeMillis();
                stoppedBy = host;
                Log.d("Finisher","stopped by "+host);
            }
        }
    }

    @Override
    public void serverState(boolean b, Set<String> hosts) {
            if (b) {
                if(hosts.size()==2) {
                    statusView.setText("Sensors connected OK");
                    statusView.setBackgroundResource(R.color.ok);
                }
                else {
                    statusView.setText("Wait sensors to connect");
                    statusView.setBackgroundResource(R.color.warn);
                }
            } else {
                statusView.setText("Starting connections...");
                statusView.setBackgroundResource(R.color.error);
            }
            statusView.invalidate();
    }

    public void onClickClearResults(View view) {

        new AlertDialog.Builder(this)
                .setTitle("Clear All results")
                .setMessage("Do you really want to clear all table results?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        listItems.clear();
                        adapter.notifyDataSetChanged();
                        list.invalidate();
                    }})
                .setNegativeButton(android.R.string.no, null).show();
    }
}