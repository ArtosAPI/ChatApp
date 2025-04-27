package com.example.chatapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {
    private String chatId;
    private String currentUserId;
    private DatabaseReference messagesRef, usersRef;
    private ListView messagesLv;
    private EditText messageEt;
    private Button sendBtn;
    private List<Message> messageList = new ArrayList<>();
    private MessageAdapter adapter;
    private static final int PICK_IMAGE = 1;
    private Button btnAttach;
    private Message selectedMessage;
    private int selectedPosition;

    @Override
    protected void onResume() {
        super.onResume();
        markMessagesAsRead();
    }

    private void markMessagesAsRead() {
        for (Message message : messageList) {
            if (!message.senderId.equals(currentUserId) && !"READ".equals(message.status)) {
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
        btnAttach = findViewById(R.id.btnAttach);

        setupAdapter();
        loadMessages();
        setupListViewLongClick();

        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });
        sendBtn.setOnClickListener(v -> sendMessage());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                sendImage(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendImage(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        String imageBase64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);

        usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                String messageId = messagesRef.push().getKey();
                Message message = new Message(
                        currentUserId,
                        user.nickname,
                        System.currentTimeMillis(),
                        imageBase64
                );
                message.isImage = true;
                messagesRef.child(messageId).setValue(message);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupListViewLongClick() {
        messagesLv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= messageList.size()) return false;

                selectedMessage = messageList.get(position);
                if (selectedMessage == null || !selectedMessage.senderId.equals(currentUserId)) {
                    return false;
                }

                showPopupMenu(view);
                return true;
            }
        });
    }

    private void showPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.message_context_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            if (selectedMessage == null) return false;

            int id = item.getItemId();
            if (id == R.id.menu_edit) {
                showEditDialog();
                return true;
            } else if (id == R.id.menu_delete) {
                showDeleteDialog();
                return true;
            }
            return false;
        });

        popup.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.message_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedMessage == null) return false;

        if (item.getItemId() == R.id.menu_edit) {
            showEditDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_delete) {
            showDeleteDialog();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактировать сообщение");

        final EditText input = new EditText(this);
        input.setText(selectedMessage.text);
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty() && !newText.equals(selectedMessage.text)) {
                updateMessage(newText);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void updateMessage(String newText) {
        messagesRef.child(selectedMessage.messageId).child("text").setValue(newText);
        messagesRef.child(selectedMessage.messageId).child("edited").setValue(true);
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить сообщение?")
                .setMessage("Это действие нельзя отменить")
                .setPositiveButton("Удалить", (dialog, which) -> deleteMessage())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteMessage() {
        messagesRef.child(selectedMessage.messageId).child("deleted").setValue(true);
    }

    private void loadMessages() {
        messagesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message msg = snapshot.getValue(Message.class);
                msg.messageId = snapshot.getKey();
                messageList.add(msg);
                adapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
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

    private void scrollToBottom() {
        messagesLv.post(() -> messagesLv.smoothScrollToPosition(messageList.size() - 1));
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(this, messageList, currentUserId);
        messagesLv.setAdapter(adapter);
    }

    private void sendMessage() {
        String text = messageEt.getText().toString().trim();
        if (!text.isEmpty()) {
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
                    message.status = "DELIVERED";
                    messagesRef.child(messageId).setValue(message)
                            .addOnSuccessListener(aVoid -> messageEt.setText(""));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }

    public static class Message {
        public String messageId;
        public String senderId;
        public String senderNickname;
        public String text;
        public long timestamp;
        public String status;
        public boolean edited;
        public boolean deleted;
        public String imageBase64 = "";
        public boolean isImage = false;

        public Message() {}

        public Message(String senderId, String senderNickname, String text, long timestamp) {
            this.senderId = senderId;
            this.senderNickname = senderNickname;
            this.text = text;
            this.timestamp = timestamp;
            this.status = "DELIVERED";
            this.edited = false;
            this.deleted = false;
            this.isImage = false;
        }

        public Message(String senderId, String senderNickname, long timestamp, String imageBase64) {
            this.senderId = senderId;
            this.senderNickname = senderNickname;
            this.imageBase64 = imageBase64;
            this.timestamp = timestamp;
            this.isImage = true;
            this.status = "DELIVERED";
        }
    }
}