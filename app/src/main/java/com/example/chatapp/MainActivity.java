package com.example.chatapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef, chatsRef;
    private String currentUserId;
    private ListView chatsListView;
    private List<Chat> chatList = new ArrayList<>();
    private ArrayAdapter<Chat> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FirebaseApp.initializeApp(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // Если пользователь не авторизован, переходим на экран входа
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");

        chatsListView = findViewById(R.id.chatsListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatList);
        chatsListView.setAdapter(adapter);

        // Загрузка чатов пользователя
        loadUserChats();

        // Обработка выбора чата
        chatsListView.setOnItemClickListener((parent, view, position, id) -> {
            Chat chat = chatList.get(position);
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chatId", chat.chatId);
            startActivity(intent);
        });

        chatsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Chat chat = chatList.get(position);
            showDeleteChatDialog(chat);
            return true;
        });

        // Кнопка для поиска пользователя по email и создания чата
        Button addChatBtn = findViewById(R.id.addChatBtn);
        addChatBtn.setOnClickListener(v -> showAddChatDialog());

        // Кнопка выхода
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        scheduleMessageCheck();
    }

    private void scheduleMessageCheck() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                MessageCheckWorker.class,
                3, // Интервал в минутах
                TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(this).enqueue(workRequest);
    }

    private void showDeleteChatDialog(Chat chat) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить чат?")
                .setMessage("Чат будет удален у обоих участников")
                .setPositiveButton("Удалить", (dialog, which) -> deleteChat(chat))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteChat(Chat chat) {
        chatsRef.child(chat.chatId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    usersRef.child(currentUserId).child("chats").child(chat.chatId).removeValue();

                    chatsRef.child(chat.chatId).child("members").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            List<String> members = new ArrayList<>();
                            for (DataSnapshot memberSnap : snapshot.getChildren()) {
                                members.add(memberSnap.getValue(String.class));
                            }

                            // Добавлены проверки на размер списка
                            if (members.size() == 2) {
                                String otherUserId = members.get(0).equals(currentUserId) ? members.get(1) : members.get(0);
                                usersRef.child(otherUserId).child("chats").child(chat.chatId).removeValue();
                            } else {
                                Log.e("DeleteChat", "Invalid members count: " + members.size());
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

                    chatList.remove(chat);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Чат удален", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserChats() {
        usersRef.child(currentUserId).child("chats").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatList.clear();
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    // Загружаем чат по ID
                    loadChat(chatId);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadChat(String chatId) {
        chatsRef.child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String member1 = snapshot.child("members").child("0").getValue(String.class);
                String member2 = snapshot.child("members").child("1").getValue(String.class);
                String otherUserId = member1.equals(currentUserId) ? member2 : member1;

                usersRef.child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                        String nickname = userSnapshot.child("nickname").getValue(String.class);
                        chatList.add(new Chat(chatId, nickname));
                        adapter.notifyDataSetChanged();

                        // Добавляем слушатель для новых сообщений
                        DatabaseReference messagesRef = chatsRef.child(chatId).child("messages");
                        messagesRef.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                                if (!ChatAppApplication.isAppInForeground()) {
                                    showNotification("Новое сообщение", "Вам пришло сообщение!");
                                }
                            }
                            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
                            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
                            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "default", "Messages", NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "default")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showAddChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите email пользователя");
        final EditText emailEt = new EditText(this);
        builder.setView(emailEt);
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String email = emailEt.getText().toString();
            addChatWithEmail(email);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void addChatWithEmail(String email) {
        Log.d("FindUser", "Ищем пользователя по email: " + email);
        // Поиск пользователя по email
        usersRef.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d("FindUser", "snapshot.exists(): " + snapshot.exists());
                        if (snapshot.exists()) {
                            Log.d("AddChat", "snapshot.exists(): " + snapshot.exists());
                            for (DataSnapshot userSnap : snapshot.getChildren()) {
                                String userId = userSnap.getKey();
                                createChat(userId);
                            }
                        } else {
                            Log.d("AddChat", "Пользователь с email " + email + " не найден");
                            Toast.makeText(MainActivity.this, "Пользователь не найден", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FindUser", "Ошибка поиска: " + error.getMessage());
                    }
                });
    }

    private void createChat(String otherUserId) {
        String chatId = chatsRef.push().getKey();
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("members", Arrays.asList(currentUserId, otherUserId));
        chatsRef.child(chatId).setValue(chatData).addOnSuccessListener(aVoid -> {
            // Добавляем чат в оба списка пользователей
            usersRef.child(currentUserId).child("chats").child(chatId).setValue(true);
            usersRef.child(otherUserId).child("chats").child(chatId).setValue(true);
        });
    }

    // Класс для отображения чатов
    public static class Chat {
        String chatId;
        String displayName;
        public Chat(String chatId, String displayName) {
            this.chatId = chatId;
            this.displayName = displayName;
        }
        @Override
        public String toString() {
            return displayName;
        }
    }
}