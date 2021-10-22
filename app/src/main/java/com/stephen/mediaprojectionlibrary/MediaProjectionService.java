package com.stephen.mediaprojectionlibrary;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.io.File;
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
    private DisplayMetrics displayMetrics;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplayImageReader;
    private ImageReader imageReader;
    private boolean isImageAvailable;
    private VirtualDisplay virtualDisplayMediaRecorder;
    private MediaRecorder mediaRecorder;
    private File mediaFile;
    private long mediaRecordCountMs = 0l;
    private boolean isMediaRecording;
    private Handler mediaRecordTiming;

    public class MediaProjectionBinder extends Binder {
        public MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    public void init(boolean isDebugMode, boolean isScreenCaptureEnable, boolean isMediaRecorderEnable, Notification notificationBarInfo,
                     MediaCaptureOrRecorderCallback mediaCaptureOrRecorderCallback){
        this.isDebugMode = isDebugMode;//debug模式设置
        this.isScreenCaptureEnable = isScreenCaptureEnable;
        this.isMediaRecorderEnable = isMediaRecorderEnable;
        this.notificationBarInfo = notificationBarInfo;//设置 通知信息
        this.mediaCaptureOrRecorderCallback = mediaCaptureOrRecorderCallback;
    }

    //绑定Service
    public static void bindService(Context context, ServiceConnection serviceConnection) {
        Intent intent = new Intent(context, MediaProjectionService.class);
        context.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE);
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
        super.onDestroy();
    }

    //创建 屏幕截图
    @SuppressLint("WrongConstant")
    private void createImageReader() {
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        int densityDpi = displayMetrics.densityDpi;

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
    public void createVirtualDisplay(int resultCode, Intent data, DisplayMetrics displayMetrics) {
        this.displayMetrics = displayMetrics;
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
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, 101, "isScreenCaptureEnable is false");
            return;
        }//end of if
        if (imageReader == null) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, 102, "imageReader is empty");
            return;
        }//end of if
        if (!isImageAvailable) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, 103, "isImageAvailable is false");
            return;
        }//end of if
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, 104, "image is empty");
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
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, 105, "resultBitmap is empty");
        } else {
            // 创建保存路径
            final File dirFile = new File(this.getExternalCacheDir(), Environment.DIRECTORY_PICTURES);
            if(!dirFile.exists())dirFile.mkdirs();

            if(TextUtils.isEmpty(pictureFileName)) pictureFileName = generateSaveFileName("ScreenCapture");
            final String savePictureFileName = pictureFileName;

            final Handler mHandler = initScreenCaptureCompressCallBack();
            (new Thread(new Runnable() {
                public void run() {
                    File outFile = new File(dirFile, savePictureFileName + ".png");
                    try {
                        FileOutputStream fos = new FileOutputStream(outFile);
                        try {
                            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            Message message = Message.obtain();
                            message.what = 0;
                            message.obj = outFile;
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
                        if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onSuccess(true, (File)msg.obj);
                    }else{
                        if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(true, msg.arg1, "resultBitmap compress exception:"+msg.obj);
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
                if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onRecorderTimeTrack(mediaRecordCountMs, (int)day, (int)hour, (int)minute, (int)second);
                if(null != mediaRecordTiming && isMediaRecording)mediaRecordTiming.sendEmptyMessageDelayed(888, 1000l);
            }
        };
    }

    //开始 媒体录制
    public void startRecording(String videoFileName) {
        if (!isMediaRecorderEnable) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 201, "isMediaRecorderEnable is false");
            return;
        }//end of if
        if (isMediaRecording) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 202, "isMediaRecording is true");
            return;
        }//end of if
        mediaRecordCountMs = 0l;
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        int densityDpi = displayMetrics.densityDpi;

        // 创建保存路径
        final File dirFile = new File(this.getExternalCacheDir(), Environment.DIRECTORY_MOVIES);
        if(!dirFile.exists())dirFile.mkdirs();

        if(TextUtils.isEmpty(videoFileName)) videoFileName = generateSaveFileName("MediaRecorder");
        // 创建保存文件
        mediaFile = new File(dirFile, videoFileName + ".mp4");
        // 调用顺序不能乱
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(mediaFile.getAbsolutePath());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(5 * width * height);

        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 300 + extra, "MediaRecorder onError callback");
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
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 201, "isMediaRecorderEnable is false");
        }//end of if
        if (mediaRecorder == null) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 203, "mediaRecorder is empty");
            return;
        }//end of if
        if (!isMediaRecording) {
            if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onFailure(false, 202, "isMediaRecording is true");
            return;
        }//end of if
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        mediaRecorder = null;
        if (null != mediaCaptureOrRecorderCallback) mediaCaptureOrRecorderCallback.onSuccess(false, mediaFile);
        mediaFile = null;
        isMediaRecording = false;
        if(null != mediaRecordTiming)mediaRecordTiming.removeCallbacksAndMessages(null);
        mediaRecordTiming = null;
    }
}
