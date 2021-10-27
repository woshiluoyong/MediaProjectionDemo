package com.stephen.mediaprojectionlibrary;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class RomUtils {
 
    private static final String TAG = "RomUtils";
 
    public static final String ROM_MIUI = "MIUI";
    public static final String ROM_EMUI = "EMUI";
    public static final String ROM_FLYME = "FLYME";
    public static final String ROM_OPPO = "OPPO";
    public static final String ROM_SMARTISAN = "SMARTISAN";
    public static final String ROM_VIVO = "VIVO";
    public static final String ROM_QIKU = "QIKU";
 
    private static final String KEY_VERSION_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_VERSION_EMUI = "ro.build.version.emui";
    private static final String KEY_VERSION_OPPO = "ro.build.version.opporom";
    private static final String KEY_VERSION_SMARTISAN = "ro.smartisan.version";
    private static final String KEY_VERSION_VIVO = "ro.vivo.os.version";
 
    private static String sName;
    private static String sVersion;
 
    public static boolean isEmui() {
        return check(ROM_EMUI);
    }
 
    public static boolean isMiui() {
        return check(ROM_MIUI);
    }
 
    public static boolean isVivo() {
        return check(ROM_VIVO);
    }
 
    public static boolean isOppo() {
        return check(ROM_OPPO);
    }
 
    public static boolean isFlyme() {
        return check(ROM_FLYME);
    }
 
    public static boolean is360() {
        return check(ROM_QIKU) || check("360");
    }
 
    public static boolean isSmartisan() {
        return check(ROM_SMARTISAN);
    }

    public static String getBrandName(){//品牌名字
        return (TextUtils.isEmpty(Build.BRAND) ? (TextUtils.isEmpty(Build.MANUFACTURER)
                ? (TextUtils.isEmpty(Build.PRODUCT) ? "unknown" : Build.PRODUCT) : Build.MANUFACTURER) : Build.BRAND);
    }

    public static String getRomName(){//rom名字
        if (sName == null) check("");
        return sName;
    }
    
    public static String getVersion() {//rom版本
        if (sVersion == null) check("");
        return sVersion;
    }
 
    public static boolean check(String rom) {
        if(sName != null)return sName.equals(rom);
 
        if (!TextUtils.isEmpty(sVersion = getPhoneSysProp(KEY_VERSION_MIUI))) {
            sName = ROM_MIUI;
        } else if (!TextUtils.isEmpty(sVersion = getPhoneSysProp(KEY_VERSION_EMUI))) {
            sName = ROM_EMUI;
        } else if (!TextUtils.isEmpty(sVersion = getPhoneSysProp(KEY_VERSION_OPPO))) {
            sName = ROM_OPPO;
        } else if (!TextUtils.isEmpty(sVersion = getPhoneSysProp(KEY_VERSION_VIVO))) {
            sName = ROM_VIVO;
        } else if (!TextUtils.isEmpty(sVersion = getPhoneSysProp(KEY_VERSION_SMARTISAN))) {
            sName = ROM_SMARTISAN;
        } else {
            sVersion = Build.DISPLAY;
            if (sVersion.toUpperCase().contains(ROM_FLYME)) {
                sName = ROM_FLYME;
            } else {
                sVersion = Build.UNKNOWN;
                sName = Build.MANUFACTURER.toUpperCase();
            }
        }
        return sName.equals(rom);
    }
 
    public static String getPhoneSysProp(String phoneSysFlag) {
        String line = null;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + phoneSysFlag);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            Log.e(TAG, "Unable to read prop " + phoneSysFlag, ex);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }

    public String getPhoneSysVer(String phoneSysFlag) {
        Class<?> classType = null;
        String buildVersion = "";
        try {
            classType = Class.forName("android.os.SystemProperties");
            Method getMethod = classType.getDeclaredMethod("get", new Class<?>[]{String.class});
            buildVersion = (String) getMethod.invoke(classType, new Object[]{ phoneSysFlag });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buildVersion;
    }
}