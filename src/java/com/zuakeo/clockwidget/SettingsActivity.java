package com.zuakeo.clockwidget;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 设置页（带桌面启动图标）。
 *
 * 只影响「桌面时钟 · 天气」。所有改动即时写入 SharedPreferences 并广播 RERENDER，
 * 组件那边只重排、不联网；换城市 / 手动坐标 / 点「立即刷新」才联网。
 *
 * 没引 AndroidX，整页用系统控件手搭：Switch / SeekBar / RadioGroup / EditText。
 */
public class SettingsActivity extends Activity {

    private static final int C_BG = 0xFF0F0F11;
    private static final int C_TEXT = 0xFFF2F2F2;
    private static final int C_SUB = 0x99FFFFFF;
    private static final int C_SECTION = 0xFF8AB4F8;
    private static final int C_ACCENT = 0xFF3D7EFF;

    private LinearLayout content;
    private TextView cityValue;
    private EditText latInput;
    private EditText lonInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);

        content = findViewById(R.id.settings_content);
        findViewById(R.id.settings_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        buildDisplayCard();
        buildFontCard();
        buildWeatherCard();
        buildAboutCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WeatherStore.Snapshot snapshot = WeatherStore.load(this);
        if (cityValue != null) {
            cityValue.setText(snapshot.city);
        }
        // 从「更换城市」回来时同步一下坐标；正在手输时不要覆盖用户输入。
        boolean editing = (latInput != null && latInput.hasFocus())
                || (lonInput != null && lonInput.hasFocus());
        if (!editing) {
            if (latInput != null) {
                latInput.setText(String.format(Locale.US, "%.5f", snapshot.lat));
            }
            if (lonInput != null) {
                lonInput.setText(String.format(Locale.US, "%.5f", snapshot.lon));
            }
        }
    }

    // ------------------------------------------------------------ 显示内容

    private void buildDisplayCard() {
        LinearLayout card = card(R.string.settings_section_display);
        switchRow(card, R.string.settings_date, R.string.settings_date_desc,
                WidgetSettings.K_DATE, true);
        switchRow(card, R.string.settings_weekday, R.string.settings_weekday_desc,
                WidgetSettings.K_WEEKDAY, true);
        switchRow(card, R.string.settings_lunar, R.string.settings_lunar_desc,
                WidgetSettings.K_LUNAR, true);
        switchRow(card, R.string.settings_icon, R.string.settings_icon_desc,
                WidgetSettings.K_ICON, true);
        switchRow(card, R.string.settings_temp, R.string.settings_temp_desc,
                WidgetSettings.K_TEMP, true);
        switchRow(card, R.string.settings_city, R.string.settings_city_desc,
                WidgetSettings.K_CITY, false);
        switchRow(card, R.string.settings_seconds, R.string.settings_seconds_desc,
                WidgetSettings.K_SECONDS, false);
    }

    // ------------------------------------------------------------ 字体

    private void buildFontCard() {
        LinearLayout card = card(R.string.settings_section_font);
        sliderRow(card, R.string.settings_time_size, WidgetSettings.K_TIME_SIZE,
                WidgetSettings.TIME_SIZE_MIN, WidgetSettings.TIME_SIZE_MAX, 62);
        sliderRow(card, R.string.settings_date_size, WidgetSettings.K_DATE_SIZE,
                WidgetSettings.DATE_SIZE_MIN, WidgetSettings.DATE_SIZE_MAX, 15);
        weightRow(card);
        textColorRow(card);
    }

    private void textColorRow(LinearLayout card) {
        beforeRow(card);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(10), 0, dp(2));
        card.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        block.addView(label(R.string.settings_text_color, 15, C_TEXT));
        block.addView(label(R.string.settings_text_color_desc, 11, C_SUB));

        final String[] values = {
                WidgetSettings.C_AUTO, WidgetSettings.C_WHITE, WidgetSettings.C_DARK,
        };
        int[] labels = {
                R.string.settings_color_auto, R.string.settings_color_white,
                R.string.settings_color_dark,
        };

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(4);
        block.addView(group, glp);

        String current = WidgetSettings.load(this).textColor;
        for (int i = 0; i < values.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(4000 + i);
            rb.setText(labels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            rb.setTextColor(C_TEXT);
            rb.setButtonTintList(ColorStateList.valueOf(C_ACCENT));
            rb.setPadding(0, 0, dp(4), 0);
            group.addView(rb, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (values[i].equals(current)) {
                group.check(4000 + i);
            }
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                int index = checkedId - 4000;
                if (index < 0 || index >= values.length) {
                    return;
                }
                WidgetSettings.putString(SettingsActivity.this,
                        WidgetSettings.K_TEXT_COLOR, values[index]);
                WidgetSettings.notifyChanged(SettingsActivity.this);
            }
        });
    }

    private void weightRow(LinearLayout card) {
        beforeRow(card);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(10), 0, dp(2));
        card.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        block.addView(label(R.string.settings_weight, 15, C_TEXT));

        final String[] values = {
                WidgetSettings.W_LIGHT, WidgetSettings.W_NORMAL,
                WidgetSettings.W_MEDIUM, WidgetSettings.W_BOLD,
        };
        int[] labels = {
                R.string.settings_weight_light, R.string.settings_weight_normal,
                R.string.settings_weight_medium, R.string.settings_weight_bold,
        };

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(4);
        block.addView(group, glp);

        String current = WidgetSettings.load(this).weight;
        for (int i = 0; i < values.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(2000 + i);
            rb.setText(labels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            rb.setTextColor(C_TEXT);
            rb.setButtonTintList(ColorStateList.valueOf(C_ACCENT));
            rb.setPadding(0, 0, dp(4), 0);
            group.addView(rb, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (values[i].equals(current)) {
                group.check(2000 + i);
            }
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                int index = checkedId - 2000;
                if (index < 0 || index >= values.length) {
                    return;
                }
                WidgetSettings.putString(SettingsActivity.this,
                        WidgetSettings.K_WEIGHT, values[index]);
                WidgetSettings.notifyChanged(SettingsActivity.this);
            }
        });
    }

    private void sliderRow(LinearLayout card, int titleRes, final String key,
                           final int min, final int max, int defValue) {
        beforeRow(card);
        final int value = clamp(WidgetSettings.getInt(this, key, defValue), min, max);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(10), 0, dp(2));
        card.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        block.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        head.addView(label(titleRes, 15, C_TEXT), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueText = new TextView(this);
        valueText.setText(value + "sp");
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueText.setTextColor(C_ACCENT);
        head.addView(valueText);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setProgressTintList(ColorStateList.valueOf(C_ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(C_ACCENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        blp.topMargin = dp(2);
        block.addView(bar, blp);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText((min + progress) + "sp");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                WidgetSettings.putInt(SettingsActivity.this, key, min + seekBar.getProgress());
                WidgetSettings.notifyChanged(SettingsActivity.this);
            }
        });
    }

    // ------------------------------------------------------------ 天气

    private void buildWeatherCard() {
        LinearLayout card = card(R.string.settings_section_weather);

        // 当前城市 + 更换
        beforeRow(card);
        LinearLayout cityRow = new LinearLayout(this);
        cityRow.setOrientation(LinearLayout.HORIZONTAL);
        cityRow.setGravity(Gravity.CENTER_VERTICAL);
        cityRow.setPadding(0, dp(10), 0, dp(10));
        card.addView(cityRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout cityTexts = new LinearLayout(this);
        cityTexts.setOrientation(LinearLayout.VERTICAL);
        cityRow.addView(cityTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cityTexts.addView(label(R.string.settings_city_title, 15, C_TEXT));

        cityValue = new TextView(this);
        cityValue.setText(WeatherStore.load(this).city);
        cityValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cityValue.setTextColor(C_SUB);
        cityTexts.addView(cityValue);

        Button change = smallButton(R.string.settings_city_change);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        clp.setMarginStart(dp(8));
        cityRow.addView(change, clp);
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, CityPickerActivity.class));
            }
        });

        // 手动坐标
        beforeRow(card);
        LinearLayout coordBlock = new LinearLayout(this);
        coordBlock.setOrientation(LinearLayout.VERTICAL);
        coordBlock.setPadding(0, dp(10), 0, dp(4));
        card.addView(coordBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        coordBlock.addView(label(R.string.settings_coord_title, 15, C_TEXT));
        coordBlock.addView(label(R.string.settings_coord_desc, 11, C_SUB));

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        inputs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(6);
        coordBlock.addView(inputs, ilp);

        WeatherStore.Snapshot snapshot = WeatherStore.load(this);
        latInput = coordInput(getString(R.string.settings_lat_hint),
                String.format(Locale.US, "%.5f", snapshot.lat));
        lonInput = coordInput(getString(R.string.settings_lon_hint),
                String.format(Locale.US, "%.5f", snapshot.lon));

        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(44), 1f);
        inputs.addView(latInput, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        half2.setMarginStart(dp(8));
        inputs.addView(lonInput, half2);

        Button save = smallButton(R.string.settings_coord_save);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        slp.setMarginStart(dp(8));
        inputs.addView(save, slp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCoords();
            }
        });

        // 温度单位
        beforeRow(card);
        LinearLayout unitBlock = new LinearLayout(this);
        unitBlock.setOrientation(LinearLayout.VERTICAL);
        unitBlock.setPadding(0, dp(10), 0, dp(2));
        card.addView(unitBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        unitBlock.addView(label(R.string.settings_unit, 15, C_TEXT));

        RadioGroup units = new RadioGroup(this);
        units.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton c = unitButton(R.string.settings_unit_c, 3000);
        RadioButton f = unitButton(R.string.settings_unit_f, 3001);
        units.addView(c, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        units.addView(f, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        units.check(WidgetSettings.load(this).fahrenheit ? 3001 : 3000);
        units.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                WidgetSettings.putBool(SettingsActivity.this,
                        WidgetSettings.K_FAHRENHEIT, checkedId == 3001);
                WidgetSettings.notifyChanged(SettingsActivity.this);
            }
        });
        unitBlock.addView(units, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 立即刷新
        beforeRow(card);
        Button refresh = new Button(this);
        refresh.setText(R.string.settings_refresh);
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        refresh.setTextColor(0xFFFFFFFF);
        refresh.setBackgroundTintList(ColorStateList.valueOf(C_ACCENT));
        refresh.setAllCaps(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rlp.topMargin = dp(12);
        card.addView(refresh, rlp);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClockWeatherWidgetProvider.requestRefresh(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                        R.string.settings_refreshing, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveCoords() {
        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latInput.getText().toString().trim());
            lon = Double.parseDouble(lonInput.getText().toString().trim());
        } catch (Throwable t) {
            Toast.makeText(this, R.string.settings_coord_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Toast.makeText(this, R.string.settings_coord_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = getString(R.string.settings_city_custom);
        WeatherStore.saveCity(this, name, lat, lon);
        if (cityValue != null) {
            cityValue.setText(name);
        }
        hideKeyboard();
        ClockWeatherWidgetProvider.requestRefresh(this);
        Toast.makeText(this, R.string.settings_coord_saved, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ 说明

    private void buildAboutCard() {
        LinearLayout card = card(R.string.settings_section_about);

        TextView body = new TextView(this);
        body.setText(R.string.settings_about);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setTextColor(C_SUB);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.addView(body, lp);

        TextView version = new TextView(this);
        version.setText(R.string.settings_version);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        version.setTextColor(0x66FFFFFF);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(10);
        card.addView(version, vlp);
    }

    // ------------------------------------------------------------ 控件工厂

    private LinearLayout card(int titleRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_settings_card);
        int pad = dp(16);
        card.setPadding(pad, dp(14), pad, dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        content.addView(card, lp);

        TextView title = label(titleRes, 13, C_SECTION);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.04f);
        card.addView(title);
        return card;
    }

    private void switchRow(LinearLayout card, int titleRes, int descRes,
                           final String key, final boolean defValue) {
        beforeRow(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(label(titleRes, 15, C_TEXT));
        texts.addView(label(descRes, 11, C_SUB));

        Switch sw = new Switch(this);
        sw.setChecked(WidgetSettings.getBool(this, key, defValue));
        sw.setThumbTintList(ColorStateList.valueOf(C_ACCENT));
        sw.setTrackTintList(ColorStateList.valueOf(0x663D7EFF));
        row.addView(sw);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                WidgetSettings.putBool(SettingsActivity.this, key, isChecked);
                WidgetSettings.notifyChanged(SettingsActivity.this);
            }
        });
    }

    private EditText coordInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        input.setTextColor(C_TEXT);
        input.setHintTextColor(0x66FFFFFF);
        input.setBackgroundResource(R.drawable.bg_city_input);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return input;
    }

    private RadioButton unitButton(int textRes, int id) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(textRes);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rb.setTextColor(C_TEXT);
        rb.setButtonTintList(ColorStateList.valueOf(C_ACCENT));
        return rb;
    }

    private Button smallButton(int textRes) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(ColorStateList.valueOf(C_ACCENT));
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        return button;
    }

    private TextView label(int textRes, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(textRes);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        return text;
    }

    private void beforeRow(LinearLayout card) {
        if (card.getChildCount() <= 1) {
            return;
        }
        View divider = new View(this);
        divider.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        card.addView(divider, lp);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
