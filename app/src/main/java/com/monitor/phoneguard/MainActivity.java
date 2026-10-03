package com.monitor.phoneguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText etDeviceNumber;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etDeviceNumber = findViewById(R.id.etDeviceNumber);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnStart = findViewById(R.id.btnStart);
        prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);

        String savedNum = prefs.getString("device_num", "");
        if (!savedNum.isEmpty()) {
            etDeviceNumber.setText(savedNum);
            tvStatus.setText("현재 감시 가동 중: " + savedNum + "번 폰");
        }

        btnStart.setOnClickListener(v -> {
            String num = etDeviceNumber.getText().toString().trim();
            if (num.isEmpty()) {
                Toast.makeText(this, "기기 번호를 입력하세요!", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("device_num", num).apply();
            Intent serviceIntent = new Intent(this, MonitorService.class);
            serviceIntent.putExtra("device_num", num);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            tvStatus.setText("상태: " + num + "번 실시간 관제 작동 중");
            Toast.makeText(this, num + "번 관제 서비스 시작됨", Toast.LENGTH_SHORT).show();
        });
    }
}
