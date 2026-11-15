package com.zuakeo.clockwidget;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 选天气城市。
 *
 * 入口：组件上的天气区域（温度/图标）。没有启动图标，也不出现在最近任务里。
 * 搜索走 Open-Meteo 的 geocoding 接口，中英文都能搜（"Beijing" / "北京"）。
 * 选中后写入 SharedPreferences，并立刻广播刷新组件。
 */
public class CityPickerActivity extends Activity {

    private EditText input;
    private Button search;
    private TextView status;
    private ListView list;
    private PlaceAdapter adapter;

    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_picker);

        // 对话框宽度：平板/手机上都不超过 360dp，也不超过屏宽的 92%。
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = (int) Math.min(360 * dm.density, dm.widthPixels * 0.92f);
            getWindow().setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        } catch (Throwable ignored) {
        }

        input = findViewById(R.id.cp_input);
        search = findViewById(R.id.cp_search);
        status = findViewById(R.id.cp_status);
        list = findViewById(R.id.cp_list);

        TextView current = findViewById(R.id.cp_current);
        WeatherStore.Snapshot snapshot = WeatherStore.load(this);
        current.setText(getString(R.string.city_current, snapshot.city));

        adapter = new PlaceAdapter();
        list.setAdapter(adapter);

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position,
                                    long id) {
                WeatherClient.Place place = adapter.getItem(position);
                if (place == null) {
                    return;
                }
                WeatherStore.saveCity(CityPickerActivity.this, place.name, place.lat, place.lon);
                Toast.makeText(CityPickerActivity.this,
                        getString(R.string.city_saved, place.name), Toast.LENGTH_SHORT).show();
                ClockWeatherWidgetProvider.requestRefresh(CityPickerActivity.this);
                finish();
            }
        });
    }

    private void doSearch() {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.city_need_keyword);
            return;
        }

        hideKeyboard();
        search.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.city_searching);
        adapter.setItems(new ArrayList<WeatherClient.Place>());

        final String query = keyword;
        android.util.Log.i("ClockCity", "开始搜索: " + query + " pid=" + android.os.Process.myPid());
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<WeatherClient.Place> result = null;
                String error = null;
                try {
                    result = WeatherClient.search(query);
                } catch (Throwable t) {
                    error = t.toString();
                }
                final List<WeatherClient.Place> places = result;
                final String failure = error;
                android.util.Log.i("ClockCity", "搜索返回: "
                        + (places == null ? "null" : places.size() + " 条")
                        + (failure == null ? "" : " 失败=" + failure));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        android.util.Log.i("ClockCity", "刷新界面 destroyed=" + destroyed);
                        if (destroyed) {
                            return;
                        }
                        search.setEnabled(true);
                        if (failure != null) {
                            status.setText(getString(R.string.city_network_error, failure));
                            return;
                        }
                        adapter.setItems(places);
                        if (places == null || places.isEmpty()) {
                            status.setText(R.string.city_no_result);
                        } else {
                            status.setText(getString(R.string.city_result_count, places.size()));
                        }
                    }
                });
            }
        }, "city-search").start();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    /** 城市候选列表。 */
    private class PlaceAdapter extends BaseAdapter {

        private final List<WeatherClient.Place> items = new ArrayList<>();
        private final LayoutInflater inflater = LayoutInflater.from(CityPickerActivity.this);

        void setItems(List<WeatherClient.Place> places) {
            items.clear();
            if (places != null) {
                items.addAll(places);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public WeatherClient.Place getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item_city, parent, false);
            }
            WeatherClient.Place place = items.get(position);
            TextView name = view.findViewById(R.id.item_city_name);
            TextView sub = view.findViewById(R.id.item_city_sub);
            name.setText(place.name);
            String subtitle = place.subtitle();
            sub.setText(subtitle);
            sub.setVisibility(subtitle.length() == 0 ? View.GONE : View.VISIBLE);
            return view;
        }
    }
}
