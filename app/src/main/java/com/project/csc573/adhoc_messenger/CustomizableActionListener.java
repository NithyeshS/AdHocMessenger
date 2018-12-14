package com.project.csc573.adhoc_messenger;

/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;


public class CustomizableActionListener implements WifiP2pManager.ActionListener {

    private final Context context;
    private final String successLog, successToast, failLog, failToast, tag;



    public CustomizableActionListener(@NonNull Context context,
                                      String tag,
                                      String successLog, String successToast,
                                      String failLog, String failToast) {
        this.context = context;
        this.successLog = successLog;
        this.successToast = successToast;
        this.failLog = failLog;
        this.failToast = failToast;

        if(tag==null) {
            this.tag = "ActionListenerTag";
        } else {
            this.tag = tag;
        }
    }

    @Override
    public void onSuccess() {
        if(successLog != null) {
            Log.d(tag, successLog);
        }
        if(context!=null && successToast != null) {
            Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFailure(int reason) {
        if(failLog != null) {
            Log.d(tag, failLog + ", reason: " + reason);
        }
        if(context!=null && failToast != null) {
            Toast.makeText(context, failToast, Toast.LENGTH_SHORT).show();
        }
    }
}
