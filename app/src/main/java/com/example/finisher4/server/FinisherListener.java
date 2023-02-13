package com.example.finisher4.server;

import java.util.Set;

public interface FinisherListener {

    void onSensorEvent(String host, int count);
    void serverState(boolean b, Set<String> hosts);

}
