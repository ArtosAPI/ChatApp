package com.example.chatapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MessageCheckWorker extends Worker {
    private static final String TAG = "MessageCheckWorker";

    public MessageCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseDatabase.getInstance().getReference("chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            // Проверяем новые сообщения
                            if (hasNewMessages(chatSnapshot)) {
                                sendNotification();
                                break;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Ошибка: " + error.getMessage());
                    }
                });

        return Result.success();
    }

    private boolean hasNewMessages(DataSnapshot chatSnapshot) {
        // Получаем время последней проверки
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        long lastCheckTime = prefs.getLong("last_msg_check", 0);

        // Проверяем все сообщения в чате
        for (DataSnapshot messageSnapshot : chatSnapshot.child("messages").getChildren()) {
            Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
            if (timestamp != null && timestamp > lastCheckTime) {
                // Обновляем время последней проверки
                prefs.edit().putLong("last_msg_check", System.currentTimeMillis()).apply();
                return true;
            }
        }
        return false;
    }

    private void sendNotification() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Создаем канал для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "messages_channel",
                    "New Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(channel);
        }

        // Строим уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "messages_channel")
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Новое сообщение")
                .setContentText("Вам пришло новое сообщение!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // Показываем уведомление
        int notificationId = (int) System.currentTimeMillis();
        manager.notify(notificationId, builder.build());
    }
}