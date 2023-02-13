package com.example.finisher4.server;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.Closeable;
import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.Nullable;

public class FinisherServer  extends Service implements Runnable {

    private final FinisherServer.MyBinder localBinder = new FinisherServer.MyBinder();

    private final int port = 3223;
    private ArrayList<FinisherListener> listeners = new ArrayList<>();
    volatile boolean running = true;

    private Set<String> connectedHosts = new HashSet<>();


    public FinisherServer() {
        for (FinisherListener l : listeners) {
            l.serverState(true, connectedHosts);
        }
    }

    public List<String> getIpAddress() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress
                            .nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        byte[] raw = inetAddress.getAddress();
                        if(raw.length==4) {
                            ips.add(ipToString(inetAddress.getAddress()));
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(getClass().getSimpleName(),"Error obtain IP",e);
        }
        return ips;
    }

    public void stop() {
        this.running = false;
    }


    @Override
    public void run() {
        ServerSocket socket = null;
        while (running) {
            try {
                socket = new ServerSocket(port,8,InetAddress.getByAddress(new byte[]{0,0,0,0}));
                for (FinisherListener l : listeners) {
                    l.serverState(true, new HashSet<>(connectedHosts));
                }
                while (running) {
                    Socket soc = socket.accept();
                    handleSocketAccepted(soc);
                }
            } catch (Exception e) {
                close(socket);
            }
            finally {
                for (FinisherListener l : listeners) {
                    l.serverState(false, new HashSet<>());
                }
            }
        }
    }


    private void handleSocketAccepted(Socket soc) {
        InetSocketAddress socketAddress = (InetSocketAddress) soc.getRemoteSocketAddress();
        String fromHost = socketAddress.getHostName();
        Log.d(this.getClass().getSimpleName(),"Socket accepted:"+socketAddress.getAddress());
        Thread th = new Thread( ()-> {

            try {
                soc.setSoTimeout(1000);
            }
            catch (Exception e){}

            try (DataInputStream is = new DataInputStream(soc.getInputStream())) {
                connectedHosts.add(fromHost);
                for (FinisherListener l : listeners) {
                   l.serverState(true, new HashSet<>(connectedHosts));
                }
                String line = null;
                while (running && (line = is.readLine()) != null) {
                    int cnt = Integer.parseInt(line.trim());
                    if(cnt>0) {
                        for (FinisherListener l : listeners) {
                            l.onSensorEvent(fromHost, cnt);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                connectedHosts.remove(fromHost);
                for (FinisherListener l : listeners) {
                    l.serverState(true, new HashSet<>(connectedHosts));
                }
            }

        });
        th.start();
    }

    private void close(Closeable c) {
        if(c==null) return;
        try {
            c.close();
        }
        catch (Exception ignored){}
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public void onCreate() {
        new Thread(this).start();
    }



    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }



    public void addListener(FinisherListener listener) {
        listeners.add(listener);
        listener.serverState(running, new HashSet<>(connectedHosts));
    }

    public class MyBinder extends Binder {
        public  FinisherServer getService() {
            return FinisherServer.this;
        }
    }


    private static String ipToString(byte[] rawBytes) {
        int i = 4;
        StringBuilder ipAddress = new StringBuilder();
        for (byte raw : rawBytes) {
            ipAddress.append(raw & 0xFF);
            if (--i > 0) {
                ipAddress.append(".");
            }
        }
        return ipAddress.toString();
    }
}
