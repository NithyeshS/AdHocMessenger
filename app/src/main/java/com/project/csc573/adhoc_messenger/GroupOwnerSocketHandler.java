package com.project.csc573.adhoc_messenger;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The implementation of a ServerSocket handler. This is used by the wifi p2p
 * group owner.
 */
public class GroupOwnerSocketHandler extends Thread {

    ServerSocket socket = null;
    private final int THREAD_COUNT = 10;
    private Handler handler;
    private static final String TAG = "adhocm-GOSocketHandler";

    public GroupOwnerSocketHandler(Handler handler) throws IOException {
        try {
            socket = new ServerSocket(4545);
            this.handler = handler;
            Log.d(TAG, "Initiated GO socket");
        } catch (IOException e) {
            Log.e(TAG,"IOException throwed by socket", e);
            pool.shutdownNow();
//            throw e;
        }

    }

    /**
     * A ThreadPool for client sockets.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    /**
     * Method to close the group owner sockets and kill this entire thread.
     */
    public void closeSocketAndKillThisThread() {
        if(socket!=null && !socket.isClosed()) {
            try {
//                ChatManager.isRunning = false;
                socket.close();
                Log.d(TAG, "GO----ISCLOSED: " + socket.isClosed());
            } catch (IOException e) {
                Log.e(TAG, "IOException during close Socket", e);
            }
            pool.shutdownNow();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // A blocking operation. Initiate a ChatManager instance when
                // there is a new connection
                if(socket!=null && !socket.isClosed()) {
                    Socket clientSocket = socket.accept(); //because now i'm connected with the client/peer device
                    pool.execute(new ChatManager(clientSocket, handler));
                    Log.d(TAG, "Launching the I/O handler");
                }
            } catch (IOException e) {
                //if there is an exception, after closing socket and pool, the execution stops with a "break".
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ioe) {
                    Log.e(TAG, "IOException during close Socket", ioe);
                }
                pool.shutdownNow();
                break; //stop the while(true).
            }
        }
    }

}
