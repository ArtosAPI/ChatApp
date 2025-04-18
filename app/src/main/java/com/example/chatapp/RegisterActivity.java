package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {
    private EditText emailEt, passwordEt, nicknameEt;
    private Button registerBtn;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private Button goToLoginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        emailEt = findViewById(R.id.emailEt);
        passwordEt = findViewById(R.id.passwordEt);
        nicknameEt = findViewById(R.id.nicknameEt);
        registerBtn = findViewById(R.id.registerBtn);
        goToLoginBtn = findViewById(R.id.goToLoginBtn);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        goToLoginBtn.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // закрывает текущий экран
        });
        registerBtn.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String email = emailEt.getText().toString();
        String password = passwordEt.getText().toString();
        String nickname = nicknameEt.getText().toString();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        // Сохраняем никнейм и email
                        usersRef.child(userId).setValue(new User(email, nickname))
                                .addOnSuccessListener(aVoid -> {
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                });
                    } else {
                        Toast.makeText(this, "Ошибка регистрации", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}