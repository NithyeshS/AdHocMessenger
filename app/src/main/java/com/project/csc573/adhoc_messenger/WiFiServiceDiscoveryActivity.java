package com.project.csc573.adhoc_messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.app.FragmentManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

//import com.project.csc573.adhoc_messenger.WiFiChatFragment.MessageTarget;
import com.project.csc573.adhoc_messenger.WiFiDirectServicesList.DeviceClickListener;
import com.project.csc573.adhoc_messenger.WiFiDirectServicesList.WiFiDevicesAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main activity for the sample. This activity registers a local service and
 * perform discovery over Wi-Fi p2p network. It also hosts a couple of fragments
 * to manage chat operations. When the app is launched, the device publishes a
 * chat service and also tries to discover services published by other peers. On
 * selecting a peer published service, the app initiates a Wi-Fi P2P (Direct)
 * connection with the peer. On successful connection with a peer advertising
 * the same service, the app opens up sockets to initiate a chat.
 * {@code WiFiChatFragment} is then added to the the main activity which manages
 * the interface and messaging needs for a chat session.
 */
public class WiFiServiceDiscoveryActivity extends Activity implements
        DeviceClickListener, Handler.Callback, /*MessageTarget,*/
        ConnectionInfoListener {

    public static final String TAG = "adhocmessenger-main";

    private List<String> statusLog = new ArrayList<>();
    private static final int MAX_STATUS_LINES = 12;

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE_DIRECT = "CSC573PROJECT";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    public static final String SERVICE_INSTANCE_INDIRECT = "CSC573PHASE2";
    public static final String TXTRECORD_INDIRECT_KEY = "AVAILABLEPEERS";
    private WifiP2pDnsSdServiceInfo mIndirectService=null;
    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;
    public static  final int MESSAGE_DISCONNECT = 0x400 + 3;
    private WifiP2pManager manager;

    private final long SERVICE_BROADCASTING_INTERVAL = 30000;
    private final long SERVICE_DISCOVERING_INTERVAL = 20000;
    static final int SERVER_PORT = 4545;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    private WifiP2pDnsSdServiceRequest serviceRequest;

    private Boolean isGroupOwner=false;

    private Thread socketHandler;
    final Handler mServiceBroadcastingHandler = new Handler();
    final Handler mServiceDiscoveringHandler = new Handler();
    private WiFiChatFragment chatFragment;
    private WiFiDirectServicesList servicesList;

    private ChatManager chatManager;
    private AlertDialog.Builder waitDialogBuilder = null;
    private AlertDialog waitDialog = null;
    private TextView statusTxtView;

    public static String deviceName="";
    public static String deviceAddress="";

    public boolean isInitiatingConnection = false;
    public String connectedDeviceName="";

    private Handler handler = new Handler(this);

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // NITHYESHS - Hack to allow sending messages in main thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        statusTxtView = findViewById(R.id.status_text);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        prepareServiceDiscovery();

        startBroadcastingService();

//        startServiceDiscovery();

        waitDialogBuilder = new AlertDialog.Builder(this);
        // set title
        waitDialogBuilder.setTitle("Please wait");

// set dialog message
        waitDialogBuilder.setMessage("Connecting to device...").setCancelable(false);

// create alert dialog
        waitDialog = waitDialogBuilder.create();

        servicesList = new WiFiDirectServicesList();
        getFragmentManager().beginTransaction()
                .add(R.id.container_root, servicesList, "services").commit();

        getFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    public void onBackStackChanged() {

                        if(getFragmentManager().getBackStackEntryCount() == 0) {
                            // Update your UI here.
                            Log.d(WiFiServiceDiscoveryActivity.TAG, "IN BACK AC LISTENER");
                            statusTxtView.setVisibility(View.VISIBLE);

                            // Disconnect from device if we are the GO
                            if(isGroupOwner) {
                                manager.removeGroup(channel, new ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        isInitiatingConnection = false;
                                        appendStatus("Disconnected from peer");
                                        Log.d(WiFiServiceDiscoveryActivity.TAG, "Disconnected " +
                                                "from peer");
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        appendStatus("Failed to disconnect from peer");
                                        Log.d(WiFiServiceDiscoveryActivity.TAG, "Failed to " +
                                                "disconnect from group. Reason :" + reason);
                                    }
                                });
                            }

                            // We are coming back from a chat. Clear peers list model and view.
                            // The next iteration of service discovery will repopulate these.

                            ServiceList.getInstance().clear();
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                                    .findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
                                        .getListAdapter());
                                adapter.clear();
                            }
                        }
                    }
                });
    }

    @Override
    protected void onRestart() {
        Fragment frag = getFragmentManager().findFragmentByTag("services");
        if (frag != null) {
            getFragmentManager().beginTransaction().remove(frag).commit();
        }
        super.onRestart();
    }

    @Override
    protected void onStop() {
        if (manager != null && channel != null) {
            closeChatSocket();
            manager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    isInitiatingConnection = false;
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                }

            });
            manager.removeLocalService(channel, mIndirectService, new ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to remove local service. Reason :" + reason);
                }
            });
            manager.removeServiceRequest(channel, serviceRequest, new ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to remove service request. Reason :" + reason);
                }
            });
            manager.stopPeerDiscovery(channel, new ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to stop peer. Reason :" + reason);
                }
            });

        }
        super.onStop();
    }


    public void startBroadcastingService(){
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Cleared local services");

                Map<String, String> record = new HashMap<String, String>();
                record.put(TXTRECORD_PROP_AVAILABLE, "visible");
                WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                        SERVICE_INSTANCE_DIRECT, SERVICE_REG_TYPE, record);
                manager.addLocalService(channel, service,
                        new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                // service broadcasting started
                                Log.d(TAG, "Added a local service");
                                appendStatus("Added local service");
                                mServiceBroadcastingHandler
                                        .post(mServiceBroadcastingRunnable);
                            }

                            @Override
                            public void onFailure(int error) {
                                // react to failure of adding the local service
                                appendStatus("Failed to add local service");
                            }
                        });

                String directPeersList = ""; //ServiceList.getInstance().getDeviceNames();
                Map<String, String> record1 = new HashMap<>();
                record.put(TXTRECORD_INDIRECT_KEY, directPeersList);
                mIndirectService = WifiP2pDnsSdServiceInfo.newInstance(
                        SERVICE_INSTANCE_INDIRECT, SERVICE_REG_TYPE, record1);
                manager.addLocalService(channel, mIndirectService, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Added empty direct peers list in service");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "Failed to add empty direct peers list in service. " +
                                "Reason " + reason);
                    }
                });
            }

            @Override
            public void onFailure(int error) {
                // react to failure of clearing the local services
                appendStatus("Failed to clear local service");

            }
        });
    }

    private Runnable mServiceBroadcastingRunnable = new Runnable() {
        @Override
        public void run() {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendStatus("Started peer discovery");
                }

                @Override
                public void onFailure(int error) {
                    appendStatus("Failed to start peer discovery");
                }
            });

            startServiceDiscovery();
            mServiceBroadcastingHandler
                    .postDelayed(mServiceBroadcastingRunnable, SERVICE_BROADCASTING_INTERVAL);
        }
    };

    public void prepareServiceDiscovery() {
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {
                        // A service has been discovered. Is this our app?
                        Log.d(TAG, "Service has been discovered");

                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE_DIRECT)) {

                            // update the UI and add the item the discovered device.
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                                    .findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
                                        .getListAdapter());
                                WiFiP2pService service = new WiFiP2pService();
                                service.device=srcDevice;
                                service.instanceName=instanceName;
                                service.serviceRegistrationType=registrationType;

                                Log.d(WiFiServiceDiscoveryActivity.TAG,
                                        srcDevice.deviceName + " was discovered");

                                if(ServiceList.getInstance().addServiceIfNotPresent(service)) {
                                    adapter.add(service);
                                    adapter.notifyDataSetChanged();
                                    Log.d(TAG, "onBonjourServiceAvailable "
                                            + instanceName);
                                }
                                if(ServiceList.getInstance().updateExistingService(service)) {
                                    adapter.clear();
                                    int serviceListSize = ServiceList.getInstance().getSize();
                                    for (int i=0; i < serviceListSize; i++) {
                                        adapter.add(ServiceList.getInstance().getElementByPosition(i));
                                    }
                                    adapter.notifyDataSetChanged();
                                    Log.d(TAG, "onBonjourServiceUpdated "
                                            + instanceName);

                                }
                                updateIndirectDiscoveryServiceBroadcast();

                            }
                        }
                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE_INDIRECT)) {
                            // We are getting direct peer updates from some other device
                            appendStatus("Discovered an indirect peers update service from " + srcDevice.deviceName);

                        }
                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {

                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        if( fullDomainName.contains(SERVICE_INSTANCE_DIRECT.toLowerCase())) {
                            Log.d(TAG, fullDomainName + " --- " + device.deviceName + " is "
                                    + record.get(TXTRECORD_PROP_AVAILABLE));
                        }

                        if(fullDomainName.contains(SERVICE_INSTANCE_INDIRECT.toLowerCase())) {
                            Log.d(TAG, fullDomainName + " --- " + device.deviceName
                                    + " - direct peers -  "
                                    + record.get(TXTRECORD_INDIRECT_KEY));
                            updateRouteTable(device.deviceAddress, record.get(TXTRECORD_INDIRECT_KEY));
                        }
                    }
                });

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
    }

    private void startServiceDiscovery() {
        manager.removeServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        appendStatus("Removed service requests");
                        manager.addServiceRequest(channel, serviceRequest,
                                new WifiP2pManager.ActionListener() {

                                    @Override
                                    public void onSuccess() {
                                        appendStatus("Added service request");
                                        manager.discoverServices(channel,
                                                new WifiP2pManager.ActionListener() {

                                                    @Override
                                                    public void onSuccess() {
                                                        appendStatus("Started service discovery");
                                                        //service discovery started

//                                                        mServiceDiscoveringHandler.postDelayed(
//                                                                mServiceDiscoveringRunnable,
//                                                                SERVICE_DISCOVERING_INTERVAL);
                                                    }

                                                    @Override
                                                    public void onFailure(int error) {
                                                        appendStatus("Failed to start service discovery");
                                                        // react to failure of starting service discovery
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onFailure(int error) {
                                        appendStatus("Failed to add new service request");
                                        // react to failure of adding service request
                                    }
                                });
                    }

                    @Override
                    public void onFailure(int reason) {
                        appendStatus("Failed to clear service requests");
                        // react to failure of removing service request
                    }
                });
    }

    public void updateIndirectDiscoveryServiceBroadcast() {
        if (mIndirectService != null) {
            manager.removeLocalService(channel, mIndirectService, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Removed existing service for indirect discovery");

                    String directPeersList = ServiceList.getInstance().getDeviceNames();
                    Map<String, String> record = new HashMap<>();
                    record.put(TXTRECORD_INDIRECT_KEY, directPeersList);
                    mIndirectService = WifiP2pDnsSdServiceInfo.newInstance(
                            SERVICE_INSTANCE_INDIRECT, SERVICE_REG_TYPE, record);
                    manager.addLocalService(channel, mIndirectService, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Added direct peers list in service");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(TAG, "Failed to add  direct peers list in service. " +
                                    "Reason " + reason);
                        }
                    });
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to remove existing service for indirect discovery." +
                            " Reason " + reason);
                }
            });
        }
    }

    public void updateRouteTable(String nextHopMAC, String devicesList) {

    }

    @Override
    public void connectP2p(final WiFiP2pService service) {
        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        connectedDeviceName=service.device.deviceName;
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest,
                    new ActionListener() {

                        @Override
                        public void onSuccess() {
                            waitDialog.show();
                            manager.connect(channel, config, new ActionListener() {

                                @Override
                                public void onSuccess() {
                                    isInitiatingConnection = true;
                                    appendStatus("Connecting to service on " + connectedDeviceName);
                                }

                                @Override
                                public void onFailure(int errorCode) {
                                    connectedDeviceName="";
                                    isInitiatingConnection = false;
                                    waitDialog.dismiss();
                                    Toast.makeText(WiFiServiceDiscoveryActivity.this,
                                            "FAILED TO CONNECT. PLEASE TRY AGAIN IN 30 " +
                                                    "SECONDS.", Toast.LENGTH_LONG);
                                    appendStatus("Failed connecting to service. REASON: " + errorCode);
                                }
                            });
                        }

                        @Override
                        public void onFailure(int arg0) {
                            appendStatus("Failed to remove service request. Will not attempt to " +
                                    "reconnect");
                        }
                    });
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                appendStatus("Received message from peer - " + readMessage);

                // Check if this is a DISCONNECT msg
                if(readMessage.equalsIgnoreCase("DISCONNECT")) {
                    // Disconnect from device if we are the GO
                    isInitiatingConnection = false;
                    if (isGroupOwner) {
                        closeChatSocket();
                        manager.removeGroup(channel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                appendStatus("Disconnected from peer");
                            }

                            @Override
                            public void onFailure(int reason) {
                                appendStatus("Failed to disconnect from peer");
                            }
                        });
                    }
                } else {
                    // Its a data msg from the peer
                    String[] data = readMessage.split("#=#");
                    Log.d(TAG, "DATA: " + data.toString() + "---" + data.length);
                    Log.d(TAG, data[0] + ": " + data[data.length - 1]);
//                (chatFragment).echoMsg(readMessage);
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Message from " + data[0] + "-" + data[1]);
                    builder.setMessage(data[data.length - 1]);

                    // Set up the buttons
                    builder.setPositiveButton("Reply", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AlertDialog.Builder builder1 = new AlertDialog.Builder(WiFiServiceDiscoveryActivity.this);
                            builder1.setTitle("Connected to " + connectedDeviceName);

                            // Set up the input
                            final EditText input1 = new EditText(WiFiServiceDiscoveryActivity.this);

                            input1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                            input1.setSingleLine(false);
//                            input1.setLines(5);
//                            input1.setMaxLines(5);
                            input1.setGravity(Gravity.START);
                            builder1.setView(input1);

                            // Set up the buttons
                            builder1.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (chatManager != null) {
                                        Log.d(TAG, "Writing msg to ChatManager. Msg is " +
                                                input1.getText().toString());
                                        String messageStr = deviceName + "#=#" + deviceAddress + "#=#" +
                                                input1.getText().toString();
                                        chatManager.write(messageStr.getBytes());
                                        dialog.dismiss();
                                        appendStatus("Message sent");
                                    }
                                }
                            });
                            builder1.setNegativeButton("Disconnect", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    // Disconnect from device if we are the GO
                                    isInitiatingConnection = false;
                                    if (isGroupOwner) {
                                        closeChatSocket();
                                        manager.removeGroup(channel, new ActionListener() {
                                            @Override
                                            public void onSuccess() {
                                                appendStatus("Disconnected from peer");
                                            }

                                            @Override
                                            public void onFailure(int reason) {
                                                appendStatus("Failed to disconnect from peer");
                                            }
                                        });
                                    } else {
                                        appendStatus("Asking peer to disconnect as we are connected as a client");
                                        String disconnectStr = "DISCONNECT";
                                        chatManager.write(disconnectStr.getBytes());
                                        closeChatSocket();
                                    }

                                    dialog.cancel();
                                }
                            });

                            builder1.show();
                        }
                    });
                    builder.setNegativeButton("Disconnect", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            // Disconnect from device if we are the GO
                            isInitiatingConnection = false;
                            if (isGroupOwner) {
                                closeChatSocket();
                                manager.removeGroup(channel, new ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        appendStatus("Disconnected from peer");
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        appendStatus("Failed to disconnect from peer");
                                    }
                                });
                            } else {
                                appendStatus("Asking peer to disconnect as we are connected as a client");
                                String disconnectStr = "DISCONNECT";
                                chatManager.write(disconnectStr.getBytes());
                                closeChatSocket();
                            }

                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
                break;

            case MY_HANDLE:
                Object obj = msg.obj;
                chatManager = (ChatManager) obj;

        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */

        if (p2pInfo.isGroupOwner) {
            Log.d(TAG, "Connected as group owner");
            isGroupOwner=true;
            try {
                socketHandler = new GroupOwnerSocketHandler(this.getHandler());
                socketHandler.start();
            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else {
            Log.d(TAG, "Connected as peer");
            isGroupOwner=false;
            socketHandler = new ClientSocketHandler(this.getHandler(),
                    p2pInfo.groupOwnerAddress);
            socketHandler.start();
        }
        if(waitDialog.isShowing())
            waitDialog.dismiss();

        if (isInitiatingConnection) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Connected to " + connectedDeviceName);

            // Set up the input
            final EditText input = new EditText(this);

            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
//            input.setLines(5);
//            input.setMaxLines(5);
            input.setGravity(Gravity.START);
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (chatManager != null) {
                        Log.d(TAG,"Writing msg to ChatManager. Msg is " +
                                input.getText().toString());
                        String messageStr = deviceName + "#=#" + deviceAddress + "#=#" +
                                input.getText().toString();
                        chatManager.write(messageStr.getBytes());
                        dialog.dismiss();
                        Log.d(TAG, "Sending message - " + messageStr);
                        appendStatus("Message sent");
                    }
                }
            });
            builder.setNegativeButton("Disconnect", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    // Disconnect from device if we are the GO
                    isInitiatingConnection = false;
                    if(isGroupOwner) {
                        closeChatSocket();
                        manager.removeGroup(channel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                appendStatus("Disconnected from peer");
                            }

                            @Override
                            public void onFailure(int reason) {
                                appendStatus("Failed to disconnect from peer");
                            }
                        });
                    }
                    else {
                        appendStatus("Asking peer to disconnect as we are connected as a client");
                        String disconnectStr = "DISCONNECT";
                        chatManager.write(disconnectStr.getBytes());
                        closeChatSocket();
                    }

                    dialog.cancel();
                }
            });

            builder.show();
        }


        /*chatFragment = new WiFiChatFragment();
        getFragmentManager().beginTransaction()
                .replace(R.id.container_root, chatFragment)
                .addToBackStack(null).commit();*/
//        statusTxtView.setVisibility(View.GONE);
    }

    public void closeChatSocket() {
        if(waitDialog.isShowing())
            waitDialog.dismiss();
        isInitiatingConnection = false;
//        ChatManager.isRunning= false;
        if (socketHandler instanceof GroupOwnerSocketHandler) {
            ((GroupOwnerSocketHandler) socketHandler).closeSocketAndKillThisThread();
        } else if (socketHandler instanceof ClientSocketHandler) {
            ((ClientSocketHandler) socketHandler).closeSocketAndKillThisThread();
        }
    }

    public void appendStatus(String status) {
        Log.d(TAG,status);
        if (status.length() > 0) {
            statusLog.add(status) ;
        }
        // remove the first line if log is too large
        if (statusLog.size() >= MAX_STATUS_LINES) {
            statusLog.remove(0);
        }

        String log = "";
        for (String str : statusLog) {
            log += str + "\n";
        }
        statusTxtView.setText(log);
    }
}
