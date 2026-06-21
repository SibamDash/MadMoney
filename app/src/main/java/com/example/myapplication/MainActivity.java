package com.example.myapplication;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static boolean isRecreatingForTheme = false;
    private static boolean sDrawerOpen = false;
    private static boolean sCustomizeExpanded = false;
    private static boolean sBackupExpanded = false;
    private static boolean sBudgetExpanded = false;

    private DailyFragment dailyFragment;
    private CalendarFragment calendarFragment;
    private long pendingMultiSelectFolderId = -1L;
    private TrendsFragment trendsFragment;
    private ViewPager2 viewPager;
    private long backPressedTime = 0L;
    private String budgetMode = "weekly"; // "weekly", "monthly", "custom"
    public List<Transaction> lastSummaryData = Arrays.asList();

    private final ActivityResultLauncher<Intent> addTransactionLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (dailyFragment != null) dailyFragment.load();
            if (calendarFragment != null && calendarFragment.isAdded()) calendarFragment.reload();
            if (trendsFragment != null && trendsFragment.isAdded()) trendsFragment.reload();
        }
    );

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/json"),
        uri -> {
            if (uri == null) return;
            BackupManager.exportToJson(this, uri).fold(
                count -> Toast.makeText(this, "Exported " + count + " transactions", Toast.LENGTH_SHORT).show(),
                it -> Toast.makeText(this, "Export failed: " + it.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
            if (uri == null) return;
            BackupManager.importFromJson(this, uri).fold(
                count -> {
                    Toast.makeText(this, "Imported " + count + " transactions", Toast.LENGTH_SHORT).show();
                    if (dailyFragment != null) dailyFragment.load();
                },
                it -> Toast.makeText(this, "Import failed: " + it.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int targetMode = prefs.getBoolean("dark_mode", false) ? AppCompatDelegate.MODE_NIGHT_YES
                                                              : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        dailyFragment = new DailyFragment();
        calendarFragment = new CalendarFragment();
        trendsFragment = new TrendsFragment();

        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            private final List<Fragment> fragments = Arrays.asList(
                dailyFragment,
                calendarFragment,
                new ChartFragment(),
                new BudgetFragment(),
                trendsFragment
            );

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return fragments.get(position);
            }

            @Override
            public int getItemCount() {
                return fragments.size();
            }
        });

        viewPager.setOffscreenPageLimit(4);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position != 0 && dailyFragment != null) {
                    dailyFragment.clearDateFilter();
                }
            }
        });

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        List<String> tabTitles = Arrays.asList("Daily", "Calendar", "Graph", "Budget", "Trends");
        new TabLayoutMediator(tabLayout, viewPager, (tab, pos) -> tab.setText(tabTitles.get(pos))).attach();

        setupSearch();
        setupSettings();
        setupBudgetModeSelector();
        applyPositionSettings(prefs);

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            addTransactionLauncher.launch(new Intent(this, AddTransactionActivity.class));
            applyLaunchTransition();
        });

        // Multi-select bar buttons
        findViewById(R.id.btnMultiSelectDone).setOnClickListener(v -> {
            if (dailyFragment != null) {
                dailyFragment.commitMultiSelect();
            }
            hideMultiSelectBar();
        });
        findViewById(R.id.btnMultiSelectCancel).setOnClickListener(v -> {
            if (dailyFragment != null) {
                dailyFragment.exitMultiSelectMode();
            }
            hideMultiSelectBar();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DrawerLayout drawer = findViewById(R.id.drawerLayout);
                View searchContainer = findViewById(R.id.searchView);
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END);
                } else if (searchContainer.getVisibility() == View.VISIBLE) {
                    searchContainer.setVisibility(View.GONE);
                    findViewById(R.id.logo).setVisibility(View.VISIBLE);
                    ((SearchView) findViewById(R.id.searchInput)).setQuery("", false);
                    if (dailyFragment != null) dailyFragment.setSearch("");
                } else if (System.currentTimeMillis() - backPressedTime < 2000) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    backPressedTime = System.currentTimeMillis();
                    Toast.makeText(MainActivity.this, "Click again to close the app", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (isRecreatingForTheme) {
            isRecreatingForTheme = false;
            if (sDrawerOpen) {
                DrawerLayout drawer = findViewById(R.id.drawerLayout);
                if (drawer != null) {
                    drawer.openDrawer(GravityCompat.END, false);
                }
            }
            if (sCustomizeExpanded) {
                findViewById(R.id.contentCustomize).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowCustomize)).setText("▼");
            }
            if (sBackupExpanded) {
                findViewById(R.id.contentBackup).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowBackup)).setText("▼");
            }
            if (sBudgetExpanded) {
                findViewById(R.id.contentBudget).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowBudget)).setText("▼");
            }
        } else if (savedInstanceState != null) {
            boolean drawerOpen = savedInstanceState.getBoolean("drawer_open", false);
            if (drawerOpen) {
                DrawerLayout drawer = findViewById(R.id.drawerLayout);
                if (drawer != null) {
                    drawer.openDrawer(GravityCompat.END, false);
                }
            }
            if (savedInstanceState.getBoolean("customize_expanded", false)) {
                findViewById(R.id.contentCustomize).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowCustomize)).setText("▼");
            }
            if (savedInstanceState.getBoolean("backup_expanded", false)) {
                findViewById(R.id.contentBackup).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowBackup)).setText("▼");
            }
            if (savedInstanceState.getBoolean("budget_expanded", false)) {
                findViewById(R.id.contentBudget).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.arrowBudget)).setText("▼");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        if (drawer != null) {
            outState.putBoolean("drawer_open", drawer.isDrawerOpen(GravityCompat.END));
        }
        View contentCustomize = findViewById(R.id.contentCustomize);
        if (contentCustomize != null) {
            outState.putBoolean("customize_expanded", contentCustomize.getVisibility() == View.VISIBLE);
        }
        View contentBackup = findViewById(R.id.contentBackup);
        if (contentBackup != null) {
            outState.putBoolean("backup_expanded", contentBackup.getVisibility() == View.VISIBLE);
        }
        View contentBudget = findViewById(R.id.contentBudget);
        if (contentBudget != null) {
            outState.putBoolean("budget_expanded", contentBudget.getVisibility() == View.VISIBLE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("multi_select_mode", false)) {
            long folderId = intent.getLongExtra("target_folder_id", -1L);
            if (folderId >= 0) {
                pendingMultiSelectFolderId = folderId;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingMultiSelectFolderId >= 0) {
            final long folderId = pendingMultiSelectFolderId;
            pendingMultiSelectFolderId = -1L;
            viewPager.setCurrentItem(0, false);
            viewPager.post(() -> {
                if (dailyFragment != null) dailyFragment.enterMultiSelectMode(folderId);
            });
        }
    }

    public void navigateToDailyForDate(int year, int month, int day) {
        if (dailyFragment != null) {
            dailyFragment.setDateFilter(year, month, day);
        }
        viewPager.setCurrentItem(0);
    }

    public void launchAddTransaction(Intent intent) {
        addTransactionLauncher.launch(intent);
        applyLaunchTransition();
    }

    public void showMultiSelectBar(long folderId) {
        View bar = findViewById(R.id.multiSelectBar);
        TextView info = findViewById(R.id.tvMultiSelectInfo);
        if (bar != null && info != null) {
            // Try to get folder name
            String name = "folder";
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            for (Folder f : db.getFolders()) {
                if (f.getId() == folderId) { name = f.getName(); break; }
            }
            info.setText("Select transactions to save to \"" + name + "\"");
            bar.setVisibility(View.VISIBLE);
        }
    }

    public void hideMultiSelectBar() {
        View bar = findViewById(R.id.multiSelectBar);
        if (bar != null) bar.setVisibility(View.GONE);
    }

    public void updateMultiSelectCount(int count) {
        TextView info = findViewById(R.id.tvMultiSelectInfo);
        if (info != null) {
            // Keep the first part of the message; append count
            String current = info.getText().toString();
            int parenIdx = current.indexOf('(');
            String base = parenIdx > 0 ? current.substring(0, parenIdx).trim() : current;
            info.setText(base + (count > 0 ? " (" + count + " selected)" : ""));
        }
    }

    public void updateSummary(List<Transaction> data) {
        lastSummaryData = data;
        Calendar cal = Calendar.getInstance();

        long start;
        long end;
        String limitKey;

        if ("weekly".equals(budgetMode)) {
            Calendar s = (Calendar) cal.clone();
            s.set(Calendar.DAY_OF_WEEK, s.getFirstDayOfWeek());
            s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
            Calendar e = (Calendar) s.clone();
            e.add(Calendar.DAY_OF_WEEK, 7);
            start = s.getTimeInMillis();
            end = e.getTimeInMillis();
            limitKey = "weekly_limit";
        } else if ("custom".equals(budgetMode)) {
            android.content.SharedPreferences settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE);
            Calendar e = (Calendar) cal.clone();
            e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59); e.set(Calendar.MILLISECOND, 999);
            Calendar s = (Calendar) cal.clone();
            s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
            s.add(Calendar.YEAR, -settingsPrefs.getInt("budget_custom_years", 0));
            s.add(Calendar.MONTH, -settingsPrefs.getInt("budget_custom_months", 0));
            s.add(Calendar.WEEK_OF_YEAR, -settingsPrefs.getInt("budget_custom_weeks", 0));
            s.add(Calendar.DAY_OF_YEAR, -settingsPrefs.getInt("budget_custom_days", 0));
            start = s.getTimeInMillis();
            end = e.getTimeInMillis();
            limitKey = "custom_limit";
        } else { // monthly
            Calendar s = (Calendar) cal.clone();
            s.set(Calendar.DAY_OF_MONTH, 1);
            s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
            Calendar e = (Calendar) cal.clone();
            e.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59); e.set(Calendar.MILLISECOND, 999);
            start = s.getTimeInMillis();
            end = e.getTimeInMillis();
            limitKey = "monthly_limit";
        }

        double income = 0;
        double expense = 0;
        for (Transaction t : data) {
            if (t.getDate() >= start && t.getDate() <= end) {
                if ("income".equalsIgnoreCase(t.getType())) {
                    income += t.getAmount();
                } else if ("expense".equalsIgnoreCase(t.getType())) {
                    expense += t.getAmount();
                }
            }
        }

        ((TextView) findViewById(R.id.tvIncomeAmount)).setText(String.format(Locale.US, "₹%.2f", income));
        ((TextView) findViewById(R.id.tvExpenseAmount)).setText(String.format(Locale.US, "₹%.2f", expense));

        float limit = getSharedPreferences("budget", MODE_PRIVATE).getFloat(limitKey, 0f);
        TextView tvTotal = findViewById(R.id.tvTotalAmount);
        if (limit > 0f) {
            double remaining = limit - expense;
            tvTotal.setText(String.format(Locale.US, "₹%.2f", remaining));
            tvTotal.setTextColor(ContextCompat.getColor(this, remaining < 0 ? R.color.color_expense : R.color.color_income));
        } else {
            tvTotal.setText("No limit");
            tvTotal.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void setupBudgetModeSelector() {
        android.content.SharedPreferences settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE);
        String customName = settingsPrefs.getString("budget_custom_name", "");
        if (customName == null || customName.isEmpty()) {
            customName = "Custom";
        }
        final String finalCustomName = customName;
        TextView tvLabel = findViewById(R.id.tvBudgetLabel);

        tvLabel.setOnClickListener(anchor -> {
            PopupMenu popup = new PopupMenu(this, anchor);
            popup.getMenu().add(0, 0, 0, "Weekly");
            if (settingsPrefs.getBoolean("budget_monthly_enabled", true)) {
                popup.getMenu().add(0, 1, 1, "Monthly");
            }
            if (settingsPrefs.getBoolean("budget_custom_enabled", false)) {
                popup.getMenu().add(0, 2, 2, finalCustomName);
            }
            popup.setOnMenuItemClickListener(item -> {
                budgetMode = item.getItemId() == 0 ? "weekly" : (item.getItemId() == 2 ? "custom" : "monthly");
                tvLabel.setText(budgetMode.equals("weekly") ? "Weekly ▾" : (budgetMode.equals("custom") ? finalCustomName + " ▾" : "Monthly ▾"));
                updateSummary(lastSummaryData);
                return true;
            });
            popup.show();
        });

        // Set initial label
        tvLabel.setText(budgetMode.equals("weekly") ? "Weekly ▾" : (budgetMode.equals("custom") ? finalCustomName + " ▾" : "Monthly ▾"));
    }

    private void setupSearch() {
        View searchContainer = findViewById(R.id.searchView);
        SearchView searchInput = findViewById(R.id.searchInput);
        Spinner spinner = findViewById(R.id.spinnerSearchBy);
        ImageView ivSearch = findViewById(R.id.ivSearch);

        List<String> fields = Arrays.asList("All", "Name", "Category", "Amount", "Date");
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fields));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (dailyFragment != null) {
                    dailyFragment.setSearchField(fields.get(position).toLowerCase(Locale.US));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ivSearch.setOnClickListener(v -> {
            if (searchContainer.getVisibility() == View.GONE) {
                searchContainer.setVisibility(View.VISIBLE);
                findViewById(R.id.logo).setVisibility(View.GONE);
                searchInput.setIconified(false);
                searchInput.requestFocus();
            } else {
                searchContainer.setVisibility(View.GONE);
                findViewById(R.id.logo).setVisibility(View.VISIBLE);
                searchInput.setQuery("", false);
                spinner.setSelection(0);
                if (dailyFragment != null) {
                    dailyFragment.setSearch("");
                    dailyFragment.setSearchField("all");
                }
            }
        });

        searchInput.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if (dailyFragment != null) {
                    dailyFragment.setSearch(query != null ? query : "");
                }
                return true;
            }
        });

        searchInput.setOnCloseListener(() -> {
            searchContainer.setVisibility(View.GONE);
            findViewById(R.id.logo).setVisibility(View.VISIBLE);
            searchInput.setQuery("", false);
            spinner.setSelection(0);
            if (dailyFragment != null) {
                dailyFragment.setSearch("");
                dailyFragment.setSearchField("all");
            }
            return false;
        });
    }

    private void setupSettings() {
        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        View sidebar = drawer.getChildAt(1);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int collapsedWidth = (int) (280 * getResources().getDisplayMetrics().density);

        findViewById(R.id.ivSettings).setOnClickListener(v -> drawer.openDrawer(GravityCompat.END));

        final boolean[] isExpanded = {false};
        findViewById(R.id.tvSettingsHeader).setOnClickListener(new View.OnClickListener() {
            private long lastClick = 0L;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClick < 300) {
                    int toWidth = isExpanded[0] ? collapsedWidth : screenWidth;
                    ValueAnimator animator = ValueAnimator.ofInt(sidebar.getLayoutParams().width, toWidth);
                    animator.setDuration(250);
                    animator.addUpdateListener(animation -> {
                        sidebar.getLayoutParams().width = (int) animation.getAnimatedValue();
                        sidebar.requestLayout();
                    });
                    animator.start();
                    isExpanded[0] = !isExpanded[0];
                }
                lastClick = now;
            }
        });

        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (isExpanded[0]) {
                    sidebar.getLayoutParams().width = collapsedWidth;
                    sidebar.requestLayout();
                    isExpanded[0] = false;
                }
            }
        });

        findViewById(R.id.headerSavedLogs).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.END);
            startActivity(new Intent(this, SavedLogsActivity.class));
        });

        findViewById(R.id.headerCustomize).setOnClickListener(v -> 
            toggleSection(findViewById(R.id.contentCustomize), findViewById(R.id.arrowCustomize))
        );

        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        SwitchCompat switchDarkMode = findViewById(R.id.switchDarkMode);
        switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));

        List<String> transitionStyles = Arrays.asList("Slide", "Fade", "Zoom", "Flip");
        Spinner spinnerStyle = findViewById(R.id.spinnerTransitionStyle);
        spinnerStyle.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, transitionStyles));
        spinnerStyle.setSelection(prefs.getInt("transition_style", 0));

        View rowSlideDir = findViewById(R.id.rowSlideDirection);
        Spinner spinnerSlideDir = findViewById(R.id.spinnerSlideDirection);
        spinnerSlideDir.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("Left to right", "Right to left", "Bottom to top", "Top to bottom")));
        spinnerSlideDir.setSelection(prefs.getInt("slide_direction", 0));
        rowSlideDir.setVisibility(prefs.getInt("transition_style", 0) == 0 ? View.VISIBLE : View.GONE);

        List<String> fabPositions = Arrays.asList("Right side", "Left side");
        Spinner spinnerFab = findViewById(R.id.spinnerFabPosition);
        spinnerFab.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fabPositions));
        spinnerFab.setSelection(prefs.getInt("fab_position", 0));

        EditText etFabMarginSide = findViewById(R.id.etFabMarginSide);
        EditText etFabMarginBottom = findViewById(R.id.etFabMarginBottom);
        etFabMarginSide.setText(String.valueOf(prefs.getInt("fab_margin_side", 30)));
        etFabMarginBottom.setText(String.valueOf(prefs.getInt("fab_margin_bottom", 60)));

        List<String> tabPositions = Arrays.asList("Below search bar", "Above search bar");
        Spinner spinnerTab = findViewById(R.id.spinnerTabPosition);
        spinnerTab.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tabPositions));
        spinnerTab.setSelection(prefs.getInt("tab_position", 0));

        SwitchCompat switchSummaryBar = findViewById(R.id.switchSummaryBar);
        SwitchCompat switchTabBar = findViewById(R.id.switchTabBar);
        switchSummaryBar.setChecked(prefs.getBoolean("show_summary_bar", true));
        switchTabBar.setChecked(prefs.getBoolean("show_tab_bar", true));

        findViewById(R.id.btnResetCustomize).setOnClickListener(v -> {
            spinnerStyle.setSelection(0);
            spinnerSlideDir.setSelection(0);
            spinnerFab.setSelection(0);
            etFabMarginSide.setText("30");
            etFabMarginBottom.setText("60");
            spinnerTab.setSelection(0);
            switchSummaryBar.setChecked(true);
            switchTabBar.setChecked(true);
            switchDarkMode.setChecked(false);
        });

        findViewById(R.id.headerBackup).setOnClickListener(v -> 
            toggleSection(findViewById(R.id.contentBackup), findViewById(R.id.arrowBackup))
        );

        findViewById(R.id.optionExport).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.END);
            exportLauncher.launch("backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json");
        });

        findViewById(R.id.optionImport).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.END);
            importLauncher.launch(new String[]{"application/json", "*/*"});
        });

        findViewById(R.id.headerBudget).setOnClickListener(v -> 
            toggleSection(findViewById(R.id.contentBudget), findViewById(R.id.arrowBudget))
        );

        SwitchCompat switchMonthly = findViewById(R.id.switchBudgetMonthly);
        SwitchCompat switchCustom = findViewById(R.id.switchBudgetCustom);
        LinearLayout layoutCustomPeriod = findViewById(R.id.layoutCustomPeriod);
        TextView arrowBudgetCustom = findViewById(R.id.arrowBudgetCustom);

        switchMonthly.setChecked(prefs.getBoolean("budget_monthly_enabled", true));
        switchCustom.setChecked(prefs.getBoolean("budget_custom_enabled", false));
        layoutCustomPeriod.setVisibility(switchCustom.isChecked() ? View.VISIBLE : View.GONE);
        arrowBudgetCustom.setText(switchCustom.isChecked() ? "▼" : "▶");

        EditText etYears = findViewById(R.id.etCustomYears);
        EditText etMonths = findViewById(R.id.etCustomMonths);
        EditText etWeeks = findViewById(R.id.etCustomWeeks);
        EditText etDays = findViewById(R.id.etCustomDays);
        EditText etCustomName = findViewById(R.id.etCustomName);
        EditText etCustomBudgetAmount = findViewById(R.id.etCustomBudgetAmount);

        int yrs = prefs.getInt("budget_custom_years", 0);
        if (yrs > 0) etYears.setText(String.valueOf(yrs));
        int mths = prefs.getInt("budget_custom_months", 0);
        if (mths > 0) etMonths.setText(String.valueOf(mths));
        int wks = prefs.getInt("budget_custom_weeks", 0);
        if (wks > 0) etWeeks.setText(String.valueOf(wks));
        int dys = prefs.getInt("budget_custom_days", 0);
        if (dys > 0) etDays.setText(String.valueOf(dys));

        String customNameSaved = prefs.getString("budget_custom_name", "");
        if (customNameSaved != null && !customNameSaved.isEmpty()) etCustomName.setText(customNameSaved);

        float limitSaved = getSharedPreferences("budget", MODE_PRIVATE).getFloat("custom_limit", 0f);
        if (limitSaved > 0) etCustomBudgetAmount.setText(String.valueOf((int) limitSaved));

        findViewById(R.id.rowBudgetCustom).setOnClickListener(v -> {
            boolean open = layoutCustomPeriod.getVisibility() == View.VISIBLE;
            layoutCustomPeriod.setVisibility(open ? View.GONE : View.VISIBLE);
            arrowBudgetCustom.setText(open ? "▶" : "▼");
        });

        switchCustom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutCustomPeriod.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            arrowBudgetCustom.setText(isChecked ? "▼" : "▶");
        });

        Runnable saveSettingsRunnable = () -> {
            String yrsText = etYears.getText().toString();
            String mthsText = etMonths.getText().toString();
            String wksText = etWeeks.getText().toString();
            String dysText = etDays.getText().toString();
            String sideText = etFabMarginSide.getText().toString();
            String bottomText = etFabMarginBottom.getText().toString();

            prefs.edit()
                .putBoolean("dark_mode", switchDarkMode.isChecked())
                .putBoolean("budget_monthly_enabled", switchMonthly.isChecked())
                .putBoolean("budget_custom_enabled", switchCustom.isChecked())
                .putString("budget_custom_name", etCustomName.getText().toString().trim())
                .putInt("budget_custom_years", parseOrDefault(yrsText, 0))
                .putInt("budget_custom_months", parseOrDefault(mthsText, 0))
                .putInt("budget_custom_weeks", parseOrDefault(wksText, 0))
                .putInt("budget_custom_days", parseOrDefault(dysText, 0))
                .putInt("transition_style", spinnerStyle.getSelectedItemPosition())
                .putInt("slide_direction", spinnerSlideDir.getSelectedItemPosition())
                .putInt("fab_position", spinnerFab.getSelectedItemPosition())
                .putInt("fab_margin_side", parseOrDefault(sideText, 30))
                .putInt("fab_margin_bottom", parseOrDefault(bottomText, 60))
                .putInt("tab_position", spinnerTab.getSelectedItemPosition())
                .putBoolean("show_summary_bar", switchSummaryBar.isChecked())
                .putBoolean("show_tab_bar", switchTabBar.isChecked())
                .apply();

            String amtText = etCustomBudgetAmount.getText().toString();
            try {
                float amt = Float.parseFloat(amtText);
                getSharedPreferences("budget", MODE_PRIVATE).edit().putFloat("custom_limit", amt).apply();
            } catch (Exception ignored) {}

            int targetMode = switchDarkMode.isChecked() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                isRecreatingForTheme = true;
                sDrawerOpen = drawer.isDrawerOpen(GravityCompat.END);
                sCustomizeExpanded = findViewById(R.id.contentCustomize).getVisibility() == View.VISIBLE;
                sBackupExpanded = findViewById(R.id.contentBackup).getVisibility() == View.VISIBLE;
                sBudgetExpanded = findViewById(R.id.contentBudget).getVisibility() == View.VISIBLE;
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }

            applyPositionSettings(prefs);

            BudgetFragment budgetFrag = (BudgetFragment) getSupportFragmentManager().findFragmentByTag("f3");
            if (budgetFrag != null && budgetFrag.getView() != null) {
                budgetFrag.applySettingsVisibility(budgetFrag.getView(), prefs);
                budgetFrag.refreshCustomFromSettings(budgetFrag.getView());
            }
        };

        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSettingsRunnable.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rowSlideDir.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                saveSettingsRunnable.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSlideDir.setOnItemSelectedListener(spinnerListener);
        spinnerFab.setOnItemSelectedListener(spinnerListener);
        spinnerTab.setOnItemSelectedListener(spinnerListener);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettingsRunnable.run());
        switchSummaryBar.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettingsRunnable.run());
        switchTabBar.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettingsRunnable.run());
        switchMonthly.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettingsRunnable.run());
        switchCustom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutCustomPeriod.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            arrowBudgetCustom.setText(isChecked ? "▼" : "▶");
            saveSettingsRunnable.run();
        });

        android.text.TextWatcher textWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                saveSettingsRunnable.run();
            }
        };

        etFabMarginSide.addTextChangedListener(textWatcher);
        etFabMarginBottom.addTextChangedListener(textWatcher);
        etYears.addTextChangedListener(textWatcher);
        etMonths.addTextChangedListener(textWatcher);
        etWeeks.addTextChangedListener(textWatcher);
        etDays.addTextChangedListener(textWatcher);
        etCustomName.addTextChangedListener(textWatcher);
        etCustomBudgetAmount.addTextChangedListener(textWatcher);
    }

    private int parseOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private void toggleSection(LinearLayout content, TextView arrow) {
        if (content.getVisibility() == View.GONE) {
            content.setVisibility(View.VISIBLE);
            arrow.setText("▼");
        } else {
            content.setVisibility(View.GONE);
            arrow.setText("▶");
        }
    }

    public void applyPositionSettings(android.content.SharedPreferences prefs) {
        float density = getResources().getDisplayMetrics().density;

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) fab.getLayoutParams();
        int marginSidePx = (int) (prefs.getInt("fab_margin_side", 30) * density);
        int marginBottomPx = (int) (prefs.getInt("fab_margin_bottom", 60) * density);

        if (prefs.getInt("fab_position", 0) == 1) {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            lp.setMargins(marginSidePx, 0, 0, marginBottomPx);
        } else {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.setMargins(0, 0, marginSidePx, marginBottomPx);
        }
        fab.setLayoutParams(lp);

        View summaryBar = findViewById(R.id.summary);
        boolean showSummary = prefs.getBoolean("show_summary_bar", true);
        summaryBar.setVisibility(showSummary ? View.VISIBLE : View.GONE);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        View searchView = findViewById(R.id.searchView);
        boolean showTab = prefs.getBoolean("show_tab_bar", true);
        tabLayout.setVisibility(showTab ? View.VISIBLE : View.GONE);

        RelativeLayout.LayoutParams tabLp = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        tabLp.removeRule(RelativeLayout.BELOW);
        tabLp.addRule(RelativeLayout.BELOW, R.id.topBar);

        RelativeLayout.LayoutParams sumLp = (RelativeLayout.LayoutParams) summaryBar.getLayoutParams();
        sumLp.removeRule(RelativeLayout.BELOW);
        if (showTab) {
            sumLp.addRule(RelativeLayout.BELOW, R.id.tabLayout);
        } else {
            sumLp.addRule(RelativeLayout.BELOW, R.id.topBar);
        }

        int lastBarId = R.id.topBar;
        if (showSummary) {
            lastBarId = R.id.summary;
        } else if (showTab) {
            lastBarId = R.id.tabLayout;
        }

        RelativeLayout.LayoutParams vpLp = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();
        vpLp.removeRule(RelativeLayout.BELOW);
        vpLp.addRule(RelativeLayout.BELOW, lastBarId);

        tabLayout.requestLayout();
        searchView.requestLayout();
        summaryBar.requestLayout();
        viewPager.requestLayout();

        if (viewPager.getChildAt(0) instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                if (child != null) {
                    child.setAlpha(1f);
                    child.setScaleX(1f);
                    child.setScaleY(1f);
                    child.setRotationY(0f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        child.setTranslationZ(0f);
                    }
                }
            }
        }

        switch (prefs.getInt("transition_style", 0)) {
            case 0:
                viewPager.setPageTransformer(null);
                break;
            case 1: // Fade
                viewPager.setPageTransformer((page, position) -> {
                    page.setAlpha(Math.max(0f, Math.min(1f, 1f - Math.abs(position))));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        page.setTranslationZ(position == 0f ? 1f : 0f);
                    }
                });
                break;
            case 2: // Zoom
                viewPager.setPageTransformer((page, position) -> {
                    float absPos = Math.max(0f, Math.min(1f, Math.abs(position)));
                    float scale = 0.75f + (1f - absPos) * 0.25f;
                    page.setScaleX(scale);
                    page.setScaleY(scale);
                    page.setAlpha(0.4f + (1f - absPos) * 0.6f);
                });
                break;
            case 3: // Flip
                viewPager.setPageTransformer((page, position) -> {
                    page.setRotationY(position * -30f);
                    page.setAlpha(Math.max(0.3f, Math.min(1f, 1f - Math.abs(position))));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        page.setTranslationZ(position == 0f ? 1f : 0f);
                    }
                });
                break;
        }
    }

    public int transitionDuration() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        switch (prefs.getInt("transition_speed", 1)) {
            case 0: return 150;
            case 2: return 500;
            case 3: return prefs.getInt("transition_custom_ms", 300);
            default: return 300;
        }
    }

    public void applyLaunchTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            overridePendingTransition(0, 0);
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int style = prefs.getInt("transition_style", 0);
        int dir = prefs.getInt("slide_direction", 0);
        int enterRes = android.R.anim.fade_in;
        int exitRes = android.R.anim.fade_out;

        if (style == 0) {
            if (dir == 1) {
                enterRes = R.anim.slide_in_left;
                exitRes = R.anim.slide_out_right;
            } else if (dir == 2) {
                enterRes = R.anim.slide_in_bottom;
                exitRes = R.anim.slide_out_top;
            } else if (dir == 3) {
                enterRes = R.anim.slide_in_top;
                exitRes = R.anim.slide_out_bottom;
            } else {
                enterRes = R.anim.slide_in_right;
                exitRes = R.anim.slide_out_left;
            }
            overridePendingTransition(enterRes, exitRes);
        } else if (style == 1 || style == 2) {
            overridePendingTransition(enterRes, exitRes);
        } else {
            overridePendingTransition(0, 0);
        }
    }
}
