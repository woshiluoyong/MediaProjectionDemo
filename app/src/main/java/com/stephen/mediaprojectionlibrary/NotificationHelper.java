package com.stephen.mediaprojectionlibrary;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import static android.content.Context.NOTIFICATION_SERVICE;

import com.mask.mediaprojectionlibrary.R;

/**
 * 通知栏 帮助类
 * Created by lishilin on 2017/7/31.
 */
public class NotificationHelper {
    private static final String CHANNEL_ID_OTHER = "other";
    private static final String CHANNEL_NAME_OTHER = "其他消息";
    @TargetApi(Build.VERSION_CODES.O)
    private static final int CHANNEL_IMPORTANCE_OTHER = NotificationManager.IMPORTANCE_MIN;

    private static final String CHANNEL_ID_SYSTEM = "system";
    private static final String CHANNEL_NAME_SYSTEM = "系统通知";
    @TargetApi(Build.VERSION_CODES.O)
    private static final int CHANNEL_IMPORTANCE_SYSTEM = NotificationManager.IMPORTANCE_HIGH;

    //创建通知渠道
    @TargetApi(Build.VERSION_CODES.O)
    private void createChannel(Context context, String channelId, String channelName, int importance, boolean isShowBadge) {
        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
        channel.setShowBadge(isShowBadge);//是否显示角标
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) notificationManager.createNotificationChannel(channel);
    }

    public NotificationCompat.Builder create(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, CHANNEL_ID_OTHER, CHANNEL_NAME_OTHER, CHANNEL_IMPORTANCE_OTHER, false);
            createChannel(context, CHANNEL_ID_SYSTEM, CHANNEL_NAME_SYSTEM, CHANNEL_IMPORTANCE_SYSTEM, true);
        }//end of if

        return new NotificationCompat.Builder(context, channelId).setContentTitle(context.getString(R.string.app_name)).setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher).setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher));
    }

    public NotificationCompat.Builder createOther(Context context) {
        return create(context, CHANNEL_ID_OTHER);
    }

    public NotificationCompat.Builder createSystem(Context context) {
        return create(context, CHANNEL_ID_SYSTEM);
    }
}
