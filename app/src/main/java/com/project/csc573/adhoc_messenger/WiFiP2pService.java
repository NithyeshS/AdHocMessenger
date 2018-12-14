package com.project.csc573.adhoc_messenger;

import android.net.wifi.p2p.WifiP2pDevice;

/**
 * A structure to hold service information.
 */
public class WiFiP2pService {
//    public WifiP2pDevice device;
    public String deviceName = null;
    public String deviceAddress = null;
    public int deviceStatus = -1;
    public Boolean isDirect = null;
    public String nextHopAddress = null;
    public String nextHopName = null;
    public String instanceName = null;
}
