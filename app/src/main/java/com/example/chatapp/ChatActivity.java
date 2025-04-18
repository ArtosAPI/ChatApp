package com.example.chatapp;

import android.content.Context;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {
    private String chatId;
    private String currentUserId;
    private String globalMessageId;
    private DatabaseReference messagesRef, usersRef;
    private ListView messagesLv;
    private EditText messageEt;
    private Button sendBtn;
    private List<Message> messageList = new ArrayList<>();
    private ArrayAdapter<Message> adapter;

    @Override
    protected void onResume() {
        super.onResume();
        // Помечаем сообщения собеседника как "READ"
        for (Message message : messageList) {
            if (!message.senderId.equals(currentUserId) && !"READ".equals(message.status)) {
                // Используем message.messageId
                messagesRef.child(message.messageId).child("status").setValue("READ");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatId = getIntent().getStringExtra("chatId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        messagesRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        messagesLv = findViewById(R.id.messagesLv);
        messageEt = findViewById(R.id.messageEt);
        sendBtn = findViewById(R.id.sendBtn);

        setupAdapter();

        loadMessages();

        sendBtn.setOnClickListener(v -> sendMessage());

        // Уведомления о новых сообщениях
        // Можно реализовать через Firebase Cloud Messaging или локальные уведомления
    }

    private void loadMessages() {
        messagesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message msg = snapshot.getValue(Message.class);
                msg.messageId = snapshot.getKey(); // Сохраняем ID сообщения
                messageList.add(msg);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                // Обработка изменений статуса
                Message updatedMsg = snapshot.getValue(Message.class);
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).messageId.equals(updatedMsg.messageId)) {
                        messageList.set(i, updatedMsg);
                        adapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(this, messageList, currentUserId);
        messagesLv.setAdapter(adapter);
    }

    private void sendMessage() {
        String text = messageEt.getText().toString();
        if (!text.isEmpty()) {
            // Получаем никнейм текущего пользователя из БД
            usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    String messageId = messagesRef.push().getKey();
                    Message message = new Message(
                            currentUserId,
                            user.nickname,
                            text,
                            System.currentTimeMillis()
                    );
                    message.status = "SENT";
                    messagesRef.child(messageId).setValue(message)
                            .addOnSuccessListener(aVoid -> {
                                messagesRef.child(messageId).child("status").setValue("DELIVERED");
                                messageEt.setText("");
                            });
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    public static class Message {
        public String messageId;
        public String senderId;
        public String senderNickname;
        public String text;
        public long timestamp;
        public String status = "SENT"; // "SENT", "DELIVERED", "READ"

        public Message() {}

        public Message(String senderId, String senderNickname, String text, long timestamp) {
            this.senderId = senderId;
            this.senderNickname = senderNickname;
            this.text = text;
            this.timestamp = timestamp;
            this.status = "SENT";
        }

        @Override
        public String toString() {
            return String.format("%s: %s\n%s",
                    senderId, // добавить это поле в класс
                    text,
                    new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(timestamp));
        }
    }
}