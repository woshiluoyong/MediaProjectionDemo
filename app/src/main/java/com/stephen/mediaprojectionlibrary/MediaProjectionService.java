package com.stephen.mediaprojectionlibrary;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

//媒体投影 Service
public class MediaProjectionService extends Service {
    private static final int ID_MEDIA_PROJECTION = MediaProjectionHelper.REQUEST_CODE;
    private boolean isDebugMode = false;
    private boolean isScreenCaptureEnable;// 是否可以屏幕截图
    private boolean isMediaRecorderEnable;// 是否可以媒体录制
    private Notification notificationBarInfo;
    private MediaCaptureOrRecorderCallback mediaCaptureOrRecorderCallback;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplayImageReader;
    private ImageReader imageReader;
    private boolean isImageAvailable;
    private VirtualDisplay virtualDisplayMediaRecorder;
    private MediaRecorder mediaRecorder;
    private long mediaRecordCountMs = 0l;
    private boolean isMediaRecording;
    private String mediaSaveFilePath;
    private Handler mediaRecordTiming;
    private int width = -1;
    private int height = -1;
    private int densityDpi = -1;
    private MyTestServiceReceiver myTestServiceReceiver;

    public class MediaProjectionBinder extends Binder {
        public MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        myTestServiceReceiver = new MyTestServiceReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("onActivityResultCallCreateVirtualDisplay");
        intentFilter.addAction("screenCapture");
        intentFilter.addAction("startMediaRecorder");
        intentFilter.addAction("stopMediaRecorder");
        intentFilter.addAction("stopMediaService");
        registerReceiver(myTestServiceReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(null != intent) {
            if(intent.hasExtra("isDebugMode"))this.isDebugMode = intent.getBooleanExtra("isDebugMode", false);//debug模式设置
            if(intent.hasExtra("isScreenCaptureEnable"))this.isScreenCaptureEnable = intent.getBooleanExtra("isScreenCaptureEnable", false);
            if(intent.hasExtra("isMediaRecorderEnable"))this.isMediaRecorderEnable = intent.getBooleanExtra("isMediaRecorderEnable", false);
            if(intent.hasExtra("notificationBarInfo"))this.notificationBarInfo = intent.getParcelableExtra("notificationBarInfo");//设置 通知信息
            if(intent.hasExtra("width"))this.width = intent.getIntExtra("width", -1);
            if(intent.hasExtra("height"))this.height = intent.getIntExtra("height", -1);
            if(intent.hasExtra("densityDpi"))this.densityDpi = intent.getIntExtra("densityDpi", -1);
        }//end of if
        return START_NOT_STICKY;
    }

    //启动Service
    public static void startService(Context context, boolean isDebugMode, boolean isScreenCaptureEnable, boolean isMediaRecorderEnable, Notification notificationBarInfo, int width, int height, int densityDpi) {
        Intent intent = new Intent(context, MediaProjectionService.class);
        intent.putExtra("isDebugMode", isDebugMode);
        intent.putExtra("isScreenCaptureEnable", isScreenCaptureEnable);
        intent.putExtra("isMediaRecorderEnable", isMediaRecorderEnable);
        intent.putExtra("notificationBarInfo", notificationBarInfo);
        intent.putExtra("width", width);
        intent.putExtra("height", height);
        intent.putExtra("densityDpi", densityDpi);
        context.startService(intent);
    }

    //绑定Service
    public static void bindService(Context context, ServiceConnection serviceConnection) {
        Intent intent = new Intent(context, MediaProjectionService.class);
        context.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE);
    }

    public void bindServiceInit(boolean isDebugMode, boolean isScreenCaptureEnable, boolean isMediaRecorderEnable, Notification notificationBarInfo, int width, int height, int densityDpi,
                                MediaCaptureOrRecorderCallback mediaCaptureOrRecorderCallback){
        this.isDebugMode = isDebugMode;//debug模式设置
        this.isScreenCaptureEnable = isScreenCaptureEnable;
        this.isMediaRecorderEnable = isMediaRecorderEnable;
        this.notificationBarInfo = notificationBarInfo;//设置 通知信息
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.mediaCaptureOrRecorderCallback = mediaCaptureOrRecorderCallback;
    }

