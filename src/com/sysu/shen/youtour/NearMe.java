package com.sysu.shen.youtour;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMap.OnMapClickListener;
import com.amap.api.maps.AMap.OnMarkerClickListener;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.SupportMapFragment;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.LatLngBounds.Builder;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amapv2.cn.apis.util.AMapUtil;
import com.sysu.shen.util.GlobalConst;
import com.sysu.shen.util.JSONFunctions;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

public class NearMe extends FragmentActivity implements LocationSource,
		AMapLocationListener, OnMarkerClickListener, OnMapClickListener {

	private AMap aMap;
	private OnLocationChangedListener mListener;
	private LocationManagerProxy mAMapLocationManager;
	private Double currentgeoLat;
	private Double currentgeoLng;
	private String cityCode = "";
	private String desc = "";
	public final static int NO_NETWORK = 0;
	private final int LOCACHANGE = 1;
	private final int LOADING = 2;
	private final int LOADED = 3;
	private String URLString = "";
	private String URLStringBegin = "beg=";
	private String URLStringEnd = "end=";
	private JSONArray jarray = null;
	private Timer mTimer = new Timer();
	private Boolean needReshLocation = true;
	private ProgressDialog mProgressDialog;

	private ArrayList<LatLng> markersList = new ArrayList<LatLng>();
	private UiSettings mUiSettings;
	
	PopupWindow mPopupWindow;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nearme_map);
		init();
		// 定时过5分钟后再从新定位
		mTimer.schedule(mTimerTask, 0, 5 * 60 * 1000);
	}

	/*******************************************************
	 * 通过定时器和Handler来改变是否需要更新位置
	 ******************************************************/
	TimerTask mTimerTask = new TimerTask() {
		@Override
		public void run() {
			needReshLocation = true;
		}
	};

	public boolean enableMyLocation() {
		boolean result = false;
		if (mAMapLocationManager
				.isProviderEnabled(LocationProviderProxy.AMapNetwork)) {
			mAMapLocationManager.requestLocationUpdates(
					LocationProviderProxy.AMapNetwork, 2000, 10, this);
			result = true;
		}
		return result;
	}

	private void init() {
		if (aMap == null) {
			aMap = ((SupportMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.map)).getMap();
			if (AMapUtil.checkReady(this, aMap)) {
				setUpMap();
			}
		}

	}

	private void setUpMap() {
		mUiSettings = aMap.getUiSettings();
		mUiSettings.setAllGesturesEnabled(true);
		mUiSettings.setCompassEnabled(true);
		mUiSettings.setMyLocationButtonEnabled(true);
		mUiSettings.setScaleControlsEnabled(true);
		mUiSettings.setZoomControlsEnabled(true);
		mAMapLocationManager = LocationManagerProxy.getInstance(NearMe.this);
		aMap.setLocationSource(this);
		aMap.setMyLocationEnabled(true);
		aMap.setOnMarkerClickListener(this);

	}

	// 处理线程中抛出的massage
	private Handler mhandle = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case NO_NETWORK:
				Toast.makeText(NearMe.this, "连接网络才能看到哦！", Toast.LENGTH_LONG)
						.show();
				break;
			case LOCACHANGE:
				// URLString = GlobalConst.URL_HAEDER_LOC + "x="
				// + geoLng.toString() + "&y=" + geoLat.toString() + "&"
				// + URLStringBegin + "0" + "&" + URLStringEnd + "25";
				URLString = GlobalConst.URL_HAEDER_ALL + URLStringBegin + "0"
						+ "&" + URLStringEnd + "25";
				String str = ("定位成功:(" + currentgeoLng + "," + currentgeoLat
						+ ")" + "\n城市编码:" + cityCode + "\n位置描述:" + desc);
				Log.i("locatinfo", str);
				Log.i("nearmeurl", URLString);
				// 异步更新列表
				new GetJSONAsynTack(NearMe.this).execute(URLString);
				break;
			case LOADING:
				mProgressDialog = new ProgressDialog(NearMe.this);
				mProgressDialog.setTitle("正在查询附近线路"); // 设置标题
				mProgressDialog.setMessage("附近线路马上为你呈现，请耐心等待..."); // 设置body信息
				mProgressDialog.show();
				break;
			case LOADED:
				mProgressDialog.dismiss();
				break;

			default:
				break;
			}
			super.handleMessage(msg);
		}

	};

	private class GetJSONAsynTack extends AsyncTask<String, Void, Void> {

		public Activity activity;

		public GetJSONAsynTack(Activity activity2) {
			activity = activity2;
		}

		@Override
		protected Void doInBackground(String... strings) {
			Message m = new Message();
			m.what = LOADING;
			mhandle.sendMessage(m);
			String URLString = strings[0];
			// 判断是否联网
			final ConnectivityManager conMgr = (ConnectivityManager) activity
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
			if (activeNetwork != null && activeNetwork.isConnected()) {
				jarray = JSONFunctions.getJsonFromNetwork(activity, URLString);

			} else {
				Message m1 = new Message();
				m1.what = NO_NETWORK;
				mhandle.sendMessage(m1);
				jarray = JSONFunctions.getJSONFromFile(activity, URLString);
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			JSONObject line = null;
			if (jarray != null) {
				Log.v("log_tag", "jarray.length():" + jarray.length());
				try {
					for (int i = 0; i < jarray.length(); i++) {
						line = jarray.getJSONObject(i);
						double longitude = line.getJSONArray("locate")
								.getDouble(0);
						double latitude = line.getJSONArray("locate")
								.getDouble(1);
						LatLng marker = new LatLng(longitude, latitude);
						markersList.add(marker);
						aMap.addMarker(new MarkerOptions()
								.position(marker)
								.title(line.getString("lineName"))
								.snippet(line.getString("mapAddress") + "@" + i)
								.icon(BitmapDescriptorFactory
										.fromResource(R.drawable.line)));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				loadMarker();
			}
			Message m = new Message();
			m.what = LOADED;
			mhandle.sendMessage(m);
			super.onPostExecute(result);
		}
	}

	public void loadMarker() {
		// 设置所有maker显示在View中
		Builder builder = new Builder();
		for (int i = 0; i < markersList.size(); i++) {
			builder.include(markersList.get(i));
		}
		LatLngBounds bounds = builder.build();
		try {
			aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 20));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * 点击返回
	 * 
	 * @param v
	 */
	public void backClicked(View v) {
		mTimerTask.cancel();
		this.finish();
	}

	@Override
	protected void onPause() {
		super.onPause();
		deactivate();
	}

	@Override
	protected void onResume() {
		super.onResume();
		enableMyLocation();
	}

	@Override
	protected void onDestroy() {
		mListener = null;
		if (mAMapLocationManager != null) {
			mAMapLocationManager.removeUpdates(this);
			mAMapLocationManager.destory();
		}
		mAMapLocationManager = null;
		super.onDestroy();
	}

	@Override
	public void onLocationChanged(Location location) {
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLocationChanged(AMapLocation location) {
		if (mListener != null) {
			mListener.onLocationChanged(location);
		}
		if (location != null && needReshLocation) {
			currentgeoLat = location.getLatitude();
			currentgeoLng = location.getLongitude();
			Bundle locBundle = location.getExtras();
			if (locBundle != null) {
				cityCode = locBundle.getString("citycode");
				desc = locBundle.getString("desc");
			}
			Message m = new Message();
			m.what = LOCACHANGE;
			mhandle.sendMessage(m);
			needReshLocation = false;
		}

	}

	@Override
	public void activate(OnLocationChangedListener arg0) {
		mListener = arg0;
		if (mAMapLocationManager == null) {
			mAMapLocationManager = LocationManagerProxy.getInstance(this);
		}
		// 网络定位
		mAMapLocationManager.requestLocationUpdates(
				LocationProviderProxy.AMapNetwork, 10, 5000, this);
	}

	@Override
	public void deactivate() {
		mListener = null;
		if (mAMapLocationManager != null) {
			mAMapLocationManager.removeUpdates(this);
			mAMapLocationManager.destory();
		}
		mAMapLocationManager = null;

	}

	@Override
	public boolean onMarkerClick(Marker arg0) {
		Log.i("marker",
				"title:" + arg0.getTitle() + " snippet:" + arg0.getSnippet());
		Context mContext = NearMe.this;
		LayoutInflater mLayoutInflater = (LayoutInflater) mContext
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		View line_popunwindwow = mLayoutInflater.inflate(
				R.layout.map_popup_view, null);
		mPopupWindow = new PopupWindow(line_popunwindwow,
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

		mPopupWindow.showAtLocation(findViewById(R.id.nearmemap),
				Gravity.CENTER, 0, 0);
		mPopupWindow.setFocusable(true);

		return false;
	}

	@Override
	public void onBackPressed() {
		mTimerTask.cancel();
		super.onBackPressed();
	}

	@Override
	public void onMapClick(LatLng arg0) {
		mPopupWindow.dismiss();
		
	}

}