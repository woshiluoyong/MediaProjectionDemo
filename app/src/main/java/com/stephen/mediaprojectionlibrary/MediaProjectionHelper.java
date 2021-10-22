package com.stephen.mediaprojectionlibrary;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;

//媒体投影 帮助类
public class MediaProjectionHelper {
    public static final int REQUEST_CODE = 10086;
    private boolean isDebugMode = false;
    private boolean isScreenCaptureEnable;// 是否可以屏幕截图
    private boolean isMediaRecorderEnable;// 是否可以媒体录制
    private Notification notificationBarInfo;
    private MediaCaptureOrRecorderCallback mediaCaptureOrRecorderCallback;
    private MediaProjectionManager mediaProjectionManager;
    private DisplayMetrics displayMetrics;
    private ServiceConnection serviceConnection;
    private MediaProjectionService mediaProjectionService;

    private static class InstanceHolder {
        private static final MediaProjectionHelper instance = new MediaProjectionHelper();
    }

    public static MediaProjectionHelper getInstance() {
        return InstanceHolder.instance;
    }

    private MediaProjectionHelper() {
        super();
    }

    public void init(boolean isDebugMode, boolean isScreenCaptureEnable, boolean isMediaRecorderEnable, Notification notificationBarInfo,
                     MediaCaptureOrRecorderCallback mediaCaptureOrRecorderCallback){
        this.isDebugMode = isDebugMode;//debug模式设置
        this.isScreenCaptureEnable = isScreenCaptureEnable;
        this.isMediaRecorderEnable = isMediaRecorderEnable;
        this.notificationBarInfo = notificationBarInfo;//设置 通知信息
        this.mediaCaptureOrRecorderCallback = mediaCaptureOrRecorderCallback;
    }

    //输出日志
    private void printDebugLog(String log){
        if(isDebugMode)System.out.println("===Stephen=====MediaProjectionHelper====>"+log);
    }

    //启动媒体投影服务
    public boolean startMediaService(Activity activity) {
        if (mediaProjectionManager != null) {
            printDebugLog("call startMediaService fun: mediaProjectionManager don't is empty when startService initialization");
            return false;
        }//end of if

        // 启动媒体投影服务
        mediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager == null) {
            printDebugLog("call startMediaService fun: when i into the system for mediaProjectionManager is empty");
            return false;
        }else{
            activity.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        }

        // 此处宽高需要获取屏幕完整宽高，否则截屏图片会有白/黑边
        displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);

        // 绑定服务
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service instanceof MediaProjectionService.MediaProjectionBinder) {
                    mediaProjectionService = ((MediaProjectionService.MediaProjectionBinder) service).getService();
                    mediaProjectionService.init(isDebugMode, isScreenCaptureEnable, isMediaRecorderEnable, notificationBarInfo, mediaCaptureOrRecorderCallback);
                } else {
                    printDebugLog("call startMediaService fun: bind service the onServiceConnected callback method result service instance incorrect");
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                printDebugLog("call startMediaService fun: bind service the onServiceDisconnected callback method result service empty");
                mediaProjectionService = null;
            }
        };
        MediaProjectionService.bindService(activity, serviceConnection);
        return true;
    }

    //停止媒体投影服务
    public boolean stopMediaService(Context context) {
        mediaProjectionService = null;
        displayMetrics = null;
        mediaProjectionManager = null;
        if (serviceConnection != null) {
            printDebugLog("call stopMediaService fun: serviceConnection instance is empty");
            MediaProjectionService.unbindService(context, serviceConnection);
            serviceConnection = null;
            return false;
        }//end of if
        return true;
    }

    //创建VirtualDisplay(onActivityResult中调用)
    public boolean onActivityResultCallCreateVirtualDisplay(int requestCode, int resultCode, Intent data) {
        if (mediaProjectionService == null || requestCode != REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            printDebugLog("call createVirtualDisplay fun: mediaProjectionService instance is empty or requestCode/resultCode incorrect");
            return false;
        }//end of if
        mediaProjectionService.createVirtualDisplay(resultCode, data, displayMetrics);
        return true;
    }

    //屏幕截图
    public boolean screenCapture(String pictureFileName) {
        if (mediaProjectionService == null) {
            String msg = "mediaProjectionService instance is empty, Please call startMediaService first";
            printDebugLog("call screenCapture fun: "+msg);
            if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onFailure(true, 100, msg);
            return false;
        }//end of if
        mediaProjectionService.screenCapture(pictureFileName);
        return true;
    }

    //开始 屏幕录制
    public boolean startMediaRecorder(String videoFileName) {
        if (mediaProjectionService == null) {
            String msg = "mediaProjectionService instance is empty, Please call startMediaService first";
            printDebugLog("call startMediaRecorder fun: "+msg);
            if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onFailure(false, 200, msg);
            return false;
        }//end of if
        mediaProjectionService.startRecording(videoFileName);
        return true;
    }

    //停止 屏幕录制
    public boolean stopMediaRecorder() {
        if (mediaProjectionService == null) {
            String msg = "mediaProjectionService instance is empty, Please call startMediaService first";
            printDebugLog("call stopMediaRecorder fun: "+msg);
            if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onFailure(false, 250, msg);
            return false;
        }//end of if
        mediaProjectionService.stopRecording();
        return true;
    }
}
