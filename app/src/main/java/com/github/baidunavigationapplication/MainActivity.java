package com.github.baidunavigationapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.trace.api.entity.OnEntityListener;
import com.baidu.trace.api.fence.FenceAlarmPushInfo;
import com.baidu.trace.api.fence.MonitoredAction;
import com.baidu.trace.api.track.DistanceRequest;
import com.baidu.trace.api.track.DistanceResponse;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.LatestPoint;
import com.baidu.trace.api.track.LatestPointResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.SupplementMode;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.LocationMode;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.SortType;
import com.baidu.trace.model.StatusCodes;
import com.baidu.trace.model.TraceLocation;
import com.baidu.trace.model.TransportMode;
import com.github.baidunavigationapplication.utils.BitmapUtil;
import com.github.baidunavigationapplication.utils.CommonUtil;
import com.github.baidunavigationapplication.utils.Constants;
import com.github.baidunavigationapplication.utils.CurrentLocation;
import com.github.baidunavigationapplication.utils.MapUtil;
import com.github.baidunavigationapplication.utils.ViewUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sunwei
 * email：tianmu19@gmail.com
 * date：2019/10/10 10:42
 * package：com.github.baidunavigationapplication
 * version：1.0
 * <p>description：  鹰眼轨迹追踪，地图轨迹展示，驾驶行为分析          </p>
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TrackApplication trackApp = null;
    private NotificationManager notificationManager = null;

    private PowerManager powerManager = null;

    private PowerManager.WakeLock wakeLock = null;

    private TrackReceiver trackReceiver = null;
    private ViewUtil viewUtil = null;

    /**
     * 地图工具
     */
    private MapUtil mapUtil = null;

    /**
     * 轨迹服务监听器
     */
    private OnTraceListener traceListener = null;

    /**
     * 轨迹监听器(用于接收纠偏后实时位置回调)
     */
    private OnTrackListener trackListener = null;

    /**
     * Entity监听器(用于接收实时定位回调)
     */
    private OnEntityListener entityListener = null;

    /**
     * 实时定位任务
     */
    private RealTimeHandler realTimeHandler = new RealTimeHandler();

    private RealTimeLocRunnable realTimeLocRunnable = null;

    private boolean isRealTimeRunning = true;
    /**
     * 定位周期(单位:秒)
     */
    public int gatherInterval = Constants.LOC_INTERVAL;
    /**
     * 打包周期
     */
    public int packInterval = Constants.DEFAULT_PACK_INTERVAL;
    private Button traceBtn = null;

    private Button gatherBtn = null;


    private int notifyId = 10;
    private MapView mapView;

    /**
     * 轨迹
     */
    /**
     * 轨迹点集合
     */
    private List<LatLng> trackPoints = new ArrayList<>();
    /**
     * 轨迹排序规则
     */
    private SortType sortType = SortType.asc;
    /**
     * 历史轨迹请求
     */
    private HistoryTrackRequest historyTrackRequest = new HistoryTrackRequest();
    /**
     * 里程请求，显示总里程
     */

    private DistanceRequest distanceRequest = new DistanceRequest();
    private AlertDialog dialog;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dialog = new AlertDialog.Builder(this).setNegativeButton("取消", null).create();
        dialog.setMessage("显示轨迹信息");
        findViewById(R.id.btn_showdialog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (null != dialog && !dialog.isShowing()) {
                    dialog.show();
                }
            }
        });
        init2();
        //位置采集周期
