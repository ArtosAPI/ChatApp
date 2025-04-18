package com.example.chatapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends ArrayAdapter<ChatActivity.Message> {
    private String currentUserId;

    public MessageAdapter(Context context, List<ChatActivity.Message> messages, String currentUserId) {
        super(context, 0, messages);
        this.currentUserId = currentUserId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatActivity.Message message = getItem(position);
        if (message == null || message.status == null) {
            return convertView;
        }
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            if (message.senderId.equals(currentUserId)) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.message_item_right, parent, false);
            } else {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.message_item_left, parent, false);
            }
            holder.tvMessage = convertView.findViewById(R.id.tvMessage);
            holder.tvTime = convertView.findViewById(R.id.tvTime);
            holder.tvStatus = convertView.findViewById(R.id.tvStatus); // Добавьте эту строку
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Форматирование времени
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date(message.timestamp));

        holder.tvMessage.setText(message.text);
        holder.tvTime.setText(time);

        if (holder.tvStatus != null) {
            if (message.senderId.equals(currentUserId)) {
                holder.tvStatus.setText(getStatusIcon(message.status));
                holder.tvStatus.setVisibility(View.VISIBLE);
            } else {
                // Для чужих сообщений скрываем статус
                holder.tvStatus.setVisibility(View.GONE);
            }
        }

        return convertView;
    }

    private String getStatusIcon(String status) {
        if (status == null) {
            return "✓"; // Или другое значение по умолчанию
        }
        switch (status) {
            case "DELIVERED": return "✓✓";
            case "READ": return "✓✓\uD83D\uDD12";
            default: return "✓";
        }
    }

    static class ViewHolder {
        TextView tvMessage;
        TextView tvTime;
        TextView tvStatus;
    }
}