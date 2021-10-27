package com.stephen.mediaprojectionlibrary;

import android.app.Activity;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.text.TextUtils;
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
    private ServiceConnection serviceConnection;
    private MediaProjectionService mediaBindService;
    private MyTestHelperReceiver myTestHelperReceiver;

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
    public boolean startMediaService(Activity activity, boolean isBindServiceMethod) {
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
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);

        if(isBindServiceMethod){
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (service instanceof MediaProjectionService.MediaProjectionBinder) {
                        mediaBindService = ((MediaProjectionService.MediaProjectionBinder) service).getService();
                        mediaBindService.bindServiceInit(isDebugMode, isScreenCaptureEnable, isMediaRecorderEnable, notificationBarInfo, displayMetrics.widthPixels,
                                displayMetrics.heightPixels, displayMetrics.densityDpi, mediaCaptureOrRecorderCallback);
                    } else {
                        printDebugLog("call startMediaService fun: bind service the onServiceConnected callback method result service instance incorrect");
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    printDebugLog("call startMediaService fun: bind service the onServiceDisconnected callback method result service empty");
                    mediaBindService = null;
                }
            };
            MediaProjectionService.bindService(activity, serviceConnection);
        }else{
            myTestHelperReceiver = new MyTestHelperReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("onRecorderTimeTrack");
            intentFilter.addAction("onSuccess");
            intentFilter.addAction("onFailure");
            activity.registerReceiver(myTestHelperReceiver, intentFilter);
            MediaProjectionService.startService(activity, isDebugMode, isScreenCaptureEnable, isMediaRecorderEnable, notificationBarInfo, displayMetrics.widthPixels,
                    displayMetrics.heightPixels, displayMetrics.densityDpi);
        }
        return true;
    }

    //停止媒体投影服务
    public boolean stopMediaService(Context context, boolean isBindServiceMethod) {
        mediaBindService = null;
        mediaProjectionManager = null;
        if(mediaBindService == null){
            Intent intent = new Intent();
            intent.setAction("stopMediaService");
            context.sendBroadcast(intent);
        }else {
            if (serviceConnection == null) {
                printDebugLog("call stopMediaService fun: serviceConnection instance is empty");
                return false;
            } else {
                MediaProjectionService.unbindService(context, serviceConnection);
                serviceConnection = null;
            }
        }
        return true;
    }

    //创建VirtualDisplay(onActivityResult中调用)
    public boolean onActivityResultCallCreateVirtualDisplay(Context context, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            printDebugLog("call createVirtualDisplay fun: mediaProjectionService instance is empty or requestCode/resultCode incorrect");
            return false;
        }//end of if
        if(mediaBindService == null){
            Intent intent = new Intent();
            intent.setAction("onActivityResultCallCreateVirtualDisplay");
            intent.putExtra("resultCode", resultCode);
            intent.putExtra("data", data);
            context.sendBroadcast(intent);
        }else{
            mediaBindService.createVirtualDisplay(resultCode, data);
        }
        return true;
    }

    //屏幕截图
    public boolean screenCapture(Context context, String pictureFileName) {
        if(mediaBindService == null){
            Intent intent = new Intent();
            intent.setAction("screenCapture");
            intent.putExtra("pictureFileName", pictureFileName);
            context.sendBroadcast(intent);
        } else {
            mediaBindService.screenCapture(pictureFileName);
        }
        return true;
    }

    //开始 屏幕录制
    public boolean startMediaRecorder(Context context, String videoFileName) {
        if(mediaBindService == null){
            Intent intent = new Intent();
            intent.setAction("startMediaRecorder");
            intent.putExtra("videoFileName", videoFileName);
            context.sendBroadcast(intent);
        } else {
            mediaBindService.startRecording(videoFileName);
        }
        return true;
    }

    //停止 屏幕录制
    public boolean stopMediaRecorder(Context context) {
        if(mediaBindService == null){
            Intent intent = new Intent();
            intent.setAction("stopMediaRecorder");
            context.sendBroadcast(intent);
        } else {
            mediaBindService.stopRecording();
        }
        return true;
    }

    public class MyTestHelperReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(null == intent || TextUtils.isEmpty(intent.getAction()))return;
            if(intent.getAction().equals("onRecorderTimeTrack")){
                if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onRecorderTimeTrack(intent.getIntExtra("sumMs", -1), intent.getIntExtra("day", -1),
                        intent.getIntExtra("hour", -1), intent.getIntExtra("minute", -1), intent.getIntExtra("second", -1));
            }else if(intent.getAction().equals("onSuccess")){
                if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onSuccess(intent.getBooleanExtra("isCapture", false), intent.getStringExtra("finalFilePath"));
            }else if(intent.getAction().equals("onFailure")){
                if(null != mediaCaptureOrRecorderCallback)mediaCaptureOrRecorderCallback.onFailure(intent.getBooleanExtra("isCapture", false), intent.getIntExtra("errCode", -1), intent.getStringExtra("errMsg"));
            }
        }
    }
}