// 在Android 6.0及以上系统，若定制手机使用到doze模式，请求将应用添加到白名单。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = trackApp.getPackageName();
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    static class RealTimeHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * 初始化轨迹追踪
     */
    private void init2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1011);
        }
        BitmapUtil.init();
        initListener();
        mapView = (MapView) findViewById(R.id.tracing_mapView);
        trackApp = (TrackApplication) getApplicationContext();
        viewUtil = new ViewUtil();
        mapUtil = MapUtil.getInstance();
        mapUtil.init(mapView);
        mapUtil.setCenter(trackApp);
        startRealTimeLoc(Constants.LOC_INTERVAL);
        powerManager = (PowerManager) trackApp.getSystemService(Context.POWER_SERVICE);

        traceBtn = (Button) findViewById(R.id.btn_trace);
        gatherBtn = (Button) findViewById(R.id.btn_gather);

        traceBtn.setOnClickListener(this);
        gatherBtn.setOnClickListener(this);
        trackApp.mClient.setLocationMode(LocationMode.High_Accuracy);
        trackApp.mClient.setInterval(gatherInterval, packInterval);
        setTraceBtnStyle();
        setGatherBtnStyle();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        initQueryTrace();
    }

    /**
     * 边上传 边轨迹查询
     */
    private void initQueryTrace() {
        trackApp.initRequest(historyTrackRequest);
        historyTrackRequest.setEntityName(trackApp.entityName);
//设置轨迹查询起止时间
// 开始时间(单位：秒)
        long startTime = CommonUtil.getCurrentTime()-60*60*19;
// 结束时间(单位：秒)
        long endTime = CommonUtil.getCurrentTime();
        historyTrackRequest.setSupplementMode(SupplementMode.walking);
        historyTrackRequest.setSortType(SortType.asc);
// 设置开始时间
        historyTrackRequest.setStartTime(startTime);
// 设置结束时间
        historyTrackRequest.setEndTime(endTime);
        /**
         * 设置需要纠偏
         */
        historyTrackRequest.setProcessed(true);
        // 创建纠偏选项实例
        ProcessOption processOption = new ProcessOption();
// 设置需要去噪
        processOption.setNeedDenoise(true);
// 设置需要抽稀
        processOption.setNeedVacuate(true);
// 设置需要绑路
        processOption.setNeedMapMatch(true);
// 设置精度过滤值(定位精度大于100米的过滤掉)
        processOption.setRadiusThreshold(100);
// 设置交通方式为驾车
        processOption.setTransportMode(TransportMode.walking);
// 设置纠偏选项
        historyTrackRequest.setProcessOption(processOption);
        /**
         * 创建里程查询请求实例
         */
        trackApp.initRequest(distanceRequest);
        distanceRequest.setEntityName(trackApp.entityName);
//设置轨迹查询起止时间
        distanceRequest.setSupplementMode(SupplementMode.walking);
// 设置开始时间
        distanceRequest.setStartTime(startTime);
// 设置结束时间
        distanceRequest.setEndTime(endTime);
        // 设置需要纠偏
        distanceRequest.setProcessed(true);
// 设置纠偏选项
        distanceRequest.setProcessOption(processOption);
// 设置里程填充方式为驾车
        distanceRequest.setSupplementMode(SupplementMode.driving);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_trace:
                if (trackApp.isTraceStarted) {
                    Log.e(TAG, "已开始服务");
                    trackApp.mClient.stopTrace(trackApp.mTrace, traceListener);
                    stopRealTimeLoc();
                } else {
                    Log.e(TAG, "没开始服务");
                    trackApp.mClient.startTrace(trackApp.mTrace, traceListener);
                    if (Constants.DEFAULT_PACK_INTERVAL != packInterval) {
                        Log.e(TAG, "没开始服务进入判断");
                        stopRealTimeLoc();
                        startRealTimeLoc(packInterval);
                    }
                }
                break;

            case R.id.btn_gather:
                if (trackApp.isGatherStarted) {
                    trackApp.mClient.stopGather(traceListener);
                } else {
                    trackApp.mClient.startGather(traceListener);
                }
                break;

            default:
                break;
        }

    }

    public void stopRealTimeLoc() {
        isRealTimeRunning = false;
        if (null != realTimeHandler && null != realTimeLocRunnable) {
            realTimeHandler.removeCallbacks(realTimeLocRunnable);
        }
        trackApp.mClient.stopRealTimeLoc();
    }

    public void startRealTimeLoc(int interval) {
        isRealTimeRunning = true;
        realTimeLocRunnable = new RealTimeLocRunnable(interval);
        realTimeHandler.post(realTimeLocRunnable);
    }

    private void initListener() {
       final OnTrackListener trackListener2 = new OnTrackListener() {

            /**
             * 里程
             */
            @Override
            public void onDistanceCallback(DistanceResponse distanceResponse) {
                super.onDistanceCallback(distanceResponse);
                Log.e(TAG, "onDistanceCallback");
                double lowSpeedDistance = distanceResponse.getLowSpeedDistance();
                dialog.setMessage("轨迹里程：" + lowSpeedDistance);
            }
            /**
             * 历史轨迹回调
             */
            @Override
            public void onHistoryTrackCallback(HistoryTrackResponse response) {
                Log.e(TAG, "onHistoryTrackCallback");

                int total = response.getTotal();
                if (StatusCodes.SUCCESS != response.getStatus()) {
                    viewUtil.showToast(MainActivity.this, response.getMessage());
                } else if (0 == total) {
                    viewUtil.showToast(MainActivity.this, getString(R.string.no_track_data));
                } else {
                    List<TrackPoint> points = response.getTrackPoints();
                    if (null != points) {
                        for (TrackPoint trackPoint : points) {
                            if (!CommonUtil.isZeroPoint(trackPoint.getLocation().getLatitude(),
                                    trackPoint.getLocation().getLongitude())) {
                                trackPoints.add(MapUtil.convertTrace2Map(trackPoint.getLocation()));
                            }
                        }
                    }
                }
                mapUtil.drawHistoryTrack(trackPoints, sortType);
            }
        };
        trackListener = new OnTrackListener() {
            /**
             * 轨迹上传
             */
            @Override
            public void onLatestPointCallback(LatestPointResponse response) {
                if (StatusCodes.SUCCESS != response.getStatus()) {
                    return;
                }

                LatestPoint point = response.getLatestPoint();
                if (null == point || CommonUtil.isZeroPoint(point.getLocation().getLatitude(), point.getLocation()
                        .getLongitude())) {
                    return;
                }

                LatLng currentLatLng = mapUtil.convertTrace2Map(point.getLocation());
                if (null == currentLatLng) {
                    return;
                }
                CurrentLocation.locTime = point.getLocTime();
                CurrentLocation.latitude = currentLatLng.latitude;
                CurrentLocation.longitude = currentLatLng.longitude;

//                if (null != mapUtil) {
//                    mapUtil.updateStatus(currentLatLng, true);
//                }
                Log.e(TAG, "onLatestPointCallback");

                //上传后立刻查询轨迹、里程，更新
                historyTrackRequest.setEndTime(CommonUtil.getCurrentTime());
                distanceRequest.setEndTime(CommonUtil.getCurrentTime());
                trackApp.mClient.queryHistoryTrack(historyTrackRequest, trackListener2);
                trackApp.mClient.queryDistance(distanceRequest, trackListener2);
            }
        };
        entityListener = new OnEntityListener() {
            /**
             * 查询实时位置
             */
            @Override
            public void onReceiveLocation(TraceLocation location) {

                if (StatusCodes.SUCCESS != location.getStatus() || CommonUtil.isZeroPoint(location.getLatitude(),
                        location.getLongitude())) {
                    return;
                }
                LatLng currentLatLng = mapUtil.convertTraceLocation2Map(location);
                if (null == currentLatLng) {
                    return;
                }
                CurrentLocation.locTime = CommonUtil.toTimeStamp(location.getTime());
                CurrentLocation.latitude = currentLatLng.latitude;
                CurrentLocation.longitude = currentLatLng.longitude;

                if (null != mapUtil) {
                    mapUtil.updateStatus(currentLatLng, true);
                }
            }

        };

        traceListener = new OnTraceListener() {

            /**
             * 绑定服务回调接口
             * @param errorNo  状态码
             * @param message 消息
             *                <p>
             *                <pre>0：成功 </pre>
             *                <pre>1：失败</pre>
             */
            @Override
            public void onBindServiceCallback(int errorNo, String message) {
                Log.e(TAG, "onBindServiceCallback  " + message);

                viewUtil.showToast(MainActivity.this,
                        String.format("onBindServiceCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            /**
             * 开启服务回调接口
             * @param errorNo 状态码
             * @param message 消息
             *                <p>
             *                <pre>0：成功 </pre>
             *                <pre>10000：请求发送失败</pre>
             *                <pre>10001：服务开启失败</pre>
             *                <pre>10002：参数错误</pre>
             *                <pre>10003：网络连接失败</pre>
             *                <pre>10004：网络未开启</pre>
             *                <pre>10005：服务正在开启</pre>
             *                <pre>10006：服务已开启</pre>
             */
            @Override
            public void onStartTraceCallback(int errorNo, String message) {
                if (StatusCodes.SUCCESS == errorNo || StatusCodes.START_TRACE_NETWORK_CONNECT_FAILED <= errorNo) {
                    Log.e(TAG, "onStartTraceCallback success" + message);
                    trackApp.isTraceStarted = true;
                    SharedPreferences.Editor editor = trackApp.trackConf.edit();
                    editor.putBoolean("is_trace_started", true);
                    editor.apply();
                    setTraceBtnStyle();
                    registerReceiver();
                }
                Log.e(TAG, "onStartTraceCallback" + message);

                viewUtil.showToast(MainActivity.this,
                        String.format("开启服务回调接口, errorNo:%d, message:%s ", errorNo, message));
            }

            /**
             * 停止服务回调接口
             * @param errorNo 状态码
             * @param message 消息
             *                <p>
             *                <pre>0：成功</pre>
             *                <pre>11000：请求发送失败</pre>
             *                <pre>11001：服务停止失败</pre>
             *                <pre>11002：服务未开启</pre>
             *                <pre>11003：服务正在停止</pre>
             */
            @Override
            public void onStopTraceCallback(int errorNo, String message) {
                Log.e(TAG, "onStopTraceCallback" + message);

                if (StatusCodes.SUCCESS == errorNo || StatusCodes.CACHE_TRACK_NOT_UPLOAD == errorNo) {
                    trackApp.isTraceStarted = false;
                    trackApp.isGatherStarted = false;
                    // 停止成功后，直接移除is_trace_started记录（便于区分用户没有停止服务，直接杀死进程的情况）
                    SharedPreferences.Editor editor = trackApp.trackConf.edit();
                    editor.remove("is_trace_started");
                    editor.remove("is_gather_started");
                    editor.apply();
                    setTraceBtnStyle();
                    setGatherBtnStyle();
                    unregisterPowerReceiver();
                }
                viewUtil.showToast(MainActivity.this,
                        String.format("onStopTraceCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            /**
             * 开启采集回调接口
             * @param errorNo 状态码
             * @param message 消息
             *                <p>
             *                <pre>0：成功</pre>
             *                <pre>12000：请求发送失败</pre>
             *                <pre>12001：采集开启失败</pre>
             *                <pre>12002：服务未开启</pre>
             */
            @Override
            public void onStartGatherCallback(int errorNo, String message) {
                Log.e(TAG, "onStartGatherCallback " + message);

                if (StatusCodes.SUCCESS == errorNo || StatusCodes.GATHER_STARTED == errorNo) {
                    trackApp.isGatherStarted = true;
                    SharedPreferences.Editor editor = trackApp.trackConf.edit();
                    editor.putBoolean("is_gather_started", true);
                    editor.apply();
                    setGatherBtnStyle();
                }
                viewUtil.showToast(MainActivity.this,
                        String.format("onStartGatherCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            /**
             * 停止采集回调接口
             * @param errorNo 状态码
             * @param message 消息
             *                <p>
             *                <pre>0：成功</pre>
             *                <pre>13000：请求发送失败</pre>
             *                <pre>13001：采集停止失败</pre>
             *                <pre>13002：服务未开启</pre>
             */
            @Override
            public void onStopGatherCallback(int errorNo, String message) {
                Log.e(TAG, "onStopGatherCallback" + message);

                if (StatusCodes.SUCCESS == errorNo || StatusCodes.GATHER_STOPPED == errorNo) {
                    trackApp.isGatherStarted = false;
                    SharedPreferences.Editor editor = trackApp.trackConf.edit();
                    editor.remove("is_gather_started");
                    editor.apply();
                    setGatherBtnStyle();
                }
                viewUtil.showToast(MainActivity.this,
                        String.format("onStopGatherCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            /**
             * 推送消息回调接口
             *
             * @param messageType 状态码
             * @param pushMessage 消息
             *                  <p>
             *                  <pre>0x01：配置下发</pre>
             *                  <pre>0x02：语音消息</pre>
             *                  <pre>0x03：服务端围栏报警消息</pre>
             *                  <pre>0x04：本地围栏报警消息</pre>
             *                  <pre>0x05~0x40：系统预留</pre>
             *                  <pre>0x41~0xFF：开发者自定义</pre>
             */
            @Override
            public void onPushCallback(byte messageType, PushMessage pushMessage) {
                viewUtil.showToast(MainActivity.this,"推送消息回调");
if (messageType < 0x03 || messageType > 0x04) {
                    viewUtil.showToast(MainActivity.this, pushMessage.getMessage());
                    return;
                }
                FenceAlarmPushInfo alarmPushInfo = pushMessage.getFenceAlarmPushInfo();
                if (null == alarmPushInfo) {
                    viewUtil.showToast(MainActivity.this,
                            String.format("onPushCallback, messageType:%d, messageContent:%s ", messageType,
                                    pushMessage));
                    return;
                }
                StringBuffer alarmInfo = new StringBuffer();
                alarmInfo.append("您于")
                        .append(CommonUtil.getHMS(alarmPushInfo.getCurrentPoint().getLocTime() * 1000))
                        .append(alarmPushInfo.getMonitoredAction() == MonitoredAction.enter ? "进入" : "离开")
                        .append(messageType == 0x03 ? "云端" : "本地")
                        .append("围栏：").append(alarmPushInfo.getFenceName());

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                    Notification notification = new Notification.Builder(trackApp)
                            .setContentTitle("轨迹记录查询通知")
                            .setContentText(alarmInfo.toString())
                            .setSmallIcon(R.mipmap.icon_app)
                            .setWhen(System.currentTimeMillis()).build();
                    notificationManager.notify(notifyId++, notification);
                }
            }

            @Override
            public void onInitBOSCallback(int errorNo, String message) {
                viewUtil.showToast(MainActivity.this,
                        String.format("onInitBOSCallback, errorNo:%d, message:%s ", errorNo, message));
            }
        };
    }

    private static final String TAG = "map";

    /**
     * 实时定位任务
     *
     * @author baidu
     */
    class RealTimeLocRunnable implements Runnable {

        private int interval = 0;

        public RealTimeLocRunnable(int interval) {
            this.interval = interval;
        }

        @Override
        public void run() {
            if (isRealTimeRunning) {
                trackApp.getCurrentLocation(entityListener, trackListener);
                realTimeHandler.postDelayed(this, interval * 1000);
            }
        }
    }


    /**
     * 设置服务按钮样式
     */
    private void setTraceBtnStyle() {
        boolean isTraceStarted = trackApp.trackConf.getBoolean("is_trace_started", false);
        if (isTraceStarted) {
            traceBtn.setText(R.string.stop_trace);
            traceBtn.setTextColor(ResourcesCompat.getColor(getResources(), R.color
                    .white, null));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                traceBtn.setBackground(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_sure, null));
            } else {
                traceBtn.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_sure, null));
            }
        } else {
            traceBtn.setText(R.string.start_trace);
            traceBtn.setTextColor(ResourcesCompat.getColor(getResources(), R.color.layout_title, null));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                traceBtn.setBackground(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_cancel, null));
            } else {
                traceBtn.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_cancel, null));
            }
        }
    }

    /**
     * 设置采集按钮样式
     */
    private void setGatherBtnStyle() {
        boolean isGatherStarted = trackApp.trackConf.getBoolean("is_gather_started", false);
        if (isGatherStarted) {
            gatherBtn.setText(R.string.stop_gather);
            gatherBtn.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                gatherBtn.setBackground(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_sure, null));
            } else {
                gatherBtn.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_sure, null));
            }
        } else {
            gatherBtn.setText(R.string.start_gather);
            gatherBtn.setTextColor(ResourcesCompat.getColor(getResources(), R.color.layout_title, null));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                gatherBtn.setBackground(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_cancel, null));
            } else {
                gatherBtn.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(),
                        R.mipmap.bg_btn_cancel, null));
            }
        }
    }

    /**
     * 注册广播（电源锁、GPS状态）
     */
    @SuppressLint("InvalidWakeLockTag")
    private void registerReceiver() {
        if (trackApp.isRegisterReceiver) {
            return;
        }

        if (null == wakeLock) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "track upload");
        }
        if (null == trackReceiver) {
            trackReceiver = new TrackReceiver(wakeLock);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(StatusCodes.GPS_STATUS_ACTION);
        trackApp.registerReceiver(trackReceiver, filter);
        trackApp.isRegisterReceiver = true;

    }

    private void unregisterPowerReceiver() {
        if (!trackApp.isRegisterReceiver) {
            return;
        }
        if (null != trackReceiver) {
            trackApp.unregisterReceiver(trackReceiver);
        }
        trackApp.isRegisterReceiver = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRealTimeLoc(packInterval);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapUtil.onResume();

        // 在Android 6.0及以上系统，若定制手机使用到doze模式，请求将应用添加到白名单。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = trackApp.getPackageName();
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapUtil.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRealTimeLoc();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapUtil.clear();
        stopRealTimeLoc();
//        CommonUtil.saveCurrentLocation(trackApp);
//        if (trackApp.trackConf.contains("is_trace_started")
//                && trackApp.trackConf.getBoolean("is_trace_started", true)) {
//            // 退出app停止轨迹服务时，不再接收回调，将OnTraceListener置空
//            trackApp.mClient.setOnTraceListener(null);
//            trackApp.mClient.stopTrace(trackApp.mTrace, null);
//        } else {
//            trackApp.mClient.clear();
//        }
//        trackApp.isTraceStarted = false;
//        trackApp.isGatherStarted = false;
//        SharedPreferences.Editor editor = trackApp.trackConf.edit();
//        editor.remove("is_trace_started");
//        editor.remove("is_gather_started");
//        editor.apply();
    }

}
