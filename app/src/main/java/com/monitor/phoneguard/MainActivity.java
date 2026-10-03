package com.monitor.phoneguard;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
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
            tvStatus.setText("?꾩옱 媛먯떆 媛??以? " + savedNum + "踰???);
        }

        btnStart.setOnClickListener(v -> {
            String num = etDeviceNumber.getText().toString().trim();
            if (num.isEmpty()) {
                Toast.makeText(this, "湲곌린 踰덊샇瑜??낅젰?섏꽭??", Toast.LENGTH_SHORT).show();
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
            tvStatus.setText("?곹깭: " + num + "踰??ㅼ떆媛?愿???묐룞 以?);
            Toast.makeText(this, num + "踰?愿???쒕퉬???쒖옉??, Toast.LENGTH_SHORT).show();
        });
    }
}
