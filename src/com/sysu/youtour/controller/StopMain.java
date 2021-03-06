package com.sysu.youtour.controller;

import java.io.File;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.sysu.shen.youtour.R;
import com.sysu.youtour.util.GlobalConst;
import com.sysu.youtour.util.Player;
import com.viewpagerindicator.CirclePageIndicator;

public class StopMain extends BaseSampleActivity {
    private String                      stopslistString;

    private String                      stopNameString;

    private String                      stopDetailString;

    private JSONArray                   stopsJSONArray;

    private JSONObject                  stopJSON;

    private TextView                    stopName;

    private TextView                    stopDetail;

    private String                      position;

    private TextView                    stopNum;

    private ArrayList<String>           stopImagesArray;

    private JSONArray                   stopImages;

    private ImageButton                 btn_play;

    private SeekBar                     skbProgress;

    private Player                      player;

    private ProgressDialog              mProgressDialog;

    private final int                   LOADING    = 0;

    private final int                   LOADED     = 1;

    private Boolean                     firstClick = true;

    private ImageButton                 preBtn;

    private ImageButton                 nextBtn;

    private TextView                    songCurrentDurationLabel;

    private TextView                    songTotalDurationLabel;

    private Boolean                     haveAudio  = true;

    private double                      longitude;

    private double                      latitude;

    private AsyncTask<Void, Void, Void> task;

    private String                      audioURL;

