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

        if (message.deleted) {
            return getDeletedView(convertView, parent);
        }

        int requiredLayoutType = message.senderId.equals(currentUserId) ? 1 : 0;

        if (convertView == null || getItemViewType(position) != requiredLayoutType) {
            convertView = createNewView(message, parent);
        }

        ViewHolder holder = (ViewHolder) convertView.getTag();
        setupMessageView(holder, message);

        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        ChatActivity.Message message = getItem(position);
        return message.senderId.equals(currentUserId) ? 1 : 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    private View getDeletedView(View convertView, ViewGroup parent) {
        if (convertView == null || convertView.getId() != R.id.tvDeleted) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.message_item_deleted, parent, false);
        }
        TextView tvDeleted = convertView.findViewById(R.id.tvDeleted);
        tvDeleted.setText("Сообщение удалено");
        return convertView;
    }

    private View createNewView(ChatActivity.Message message, ViewGroup parent) {
        View view;
        ViewHolder holder = new ViewHolder();

        if (message.senderId.equals(currentUserId)) {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.message_item_right, parent, false);
        } else {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.message_item_left, parent, false);
        }

        holder.tvMessage = view.findViewById(R.id.tvMessage);
        holder.tvTime = view.findViewById(R.id.tvTime);
        holder.tvStatus = view.findViewById(R.id.tvStatus);
        view.setTag(holder);

        return view;
    }

    private void setupMessageView(ViewHolder holder, ChatActivity.Message message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date(message.timestamp));

        String messageText = message.edited ? message.text + " (изменено)" : message.text;
        holder.tvMessage.setText(messageText);
        holder.tvTime.setText(time);

        if (message.senderId.equals(currentUserId)) {
            holder.tvStatus.setText(getStatusIcon(message.status));
            holder.tvStatus.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }
    }

    private String getStatusIcon(String status) {
        if (status == null) return "✓";
        switch (status) {
            case "DELIVERED": return "✓✓";
            case "READ": return "✓✓✓";
            default: return "✓";
        }
    }

    static class ViewHolder {
        TextView tvMessage;
        TextView tvTime;
        TextView tvStatus;
    }
}