    //解绑Service
    public static void unbindService(Context context, ServiceConnection serviceConnection) {
        context.unbindService(serviceConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MediaProjectionBinder();
    }

    @Override
    public void onDestroy() {
        //结束 屏幕截图
        isImageAvailable = false;
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }//end of if
        if (virtualDisplayImageReader != null) {
            virtualDisplayImageReader.release();
            virtualDisplayImageReader = null;
        }//end of if
        //结束 媒体录制
        stopRecording();
        if (virtualDisplayMediaRecorder != null) {
            virtualDisplayMediaRecorder.release();
            virtualDisplayMediaRecorder = null;
        }//end of if
        //结束其他
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }//end of if
        if (mediaProjectionManager != null) mediaProjectionManager = null;
        if(null != notificationBarInfo)stopForeground(true);
        if(null != myTestServiceReceiver)unregisterReceiver(myTestServiceReceiver);
        super.onDestroy();
    }

    //创建 屏幕截图
    @SuppressLint("WrongConstant")
    private void createImageReader() {
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                isImageAvailable = true;
            }
        }, null);

        virtualDisplayImageReader = mediaProjection.createVirtualDisplay("ScreenCapture", width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
    }

    //创建VirtualDisplay
    public void createVirtualDisplay(int resultCode, Intent data) {
        if (data == null) {
            stopSelf();
            return;
        }//end of if
        if(null != notificationBarInfo)startForeground(ID_MEDIA_PROJECTION, notificationBarInfo);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager == null) {
            stopSelf();
            return;
        }//end of if
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            stopSelf();
            return;
        }//end of if
        if (isScreenCaptureEnable)createImageReader();
    }

    private String generateSaveFileName(String prefixStr){
        Date date = new Date();
        date.setTime(System.currentTimeMillis());
        String saveFileName = (new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault())).format(date);
        saveFileName = prefixStr + "_" + saveFileName;
        return saveFileName;
    }

    //屏幕截图
    public void screenCapture(String pictureFileName) {
        if (!isScreenCaptureEnable) {
            sendMediaFailureCallback(true, 101, "isScreenCaptureEnable is false");
            return;
        }//end of if
        if (imageReader == null) {
            sendMediaFailureCallback(true, 102, "imageReader is empty");
            return;
        }//end of if
        if (!isImageAvailable) {
            sendMediaFailureCallback(true, 103, "isImageAvailable is false");
            return;
        }//end of if
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            sendMediaFailureCallback(true, 104, "image is empty");
            return;
        }//end of if
        // 获取数据
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane plane = image.getPlanes()[0];
        final ByteBuffer buffer = plane.getBuffer();
        // 重新计算Bitmap宽度，防止Bitmap显示错位
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int bitmapWidth = width + rowPadding / pixelStride;
        // 创建Bitmap
        final Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        // 释放资源
        image.close();
        // 裁剪Bitmap，因为重新计算宽度原因，会导致Bitmap宽度偏大
        final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        bitmap.recycle();
        isImageAvailable = false;
        if (result == null) {
            sendMediaFailureCallback(true, 105, "resultBitmap is empty");
        } else {
            if(TextUtils.isEmpty(pictureFileName)) pictureFileName = generateSaveFileName("ScreenCapture");

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, pictureFileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if(RomUtils.isMiui() || RomUtils.isOppo() || RomUtils.isVivo()){//reference https://m.thepaper.cn/baijiahao_10262949
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Screenshots");
                } else {
                    values.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath()+"/Screenshots/"+pictureFileName+".png");
                }
            }else{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots");
                } else {
                    values.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath()+"/Screenshots/"+pictureFileName+".png");
                }
            }
            Uri pictureUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (null == pictureUri) {
                sendMediaFailureCallback(false, 210, "Images pictureUri is empty， Don't set the duplicate file name");
                return;
            }//end of if
            ParcelFileDescriptor pfd = null;
            try {
                pfd = getContentResolver().openFileDescriptor(pictureUri, "rw", null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (null == pfd) {
                sendMediaFailureCallback(false, 210, "Images ParcelFileDescriptor pfd is empty");
                return;
            }//end of if
            mediaSaveFilePath = pictureUri.getPath();
            final ParcelFileDescriptor finalPfd = pfd;

            final Handler mHandler = initScreenCaptureCompressCallBack();
            (new Thread(new Runnable() {
                public void run() {
                    try {
                        FileOutputStream fos = new FileOutputStream(finalPfd.getFileDescriptor());
                        try {
                            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            Message message = Message.obtain();
                            message.what = 0;
                            message.obj = mediaSaveFilePath;
                            mHandler.sendMessage(message);
                        } catch (Exception e) {
                            try {
                                fos.close();
                            } catch (Exception e1) { e1.printStackTrace(); }
                            Message message = Message.obtain();
                            message.what = 1;
                            message.arg1 = 106;
                            message.obj = "resultBitmap compress exception:"+e.getMessage();
                            mHandler.sendMessage(message);
                        }
                        fos.close();
                    } catch (Exception e) {
                        Message message = Message.obtain();
                        message.what = 1;
                        message.arg1 = 107;
                        message.obj = "resultBitmap compress exception:"+e.getMessage();
                        mHandler.sendMessage(message);
                    }
                }
            })).start();
        }
    }

    //录屏压缩线程回调至主线程
    private Handler initScreenCaptureCompressCallBack(){
        Looper looper = getMainLooper();
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                try {
                    if(null == msg)return;
                    if(0 == msg.what){
                        sendMediaSuccessCallback(true, (String)msg.obj);
                    }else{
                        sendMediaFailureCallback(true, msg.arg1, "resultBitmap compress exception:"+msg.obj);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                this.removeCallbacksAndMessages(null);
            }
        };
    }

    //录屏计时
    private Handler initMediaRecorderTiming(){
        Looper looper = getMainLooper();
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mediaRecordCountMs += 1000l;
                long day = mediaRecordCountMs / (24 * 60 * 60 * 1000);
                long hour = mediaRecordCountMs / (60 * 60 * 1000) - day * 24;
                long minute = mediaRecordCountMs / (60 * 1000) - day * 24 * 60 - hour * 60;
                long second = mediaRecordCountMs / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - minute * 60;
                sendMediaTimeTrackCallback(mediaRecordCountMs, (int)day, (int)hour, (int)minute, (int)second);
                if(null != mediaRecordTiming && isMediaRecording)mediaRecordTiming.sendEmptyMessageDelayed(888, 1000l);
            }
        };
    }

    //开始 媒体录制
    public void startRecording(String videoFileName) {
        if (!isMediaRecorderEnable) {
            sendMediaFailureCallback(false, 201, "isMediaRecorderEnable is false");
            return;
        }//end of if
        if (isMediaRecording) {
            sendMediaFailureCallback(false, 202, "isMediaRecording is true");
            return;
        }//end of if
        mediaRecordCountMs = 0l;

        if(TextUtils.isEmpty(videoFileName)) videoFileName = generateSaveFileName("MediaRecorder");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if(RomUtils.isMiui() || RomUtils.isOppo() || RomUtils.isVivo()){//reference https://m.thepaper.cn/baijiahao_10262949
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Screenshots");
            } else {
                values.put(MediaStore.Video.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath()+"/Screenshots/"+videoFileName+".mp4");
            }
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/Screenshots");
            } else {
                values.put(MediaStore.Video.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath()+"/Screenshots/"+videoFileName+".mp4");
            }
        }
        Uri videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (null == videoUri) {
            sendMediaFailureCallback(false, 210, "Video videoUri is empty， Don't set the duplicate file name");
            return;
        }//end of if
        ParcelFileDescriptor pfd = null;
        try {
            pfd = getContentResolver().openFileDescriptor(videoUri, "rw", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null == pfd) {
            sendMediaFailureCallback(false, 211, "Video ParcelFileDescriptor pfd is empty");
            return;
        }//end of if

        mediaSaveFilePath = videoUri.getPath();
        // 调用顺序不能乱
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(pfd.getFileDescriptor());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(5 * width * height);

        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                sendMediaFailureCallback(false, 300 + extra, "MediaRecorder onError callback");
            }
        });
        try {
            mediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (virtualDisplayMediaRecorder == null) {
            virtualDisplayMediaRecorder = mediaProjection.createVirtualDisplay("MediaRecorder", width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
        } else {
            virtualDisplayMediaRecorder.setSurface(mediaRecorder.getSurface());
        }
        mediaRecorder.start();
        isMediaRecording = true;
        mediaRecordTiming = initMediaRecorderTiming();
        mediaRecordTiming.sendEmptyMessage(888);
    }

    //停止 媒体录制
    public void stopRecording() {
        if (!isMediaRecorderEnable) {
            sendMediaFailureCallback(false, 201, "isMediaRecorderEnable is false");
        }//end of if
        if (mediaRecorder == null) {
            sendMediaFailureCallback(false, 203, "mediaRecorder is empty");
            return;
        }//end of if
        if (!isMediaRecording) {
            sendMediaFailureCallback(false, 202, "isMediaRecording is true");
            return;
        }//end of if
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        mediaRecorder = null;
        sendMediaSuccessCallback(false, mediaSaveFilePath);
        isMediaRecording = false;
        if(null != mediaRecordTiming)mediaRecordTiming.removeCallbacksAndMessages(null);
        mediaRecordTiming = null;
    }

    private void sendMediaSuccessCallback(boolean isCapture, String finalFilePath){
        if (null != mediaCaptureOrRecorderCallback) {
            mediaCaptureOrRecorderCallback.onSuccess(isCapture, finalFilePath);
        } else {
            Intent intent = new Intent();
            intent.setAction("onSuccess");
            intent.putExtra("isCapture", isCapture);
            intent.putExtra("finalFilePath", finalFilePath);
            sendBroadcast(intent);
        }
    }

    private void sendMediaFailureCallback(boolean isCapture, int errCode, String errMsg){
        if (null != mediaCaptureOrRecorderCallback) {
            mediaCaptureOrRecorderCallback.onFailure(isCapture, errCode, errMsg);
        } else {
            Intent intent = new Intent();
            intent.setAction("onFailure");
            intent.putExtra("isCapture", isCapture);
            intent.putExtra("errCode", errCode);
            intent.putExtra("errMsg", errMsg);
            sendBroadcast(intent);
        }
    }

    private void sendMediaTimeTrackCallback(long sumMs, int day, int hour, int minute, int second){
        if (null != mediaCaptureOrRecorderCallback) {
            mediaCaptureOrRecorderCallback.onRecorderTimeTrack(sumMs, day, hour, minute, second);
        } else {
            Intent intent = new Intent();
            intent.setAction("onRecorderTimeTrack");
            intent.putExtra("sumMs", sumMs);
            intent.putExtra("day", day);
            intent.putExtra("hour", hour);
            intent.putExtra("minute", minute);
            intent.putExtra("second", second);
            sendBroadcast(intent);
        }
    }

    private void stopMediaService(){
        stopSelf();
    }

    public class MyTestServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(null == intent || TextUtils.isEmpty(intent.getAction()))return;
            if(intent.getAction().equals("onActivityResultCallCreateVirtualDisplay")){
                createVirtualDisplay(intent.getIntExtra("resultCode", -1), intent.<Intent>getParcelableExtra("data"));
            }else if(intent.getAction().equals("screenCapture")){
                screenCapture(intent.getStringExtra("pictureFileName"));
            }else if(intent.getAction().equals("startMediaRecorder")){
                startRecording(intent.getStringExtra("videoFileName"));
            }else if(intent.getAction().equals("stopMediaRecorder")){
                stopRecording();
            }else if(intent.getAction().equals("stopMediaService")){
                stopMediaService();
            }
        }
    }
}