    private String                      lineID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stop_main);
        Bundle extras = getIntent().getExtras();
        stopslistString = extras.getString("stopsJarray");
        position = extras.getString("position");
        lineID = extras.getString("lineID");

        initView();
        initValue();

        mAdapter = new StopMainFragmentAdapter(getSupportFragmentManager(), stopImagesArray, lineID);

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);

        CirclePageIndicator indicator = (CirclePageIndicator) findViewById(R.id.indicator);
        mIndicator = indicator;
        indicator.setViewPager(mPager);
        indicator.setSnap(true);
    }

    private void initView() {
        stopName = (TextView) findViewById(R.id.stop_name);
        stopDetail = (TextView) findViewById(R.id.stop_detail);
        stopNum = (TextView) findViewById(R.id.number);
        btn_play = (ImageButton) findViewById(R.id.btn_play);
        skbProgress = (SeekBar) findViewById(R.id.songProgressBar);
        skbProgress.setOnSeekBarChangeListener(new SeekBarChangeEvent());
        preBtn = (ImageButton) findViewById(R.id.preBtton);
        nextBtn = (ImageButton) findViewById(R.id.nextBtton);
        songCurrentDurationLabel = (TextView) findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) findViewById(R.id.songTotalDurationLabel);
    }

    private void initValue() {
        try {
            stopsJSONArray = new JSONArray(stopslistString);
            if (Integer.parseInt(position) == 1) {
                preBtn.setBackgroundColor(this.getResources().getColor(R.color.orangetransparent));
            }
            if (Integer.parseInt(position) == stopsJSONArray.length()) {
                nextBtn.setBackgroundColor(this.getResources().getColor(R.color.orangetransparent));
            }
            stopJSON = stopsJSONArray.getJSONObject(Integer.parseInt(position) - 1);
            stopNameString = stopJSON.getString("stopName");
            stopDetailString = stopJSON.getString("stopDes");
            stopImages = stopJSON.getJSONArray("stopImages");
            audioURL = stopJSON.getString("stopAudio");
            audioURL = GlobalConst.HOST + audioURL;
            longitude = stopJSON.getJSONArray("locate").getDouble(0);
            latitude = stopJSON.getJSONArray("locate").getDouble(1);
            Log.i("audioURLorigin: ", audioURL);
            // 如果没有音频链接则让播放控件失效
            if (audioURL.equals("")) {
                btn_play.setImageResource(R.drawable.play);
                haveAudio = false;
                skbProgress.setEnabled(false);
            }
            // audioURL = GlobalConst.HOST + audsioURL;
            Log.i("audioURL: ", audioURL);
            stopImagesArray = new ArrayList<String>();

            for (int i = 0; i < stopImages.length(); i++) {
                stopImagesArray.add(stopImages.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        stopName.setText(stopNameString);
        stopDetail.setText(stopDetailString);
        stopNum.setText(position);

        player = new Player(audioURL, skbProgress, songTotalDurationLabel, songCurrentDurationLabel, lineID);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new MyPhoneListener(), PhoneStateListener.LISTEN_CALL_STATE);
    }

    // 处理线程中抛出的massage
    private Handler mhandle = new Handler() {

                                @Override
                                public void handleMessage(Message msg) {
                                    switch (msg.what) {
                                        case LOADING:
                                            mProgressDialog = new ProgressDialog(StopMain.this);
                                            mProgressDialog.setTitle("正在加载音频…"); // 设置标题
                                            mProgressDialog.setMessage("请稍等"); // 设置body信息
                                            mProgressDialog.show();
                                            break;
                                        case LOADED:
                                            btn_play.setImageResource(R.drawable.pause_w);
                                            mProgressDialog.dismiss();
                                            break;
                                        default:
                                            break;
                                    }
                                    super.handleMessage(msg);
                                }

                            };

    /**
     * 只有电话来了之后才暂停音乐的播放
     */
    private final class MyPhoneListener extends android.telephony.PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:// 电话来了
                    player.callIsComing();
                    break;
                case TelephonyManager.CALL_STATE_IDLE: // 通话结束
                    player.callIsDown();
                    break;
            }
        }
    }

    class SeekBarChangeEvent implements SeekBar.OnSeekBarChangeListener {
        int progress;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            this.progress = progress * player.mediaPlayer.getDuration() / seekBar.getMax();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // seekTo()的参数是相对与影片时间的数字，而不是与seekBar.getMax()相对的数字
            player.mediaPlayer.seekTo(progress);
        }
    }

    private class PlayMusicAsynTack extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Message m = new Message();
            m.what = LOADING;
            mhandle.sendMessage(m);
            player.play();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Message m = new Message();
            m.what = LOADED;
            mhandle.sendMessage(m);
            super.onPostExecute(result);
        }

    }

    /**
     * 点击返回
     * 
     * @param v
     */
    public void backClicked(View v) {
        player.stop();
        if (task != null) {
            task.cancel(true);
        }
        this.finish();
    }

    /**
     * 点击地图
     * 
     * @param v
     */
    public void mapclicked(View v) {
        // Uri mUri = Uri.parse("geo:"+latitude+","+longitude);
        // Log.i("geo", "geo:"+longitude+","+latitude);
        // Intent mIntent = new Intent(Intent.ACTION_VIEW,mUri);
        // startActivity(mIntent);
    }

    /**
     * 点击前一个站点
     * 
     * @param v
     */
    public void prebuttonClicked(View v) {
        if (Integer.parseInt(position) == 1) {
            Toast.makeText(this, "已经是第一个站点了", Toast.LENGTH_SHORT).show();
        } else {
            Intent it = new Intent(StopMain.this, StopMain.class);
            it.putExtra("stopsJarray", stopslistString);
            it.putExtra("position", (Integer.parseInt(position) - 1) + "");
            it.putExtra("lineID", lineID);
            startActivity(it);
            player.stop();
            if (task != null) {
                task.cancel(true);
            }
            this.finish();
            this.overridePendingTransition(R.anim.slide_out_right, R.anim.slide_in_left);
        }
    }

    /**
     * 点击后一个站点
     * 
     * @param v
     */
    public void nextbuttonClicked(View v) {
        if (Integer.parseInt(position) == stopsJSONArray.length()) {
            Toast.makeText(this, "已经是最后一个站点了", Toast.LENGTH_SHORT).show();
        } else {
            Intent it = new Intent(StopMain.this, StopMain.class);
            it.putExtra("stopsJarray", stopslistString);
            it.putExtra("position", (Integer.parseInt(position) + 1) + "");
            it.putExtra("lineID", lineID);
            startActivity(it);
            player.stop();
            if (task != null) {
                task.cancel(true);
            }
            this.finish();
            this.overridePendingTransition(R.anim.slide_out_left, R.anim.slide_in_right);
        }
    }

    /**
     * 点击分享
     * 
     * @param v
     */
    public void shareclicked(View v) {

    }

    /**
     * 点击音乐播放
     * 
     * @param v
     */
    public void playMusic(View v) {
        if (!haveAudio) {
            Toast.makeText(this, "这个站点没有音频信息哦", Toast.LENGTH_SHORT).show();
        } else {

            if (firstClick) {
                File stopAudioFile = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                        + GlobalConst.SDCARD_CACHE_DIR + "/" + lineID.hashCode() + "/" + audioURL.hashCode());
                if (stopAudioFile.exists()) {
                    task = new PlayMusicAsynTack();
                    task.execute();
                    firstClick = false;
                } else {
                    // 判断是否wifi环境
                    ConnectivityManager connManager = (ConnectivityManager) this
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo mobileCon = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                    NetworkInfo netInfo = connManager.getActiveNetworkInfo();
                    if (mobileCon.isConnected()) {
                        // 使用移动网络连接
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setIcon(android.R.drawable.ic_dialog_info);
                        builder.setTitle("设置WIFI更流畅");
                        builder.setMessage("小游检测到您正使用移动网络，设置WIFI音频更流畅哦，还是设置一下WIFI吧！");
                        builder.setPositiveButton("马上设置", new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                                final ComponentName cn = new ComponentName("com.android.settings",
                                        "com.android.settings.wifi.WifiSettings");
                                intent.setComponent(cn);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        });
                        builder.setNegativeButton("继续使用移动网络", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                task = new PlayMusicAsynTack();
                                task.execute();
                                // new PlayMusicAsynTack().execute();
                                firstClick = false;
                            }
                        });
                        builder.create();
                        builder.show();
                    } else if (netInfo == null || !netInfo.isConnectedOrConnecting()) {

                    } else {
                        // 使用非移动网络
                        // new PlayMusicAsynTack().execute();
                        task = new PlayMusicAsynTack();
                        task.execute();
                        firstClick = false;
                    }

                }
            } else {
                boolean pause = player.pause();
                if (pause) {
                    btn_play.setImageResource(R.drawable.play_w);
                } else {
                    btn_play.setImageResource(R.drawable.pause_w);
                }
            }
        }

    }

    @Override
    public void onBackPressed() {
        player.stop();
        if (task != null) {
            task.cancel(true);
        }
        this.finish();
        super.onBackPressed();
    }

}