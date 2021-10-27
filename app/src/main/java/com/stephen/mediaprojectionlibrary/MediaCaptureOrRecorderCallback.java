package com.stephen.mediaprojectionlibrary;

import java.io.File;
import java.io.Serializable;

//媒体投影 回调
public abstract class MediaCaptureOrRecorderCallback implements Serializable {
    //录制毫秒回调(sumMs is sum millisecond)
    public void onRecorderTimeTrack(long sumMs, int day, int hour, int minute, int second){}
    //截屏/录制成功
    public void onSuccess(boolean isCapture, String finalFilePath) {}
    //截屏/录制失败(capture errCode start from 100, record errCode start from 200 and 300)
    public void onFailure(boolean isCapture, int errCode, String errMsg) {}
}
