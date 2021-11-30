package com.android.exsell.UI;

import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.exsell.R;
import com.android.exsell.adapters.MessageAdapter;
import com.android.exsell.models.Message;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class PrivateMessage extends AppCompatActivity {
    private static final String TAG = "PrivateMessage";

    private ArrayList<Message> messageArrayList;
    MessageAdapter adapter;
    private RecyclerView recyclerView;

    FirebaseAuth mAuth;

    TextView contactName;

    EditText newMessage;

    String messageId;

    private ImageView profilePic;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.private_message);

        recyclerView = findViewById(R.id.private_message);

        newMessage = findViewById(R.id.new_chat_message);

        profilePic = (ImageView) findViewById(R.id.profile_pic);

        Bundle extras = getIntent().getExtras();
        messageId = extras.get("messageId").toString();
        String name = extras.get("name").toString();
        String imageUri = new String();
        if(extras.get("imageUri") != null)
            imageUri = extras.get("imageUri").toString();
        name = name.substring(0, 1).toUpperCase() + name.substring(1);

        contactName = findViewById(R.id.contact_name);
        contactName.setText(name);

        if(imageUri != null && imageUri.length() > 0) {
            Picasso.get().load(imageUri).fit().into(profilePic);
        }

        getMessages();

        setAdapter();
    }

    private void setAdapter() {
        Log.i(TAG, "setAdapter");
        adapter = new MessageAdapter(messageArrayList);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    public void getMessages() {
        Log.i(TAG, "getMessages");
        messageArrayList = new ArrayList<>();
        FirebaseFirestore.getInstance().collection("messages").document(messageId)
                .collection("messages").orderBy("timeStamp")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value,
                                        @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e);
                            return;
                        }
                        for (QueryDocumentSnapshot doc : value) {
                            if(doc.exists()) {
                                Message message = new Message();

                                message.setMessage(doc.getString("message"));
                                message.setSender(doc.getString("sender"));

                                Calendar calendar = Calendar.getInstance();
                                Map<String, Object> map = (Map<String, Object>) doc.get("timeStamp");
                                calendar.setTime(((Timestamp) map.get("time")).toDate());
                                message.setTimeStamp(calendar);

                                boolean found = false;
                                for(Message m : messageArrayList) {
                                    if(m.isSame(message)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if(!found) {
                                    messageArrayList.add(message);
                                    adapter.notifyItemInserted(messageArrayList.size() - 1);
                                    recyclerView.scrollToPosition(messageArrayList.size() - 1);
                                }
                            }
                        }
                    }
                });
    }

    public void sendMessage(View view) {
        Log.i(TAG, "sendMessage");
        String message = newMessage.getText().toString();
        if (!message.isEmpty()) {
            createMessage(message, null);
            newMessage.setText("");
        }
    }

    public void createMessage(String m, String mid) {
        Log.i(TAG, "createMessage " +mid);
        mAuth = FirebaseAuth.getInstance();
        String messageid = mid == null ? messageId : mid;
//      Add message to database
        Message message = new Message();
        message.setMessage(m);
        message.setSender(mAuth.getCurrentUser().getUid());
        message.setTimeStamp(Calendar.getInstance());

        FirebaseFirestore.getInstance().collection("messages").document(messageid)
                .collection("messages").document().set(message);

//      Update the most recent message in database
        Map<String, Object> preview = new HashMap<>();
        preview.put("previewMessage", m);
        preview.put("previewTimeStamp", Calendar.getInstance());
        preview.put("messageId", messageid);

        FirebaseFirestore.getInstance().collection("messages").document(messageid).set(preview);

//      Scroll to the bottom of the chat
        if(mid == null) {
            recyclerView.scrollToPosition(messageArrayList.size() - 1);
        }
    }

    public void onClick(View view) {
        Intent intent = new Intent(this, MessagePreviews.class);
        startActivity(intent);
    }

    public void createThread(String selfUid, String otherUid, String selfName, String otherName) {
        String newThreadId;
        if (selfUid.compareTo(otherUid) < 0) {
            newThreadId = otherUid + selfUid;
        } else {
            newThreadId = selfUid + otherUid;
        }
        Map<String, Object> selfEntry = new HashMap<>();
        Map<String, Object> otherEntry = new HashMap<>();

        selfEntry.put("messageId", newThreadId);
        otherEntry.put("messageId", newThreadId);

        selfEntry.put("otherName", otherName);
        otherEntry.put("otherName", selfName);

        // TODO implement profilePic query
        selfEntry.put("otherPic", "");
        otherEntry.put("otherPic", "");

        FirebaseFirestore.getInstance().collection("Users").document(selfUid)
                .collection("messages").document(newThreadId).set(selfEntry);
        FirebaseFirestore.getInstance().collection("Users").document(otherUid)
                .collection("messages").document(newThreadId).set(otherEntry);
    }
}