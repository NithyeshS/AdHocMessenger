package com.project.csc573.adhoc_messenger;


import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * This fragment handles chat related UI which includes a list view for messages
 * and a message entry field with send button.
 */
public class WiFiChatFragment extends Fragment {

    private View view;
    private ChatManager chatManager;
    private TextView chatLine;
    private ListView listView;
    ChatMessageAdapter adapter = null;
    private List<String> items = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_chat, container, false);

        // NITHYESHS - Hack to allow sending messages in main thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        chatLine =  view.findViewById(R.id.txtChatLine);
        listView =  view.findViewById(android.R.id.list);
        adapter = new ChatMessageAdapter(getActivity(), android.R.id.text1,
                items);
        listView.setAdapter(adapter);
        view.findViewById(R.id.button1).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View arg0) {
                        if (chatManager != null) {
                            Log.d(WiFiServiceDiscoveryActivity.TAG,
                                    "Writing msg to ChatManager. Msg is " +
                                            chatLine.getText().toString());
                            chatManager.write(chatLine.getText().toString()
                                    .getBytes());
                            pushMessage("Me: " + chatLine.getText().toString());
                            chatLine.setText("");
                        }
                    }
                });
        return view;
    }

    public void setChatManager(ChatManager obj) {
        chatManager = obj;
    }

    public void echoMsg(String msg) {
        if (chatManager != null) {
            try {
                Thread.sleep(1000);
                Log.d(WiFiServiceDiscoveryActivity.TAG,
                        "Writing msg to ChatManager. Msg is " +
                                msg);
                chatManager.write(msg.getBytes());
                pushMessage("Me: " + msg);
//                chatLine.setText("");
            }
            catch (InterruptedException ie) {
                Log.e(WiFiServiceDiscoveryActivity.TAG, "echoMsg() sleep thread was interrupted");
            }
        }
    }

    public void pushMessage(String readMessage) {
        adapter.add(readMessage);
        adapter.notifyDataSetChanged();
    }

    /**
     * ArrayAdapter to manage chat messages.
     */
    public class ChatMessageAdapter extends ArrayAdapter<String> {

        List<String> messages = null;

        public ChatMessageAdapter(Context context, int textViewResourceId,
                                  List<String> items) {
            super(context, textViewResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(android.R.layout.simple_list_item_1, null);
            }
            String message = items.get(position);
            if (message != null && !message.isEmpty()) {
                TextView nameText = v.findViewById(android.R.id.text1);

                if (nameText != null) {
                    nameText.setText(message);
                    if (message.startsWith("Me: ")) {
                        nameText.setTextAppearance(getActivity(),
                                R.style.normalText);
                    } else {
                        nameText.setTextAppearance(getActivity(),
                                R.style.boldText);
                    }
                }
            }
            return v;
        }
    }
}
