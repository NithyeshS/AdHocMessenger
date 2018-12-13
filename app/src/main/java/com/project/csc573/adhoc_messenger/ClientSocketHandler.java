package com.project.csc573.adhoc_messenger;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientSocketHandler extends Thread {

    private static final String TAG = "adhocm-CliSocketHandler";
    private Handler handler;
    private ChatManager chat;
    private InetAddress mAddress;
    private Socket socket;

    public ClientSocketHandler(Handler handler, InetAddress groupOwnerAddress) {
        Log.d(TAG, "Initiated client socket");
        this.handler = handler;
        this.mAddress = groupOwnerAddress;
    }

    @Override
    public void run() {
        ChatManager chat;
        socket = new Socket();
        try {
            socket.bind(null);
            socket.connect(new InetSocketAddress(mAddress.getHostAddress(),
                    4545), 5000);
            Log.d(TAG, "Launching the I/O handler");
            chat = new ChatManager(socket, handler);
            new Thread(chat).start();
        } catch (IOException e) {
            Log.e(TAG,"IOException throwed by socket", e);
            try {
                socket.close();
            } catch (IOException e1) {
                Log.e(TAG,"IOException during close Socket" , e1);
            }
        }
    }



    /**
     * Method to close the client/peer socket and kill this entire thread.
     */
    public void closeSocketAndKillThisThread() {
        if(socket!=null && !socket.isClosed()) {
            try {
//                ChatManager.isRunning = false;
                socket.close();
            } catch (IOException e) {
                Log.e(TAG,"IOException during close Socket" , e);
            }
        }

        Log.d(TAG, "CLI----ISCLOSED: " + socket.isClosed() + " - ISCONNECTED: " + socket.isConnected());

        //to interrupt this thread, without the threadpoolexecutor
        if(!this.isInterrupted()) {
            Log.d(TAG,"Stopping ClientSocketHandler");
            this.interrupt();
        }
    }

}
