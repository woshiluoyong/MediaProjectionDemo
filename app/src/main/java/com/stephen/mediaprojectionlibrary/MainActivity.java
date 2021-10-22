package com.stephen.mediaprojectionlibrary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mask.mediaprojectionlibrary.R;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private Button btn_service_start;
    private Button btn_service_stop;
    private Button btn_screen_capture;
    private Button btn_media_recorder_start;
    private Button btn_media_recorder_stop;
    private TextView recorder_time_text;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MediaProjectionHelper.getInstance().onActivityResultCallCreateVirtualDisplay(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initListener();
        initData();
    }

    @Override
    protected void onDestroy() {
        MediaProjectionHelper.getInstance().stopMediaService(this);
        super.onDestroy();
    }

    private void initView() {
        btn_service_start = findViewById(R.id.btn_service_start);
        btn_service_stop = findViewById(R.id.btn_service_stop);
        btn_screen_capture = findViewById(R.id.btn_screen_capture);
        btn_media_recorder_start = findViewById(R.id.btn_media_recorder_start);
        btn_media_recorder_stop = findViewById(R.id.btn_media_recorder_stop);
        recorder_time_text = findViewById(R.id.recorder_time_text);
    }

    private void initListener() {
        btn_service_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(), MediaProjectionHelper.getInstance().startMediaService(MainActivity.this) ? "启动服务成功" : "启动服务失败", Toast.LENGTH_LONG).show();
            }
        });
        btn_service_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(), MediaProjectionHelper.getInstance().stopMediaService(MainActivity.this) ? "停止服务成功" : "停止服务失败", Toast.LENGTH_LONG).show();
            }
        });
        btn_screen_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(), MediaProjectionHelper.getInstance().screenCapture("StephenPicture") ? "截屏成功" : "截屏失败", Toast.LENGTH_LONG).show();
            }
        });
        btn_media_recorder_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(), MediaProjectionHelper.getInstance().startMediaRecorder("StephenVideo") ? "启动录屏成功" : "启动录屏失败", Toast.LENGTH_LONG).show();
            }
        });
        btn_media_recorder_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(), MediaProjectionHelper.getInstance().stopMediaRecorder() ? "停止录屏成功" : "停止录屏失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void initData() {
        MediaProjectionHelper.getInstance().init(true, true, true, /*NotificationHelper.getInstance().createSystem().setOngoing(true).setTicker("Media Service Can Use")
                .setContentText("Media Service is Running...").setDefaults(Notification.DEFAULT_ALL).build()*/ null, new MediaCaptureOrRecorderCallback() {
            @Override
            public void onRecorderTimeTrack(long sumMs, int day, int hour, int minute, int second) {
                System.out.println("===Stephen======onRecorderTimeTrack===>sumMs:"+sumMs+"===>day:"+day+"===>hour:"+hour+"===>minute:"+minute+"===>second:"+second);
                recorder_time_text.setText(day+"天"+hour+"时"+minute+"分"+second+"秒");
            }

            @Override
            public void onSuccess(boolean isCapture, File finalFile) {
                final String msg = (isCapture ? "截屏" : "录屏")+ finalFile.getAbsolutePath();
                System.out.println("===Stephen======onSuccess===>"+msg);
                (new Handler()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show();
                    }
                }, 3000);
            }

            @Override
            public void onFailure(boolean isCapture, int errCode, String errMsg) {
                final String msg = (isCapture ? "截屏" : "录屏")+ "报错啦！===>errCode:"+errCode+"===>errMsg:"+errMsg;
                System.out.println("===Stephen======onFailure===>"+msg);
                (new Handler()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show();
                    }
                }, 3000);
            }
        });
    }
}
