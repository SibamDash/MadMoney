package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.graphics.Insets;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AddTransactionActivity extends AppCompatActivity {

    private String selectedType = "expense";
    private String debtSubType = "togive";
    private long selectedDateMillis = System.currentTimeMillis();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault());
    private OnDebtTypeSelect debtTypeSelect; // field so tabSelect can call it

    private int getWhiteColor() { return ContextCompat.getColor(this, android.R.color.white); }
    private int getGreyColor() { return ContextCompat.getColor(this, R.color.toggle_text_unselected); }
    private android.graphics.drawable.Drawable getSelBg() { return ContextCompat.getDrawable(this, R.drawable.bg_toggle_selected); }
    private android.graphics.drawable.Drawable getTransBg() { return ContextCompat.getDrawable(this, android.R.color.transparent); }

    private final List<String> defaultCategories = Arrays.asList("Food", "Social Life", "Pets", "Transport", "Health", "Education", "Gift", "Apparel");
    private final Map<String, String> defaultEmojis = new HashMap<>();
    
    private final List<String> defaultIncomeCategories = Arrays.asList("Allowance", "Salary", "Cash", "Bonus");
    private final Map<String, String> defaultIncomeEmojis = new HashMap<>();

    // For crop
    private Uri cropSourceUri = null;
    private int pendingCropIndex = -1;
    private String pendingCropPrefsKey = "cat_icons";
    private BaseAdapter currentSheetAdapter = null;
    private List<String> currentCategories = null;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri == null) return;
            cropSourceUri = uri;
            launchCrop(uri);
        }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                Uri croppedUri = (data != null) ? data.getData() : null;
                if (croppedUri == null) return;
                saveCroppedImageKeyed(croppedUri, pendingCropIndex, pendingCropPrefsKey);
                if (currentSheetAdapter != null) {
                    currentSheetAdapter.notifyDataSetChanged();
                }
            }
        }
    );

    private void launchCrop(Uri sourceUri) {
        File destFile = new File(getCacheDir(), "cropped/cat_" + pendingCropPrefsKey + "_" + pendingCropIndex + ".jpg");
        if (destFile.getParentFile() != null) {
            destFile.getParentFile().mkdirs();
        }
        Uri destUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", destFile);
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(sourceUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 200);
        intent.putExtra("outputY", 200);
        intent.putExtra("output", destUri);
        intent.putExtra("return-data", false);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            cropLauncher.launch(intent);
        } else {
            saveCroppedImageKeyed(sourceUri, pendingCropIndex, pendingCropPrefsKey);
            if (currentSheetAdapter != null) {
                currentSheetAdapter.notifyDataSetChanged();
            }
        }
    }

    private String getCategoryIconPath(int index, String prefsKey) {
        return getSharedPreferences(prefsKey, Context.MODE_PRIVATE).getString("icon_" + index, null);
    }

    private String getCategoryEmoji(int index, String name, String prefsKey, Map<String, String> defaults) {
        return getSharedPreferences(prefsKey, Context.MODE_PRIVATE).getString("emoji_" + index, defaults.get(name));
    }

    private void saveCategoryEmoji(int index, String emoji, String prefsKey) {
        getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit()
            .putString("emoji_" + index, emoji)
            .remove("icon_" + index)
            .apply();
        new File(getFilesDir(), "cat_icon_" + prefsKey + "_" + index + ".jpg").delete();
    }

    private void saveCroppedImageKeyed(Uri uri, int index, String prefsKey) {
        try {
            Bitmap bmp;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(is);
            }
            if (bmp == null) return;
            File dest = new File(getFilesDir(), "cat_icon_" + prefsKey + "_" + index + ".jpg");
            if (dest.getParentFile() != null) {
                dest.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit()
                .putString("icon_" + index, dest.getAbsolutePath()).apply();
        } catch (Exception ignored) {}
    }

    private List<String> getCategories(String prefsKey, List<String> defaults) {
        android.content.SharedPreferences prefs = getSharedPreferences(prefsKey, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet("list", null);
        if (saved != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < saved.size(); i++) {
                String defaultVal = i < defaults.size() ? defaults.get(i) : "";
                String val = prefs.getString("cat_" + i, defaultVal);
                list.add(val != null ? val : "");
            }
            return list;
        } else {
            saveCategories(defaults, prefsKey);
            return new ArrayList<>(defaults);
        }
    }

    private void saveCategories(List<String> list, String prefsKey) {
        android.content.SharedPreferences.Editor prefs = getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit();
        Set<String> set = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            set.add(String.valueOf(i));
            prefs.putString("cat_" + i, list.get(i));
        }
        prefs.putStringSet("list", set);
        prefs.apply();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        applyEnterTransition();
        setContentView(R.layout.activity_add_transaction);

        // Populate default maps
        defaultEmojis.put("Food", "🍔");
        defaultEmojis.put("Social Life", "🎉");
        defaultEmojis.put("Pets", "🐾");
        defaultEmojis.put("Transport", "🚗");
        defaultEmojis.put("Health", "💊");
        defaultEmojis.put("Education", "📚");
        defaultEmojis.put("Gift", "🎁");
        defaultEmojis.put("Apparel", "👗");

        defaultIncomeEmojis.put("Allowance", "💰");
        defaultIncomeEmojis.put("Salary", "💼");
        defaultIncomeEmojis.put("Cash", "💵");
        defaultIncomeEmojis.put("Bonus", "🎯");

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ViewFlipper flipper = findViewById(R.id.viewFlipper);
        AppCompatButton btnExpense = findViewById(R.id.btnExpense);
        AppCompatButton btnIncome = findViewById(R.id.btnIncome);
        AppCompatButton btnDebt = findViewById(R.id.btnDebt);

        List<String> tabs = Arrays.asList("expense", "income", "debt");

        OnTabSelect tabSelect = new OnTabSelect() {
            @Override
            public void selectTab(String type) {
                int idx = type.equals("expense") ? 0 : (type.equals("income") ? 1 : 2);
                boolean goingRight = idx > flipper.getDisplayedChild();
                android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
                int style = prefs.getInt("transition_style", 0);
                long dur = 300L;

                flipper.setInAnimation(null);
                flipper.setOutAnimation(null);

                if (style == 1) { // Fade
                    Animation inAnim = AnimationUtils.loadAnimation(AddTransactionActivity.this, android.R.anim.fade_in);
                    inAnim.setDuration(dur);
                    Animation outAnim = AnimationUtils.loadAnimation(AddTransactionActivity.this, android.R.anim.fade_out);
                    outAnim.setDuration(dur);
                    flipper.setInAnimation(inAnim);
                    flipper.setOutAnimation(outAnim);
                } else if (style == 2) { // Zoom
                    ScaleAnimation inAnim = new ScaleAnimation(0.85f, 1f, 0.85f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    inAnim.setDuration(dur);
                    ScaleAnimation outAnim = new ScaleAnimation(1f, 0.85f, 1f, 0.85f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    outAnim.setDuration(dur);
                    flipper.setInAnimation(inAnim);
                    flipper.setOutAnimation(outAnim);
                } else if (style == 3) { // Flip-ish (Fade + scale)
                    AnimationSet inSet = new AnimationSet(true);
                    Animation fIn = AnimationUtils.loadAnimation(AddTransactionActivity.this, android.R.anim.fade_in);
                    fIn.setDuration(dur);
                    inSet.addAnimation(fIn);
                    ScaleAnimation sIn = new ScaleAnimation(0f, 1f, 1f, 1f, Animation.RELATIVE_TO_SELF, goingRight ? 0f : 1f, Animation.RELATIVE_TO_SELF, 0.5f);
                    sIn.setDuration(dur);
                    inSet.addAnimation(sIn);
                    flipper.setInAnimation(inSet);

                    AnimationSet outSet = new AnimationSet(true);
                    Animation fOut = AnimationUtils.loadAnimation(AddTransactionActivity.this, android.R.anim.fade_out);
                    fOut.setDuration(dur);
                    outSet.addAnimation(fOut);
                    ScaleAnimation sOut = new ScaleAnimation(1f, 0f, 1f, 1f, Animation.RELATIVE_TO_SELF, goingRight ? 1f : 0f, Animation.RELATIVE_TO_SELF, 0.5f);
                    sOut.setDuration(dur);
                    outSet.addAnimation(sOut);
                    flipper.setOutAnimation(outSet);
                } else { // Slide
                    Animation inAnim = AnimationUtils.loadAnimation(AddTransactionActivity.this, goingRight ? R.anim.slide_in_right : R.anim.slide_in_left);
                    inAnim.setDuration(dur);
                    Animation outAnim = AnimationUtils.loadAnimation(AddTransactionActivity.this, goingRight ? R.anim.slide_out_left : R.anim.slide_out_right);
                    outAnim.setDuration(dur);
                    flipper.setInAnimation(inAnim);
                    flipper.setOutAnimation(outAnim);
                }

                flipper.setDisplayedChild(idx);
                int themeColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.orange_primary);
                if (type.equals("income")) {
                    themeColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.color_income);
                } else if (type.equals("debt") || type.equals("togive") || type.equals("toget")) {
                    themeColor = ContextCompat.getColor(AddTransactionActivity.this,
                        debtSubType.equals("togive") ? R.color.color_to_give : R.color.color_to_get);
                }

                btnExpense.setBackground(type.equals("expense") ? getSelBg() : getTransBg());
                btnExpense.setBackgroundTintList(type.equals("expense") ? ColorStateList.valueOf(themeColor) : null);

                btnIncome.setBackground(type.equals("income") ? getSelBg() : getTransBg());
                btnIncome.setBackgroundTintList(type.equals("income") ? ColorStateList.valueOf(themeColor) : null);

                boolean isDebtTab = type.equals("debt") || type.equals("togive") || type.equals("toget");
                btnDebt.setBackground(isDebtTab ? getSelBg() : getTransBg());
                btnDebt.setBackgroundTintList(isDebtTab ? ColorStateList.valueOf(themeColor) : null);

                btnExpense.setTextColor(type.equals("expense") ? getWhiteColor() : getGreyColor());
                btnIncome.setTextColor(type.equals("income") ? getWhiteColor() : getGreyColor());
                btnDebt.setTextColor(isDebtTab ? getWhiteColor() : getGreyColor());

                for (int i = 0; i < flipper.getChildCount(); i++) {
                    View child = flipper.getChildAt(i);
                    AppCompatButton btnSave = child.findViewById(R.id.btnSave);
                    if (btnSave != null) {
                        int saveBtnColor = themeColor;
                        if (i == 2) {
                            saveBtnColor = ContextCompat.getColor(AddTransactionActivity.this,
                                debtSubType.equals("togive") ? R.color.color_to_give : R.color.color_to_get);
                        } else if (i == 1) {
                            saveBtnColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.color_income);
                        } else if (i == 0) {
                            saveBtnColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.orange_primary);
                        }
                        btnSave.setBackgroundTintList(ColorStateList.valueOf(saveBtnColor));
                    }
                }

                selectedType = type.equals("income") ? "income" : (type.equals("debt") ? "togive" : "expense");
                if (type.equals("debt")) {
                    selectedType = debtSubType;
                }
                updateAllDates();
                // When switching to debt tab, re-apply the debt sub-type button tint
                // so To Give is always blue (not the XML default orange)
                if (type.equals("debt") && debtTypeSelect != null) {
                    debtTypeSelect.selectDebtType(debtSubType);
                }
            }
        };

        btnExpense.setOnClickListener(v -> tabSelect.selectTab("expense"));
        btnIncome.setOnClickListener(v -> tabSelect.selectTab("income"));
        btnDebt.setOnClickListener(v -> tabSelect.selectTab("debt"));

        GestureDetector swipeGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (Math.abs(vX) < Math.abs(vY)) return false;
                int cur = flipper.getDisplayedChild();
                if (vX < -500f && cur < tabs.size() - 1) {
                    tabSelect.selectTab(tabs.get(cur + 1));
                    return true;
                }
                if (vX > 500f && cur > 0) {
                    tabSelect.selectTab(tabs.get(cur - 1));
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        flipper.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            swipeGestureDetector.onTouchEvent(event);
            return true;
        });

        for (int i = 0; i < flipper.getChildCount(); i++) {
            flipper.getChildAt(i).setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    flipper.getParent().requestDisallowInterceptTouchEvent(true);
                }
                swipeGestureDetector.onTouchEvent(event);
                return false;
            });
        }

        AppCompatButton btnToGive = findViewById(R.id.btnToGive);
        AppCompatButton btnToGet = findViewById(R.id.btnToGet);

        debtTypeSelect = new OnDebtTypeSelect() {
            @Override
            public void selectDebtType(String t) {
                debtSubType = t;
                selectedType = t;
                int toGiveColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.color_to_give);
                int toGetColor = ContextCompat.getColor(AddTransactionActivity.this, R.color.color_to_get);

                btnToGive.setBackground(t.equals("togive") ? getSelBg() : getTransBg());
                btnToGive.setBackgroundTintList(t.equals("togive") ? ColorStateList.valueOf(toGiveColor) : null);

                btnToGet.setBackground(t.equals("toget") ? getSelBg() : getTransBg());
                btnToGet.setBackgroundTintList(t.equals("toget") ? ColorStateList.valueOf(toGetColor) : null);

                btnToGive.setTextColor(t.equals("togive") ? getWhiteColor() : getGreyColor());
                btnToGet.setTextColor(t.equals("toget") ? getWhiteColor() : getGreyColor());

                // Also update the main Debt toggle button background tint if selected
                int activeColor = t.equals("togive") ? toGiveColor : toGetColor;
                AppCompatButton btnDebt = findViewById(R.id.btnDebt);
                if (btnDebt != null && btnDebt.getBackground() != null && btnDebt.getBackground() != getTransBg()) {
                    btnDebt.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                }

                // Also update the Debt form Save button background tint
                View child = flipper.getChildAt(2); // Debt form is index 2
                if (child != null) {
                    AppCompatButton btnSave = child.findViewById(R.id.btnSave);
                    if (btnSave != null) {
                        btnSave.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                    }
                }
            }
        };

        btnToGive.setOnClickListener(v -> debtTypeSelect.selectDebtType("togive"));
        btnToGet.setOnClickListener(v -> debtTypeSelect.selectDebtType("toget"));

        AutoCompleteTextView etDebtPerson = findViewById(R.id.etDebtPerson);
        List<String> persons = DatabaseHelper.getInstance(this).getDebtPersonsSorted();
        android.widget.ArrayAdapter<String> autoAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, persons);
        etDebtPerson.setAdapter(autoAdapter);
        etDebtPerson.setOnClickListener(v -> {
            if (etDebtPerson.getText().toString().isEmpty()) {
                etDebtPerson.showDropDown();
            }
        });

        for (int i = 0; i < flipper.getChildCount(); i++) {
            TextView tvDate = flipper.getChildAt(i).findViewById(R.id.tvDate);
            if (tvDate != null) {
                tvDate.setOnClickListener(v -> pickDateTime());
            }
        }

        for (int i = 0; i < flipper.getChildCount(); i++) {
            View child = flipper.getChildAt(i);
            AppCompatButton btnSave = child.findViewById(R.id.btnSave);
            if (btnSave != null) btnSave.setOnClickListener(v -> save(false));
            AppCompatButton btnContinue = child.findViewById(R.id.btnContinue);
            if (btnContinue != null) btnContinue.setOnClickListener(v -> save(true));
        }

        findViewById(R.id.etExpenseCategory).setOnClickListener(v -> showCategorySheet((TextView) v, "expense"));
        findViewById(R.id.etIncomeSource).setOnClickListener(v -> showCategorySheet((TextView) v, "income"));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
        selectedDateMillis = getIntent().getLongExtra("selected_date_millis", System.currentTimeMillis());
        tabSelect.selectTab("expense");

        // Pre-fill for editing
        long editId = getIntent().getLongExtra("edit_id", -1L);
        if (editId != -1L) {
            String type = getIntent().getStringExtra("edit_type");
            if (type == null) type = "expense";
            String category = getIntent().getStringExtra("edit_category");
            if (category == null) category = "";
            double amount = getIntent().getDoubleExtra("edit_amount", 0.0);
            String note = getIntent().getStringExtra("edit_note");
            if (note == null) note = "";
            selectedDateMillis = getIntent().getLongExtra("edit_date", System.currentTimeMillis());
            tabSelect.selectTab(type.equals("togive") || type.equals("toget") ? "debt" : type);
            if (type.equals("togive") || type.equals("toget")) {
                debtTypeSelect.selectDebtType(type);
            }
            View child = flipper.getCurrentView();
            EditText etAmt = child.findViewById(R.id.etAmount);
            if (etAmt != null) {
                etAmt.setText(String.valueOf((int) amount));
            }
            if (type.equals("income")) {
                TextView tvSrc = child.findViewById(R.id.etIncomeSource);
                if (tvSrc != null) tvSrc.setText(category);
                EditText etNote = child.findViewById(R.id.etIncomeNote);
                if (etNote != null) etNote.setText(note);
            } else if (type.equals("togive") || type.equals("toget")) {
                EditText etPerson = child.findViewById(R.id.etDebtPerson);
                if (etPerson != null) etPerson.setText(getIntent().getStringExtra("edit_title"));
                EditText etNote = child.findViewById(R.id.etDebtNote);
                if (etNote != null) etNote.setText(note);
            } else {
                TextView tvCat = child.findViewById(R.id.etExpenseCategory);
                if (tvCat != null) tvCat.setText(category);
                EditText etNote = child.findViewById(R.id.etExpenseNote);
                if (etNote != null) etNote.setText(note);
            }

            for (int i = 0; i < flipper.getChildCount(); i++) {
                AppCompatButton btnCont = flipper.getChildAt(i).findViewById(R.id.btnContinue);
                if (btnCont != null) btnCont.setVisibility(View.GONE);
            }
            updateAllDates();
        }
    }

    private void showCategorySheet(TextView tvCategory, String type) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        GridView grid = (GridView) LayoutInflater.from(this).inflate(R.layout.bottom_sheet_categories, null);
        sheet.setContentView(grid);

        boolean isIncome = type.equals("income");
        String prefsKey = isIncome ? "income_cat_icons" : "cat_icons";
        String catPrefsKey = isIncome ? "income_categories" : "categories";
        List<String> defaults = isIncome ? defaultIncomeCategories : defaultCategories;
        Map<String, String> defaultEmojiMap = isIncome ? defaultIncomeEmojis : defaultEmojis;
        int circleBg = isIncome ? R.drawable.bg_circle_icon_green : R.drawable.bg_circle_icon;

        List<String> categories = getCategories(catPrefsKey, defaults);
        currentCategories = categories;

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() { return categories.size(); }
            @Override
            public Object getItem(int pos) { return categories.get(pos); }
            @Override
            public long getItemId(int pos) { return pos; }
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
                }
                String name = categories.get(pos);
                ((TextView) v.findViewById(R.id.tvCategoryName)).setText(name);
                TextView tvEmoji = v.findViewById(R.id.tvCategoryEmoji);
                ImageView ivImage = v.findViewById(R.id.ivCategoryImage);
                tvEmoji.setBackgroundResource(circleBg);

                String imagePath = getCategoryIconPath(pos, prefsKey);
                if (imagePath != null && new File(imagePath).exists()) {
                    tvEmoji.setVisibility(View.GONE);
                    ivImage.setVisibility(View.VISIBLE);
                    ivImage.setImageBitmap(BitmapFactory.decodeFile(imagePath));
                } else {
                    tvEmoji.setVisibility(View.VISIBLE);
                    ivImage.setVisibility(View.GONE);
                    String emoji = getCategoryEmoji(pos, name, prefsKey, defaultEmojiMap);
                    tvEmoji.setText(emoji != null ? emoji : "•");
                }
                return v;
            }
        };
        currentSheetAdapter = adapter;
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, pos, id) -> {
            tvCategory.setText(categories.get(pos));
            sheet.dismiss();
        });

        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            showEditCategoryDialog(pos, categories, adapter, prefsKey, catPrefsKey);
            return true;
        });

        sheet.show();
    }

    private void showEditCategoryDialog(int pos, List<String> categories, BaseAdapter adapter, String prefsKey, String catPrefsKey) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null);
        EditText etName = view.findViewById(R.id.etEditName);
        etName.setText(categories.get(pos));
        EditText etEmoji = view.findViewById(R.id.etEditEmoji);
        etEmoji.setText(getCategoryEmoji(pos, categories.get(pos), prefsKey, "income_cat_icons".equals(prefsKey) ? defaultIncomeEmojis : defaultEmojis));

        view.findViewById(R.id.btnPickGallery).setOnClickListener(v -> {
            pendingCropIndex = pos;
            pendingCropPrefsKey = prefsKey;
            galleryLauncher.launch("image/*");
        });

        new AlertDialog.Builder(this)
            .setTitle("Edit Category")
            .setView(view)
            .setPositiveButton("Save", (dialog, which) -> {
                String newName = etName.getText().toString().trim();
                String newEmoji = etEmoji.getText().toString().trim();
                if (!newName.isEmpty()) {
                    categories.set(pos, newName);
                    saveCategories(categories, catPrefsKey);
                }
                if (!newEmoji.isEmpty()) {
                    saveCategoryEmoji(pos, newEmoji, prefsKey);
                }
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            overridePendingTransition(0, 0);
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int style = prefs.getInt("transition_style", 0);
        int dir = prefs.getInt("slide_direction", 0);
        if (style == 0) {
            if (dir == 1) overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            else if (dir == 2) overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
            else if (dir == 3) overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
            else overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } else if (style == 1 || style == 2) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    private void applyEnterTransition() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int style = prefs.getInt("transition_style", 0);
        int dir = prefs.getInt("slide_direction", 0);
        long dur = 300L;
        Interpolator interp = new DecelerateInterpolator();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition enter;
            if (style == 1 || style == 3) {
                enter = new Fade();
            } else if (style == 2) {
                enter = new Explode();
            } else {
                int gravity = Gravity.END;
                if (dir == 1) gravity = Gravity.START;
                else if (dir == 2) gravity = Gravity.BOTTOM;
                else if (dir == 3) gravity = Gravity.TOP;
                enter = new Slide(gravity);
            }
            enter.setDuration(dur);
            enter.setInterpolator(interp);
            getWindow().setEnterTransition(enter);

            Transition exit;
            if (style == 1 || style == 3) {
                exit = new Fade();
            } else if (style == 2) {
                exit = new Explode();
            } else {
                int gravity = Gravity.START;
                if (dir == 1) gravity = Gravity.END;
                else if (dir == 2) gravity = Gravity.TOP;
                else if (dir == 3) gravity = Gravity.BOTTOM;
                exit = new Slide(gravity);
            }
            exit.setDuration(dur);
            exit.setInterpolator(interp);
            getWindow().setReturnTransition(exit);
        }
    }

    private void pickDateTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDateMillis);
        new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            new TimePickerDialog(this, (view2, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                selectedDateMillis = cal.getTimeInMillis();
                updateAllDates();
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateAllDates() {
        String text = dateFmt.format(new Date(selectedDateMillis));
        ViewFlipper flipper = findViewById(R.id.viewFlipper);
        for (int i = 0; i < flipper.getChildCount(); i++) {
            TextView tvDate = flipper.getChildAt(i).findViewById(R.id.tvDate);
            if (tvDate != null) {
                tvDate.setText(text);
            }
        }
    }

    private void save(boolean andContinue) {
        ViewFlipper flipper = findViewById(R.id.viewFlipper);
        View child = flipper.getCurrentView();
        EditText etAmt = child.findViewById(R.id.etAmount);
        String amountText = etAmt != null ? etAmt.getText().toString().trim() : "";

        if (amountText.isEmpty()) {
            if (etAmt != null) etAmt.setError("Required");
            return;
        }

        String title = "";
        String category = "";
        String note = "";

        if (selectedType.equals("income")) {
            TextView tvSrc = child.findViewById(R.id.etIncomeSource);
            String src = tvSrc != null ? tvSrc.getText().toString().trim() : "";
            if (src.isEmpty()) {
                Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            title = src;
            category = src;
            EditText etNote = child.findViewById(R.id.etIncomeNote);
            note = etNote != null ? etNote.getText().toString().trim() : "";
        } else if (selectedType.equals("togive") || selectedType.equals("toget")) {
            EditText etPerson = child.findViewById(R.id.etDebtPerson);
            String person = etPerson != null ? etPerson.getText().toString().trim() : "";
            if (person.isEmpty()) {
                if (etPerson != null) etPerson.setError("Required");
                return;
            }
            title = person;
            category = selectedType.equals("togive") ? "To Give" : "To Get";
            EditText etNote = child.findViewById(R.id.etDebtNote);
            note = etNote != null ? etNote.getText().toString().trim() : "";
        } else {
            TextView tvCat = child.findViewById(R.id.etExpenseCategory);
            String cat = tvCat != null ? tvCat.getText().toString().trim() : "";
            if (cat.isEmpty()) {
                Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            title = cat;
            category = cat;
            EditText etNote = child.findViewById(R.id.etExpenseNote);
            note = etNote != null ? etNote.getText().toString().trim() : "";
        }

        DatabaseHelper db = DatabaseHelper.getInstance(this);
        long editId = getIntent().getLongExtra("edit_id", -1L);
        double amountVal = Double.parseDouble(amountText);

        if (editId != -1L) {
            db.updateTransaction(new Transaction(
                editId, title, category, amountVal, selectedType,
                "", note, "", selectedDateMillis, false, 0L, false
            ));
        } else {
            long folderId = getIntent().getLongExtra("folder_id", 0L);
            db.addTransaction(new Transaction(
                0L, title, category, amountVal, selectedType,
                "", note, "", selectedDateMillis, false, 0L, folderId > 0, folderId
            ));
        }
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();

        if (andContinue) {
            EditText etAmount = child.findViewById(R.id.etAmount);
            if (etAmount != null) etAmount.getText().clear();
            EditText etExpNote = child.findViewById(R.id.etExpenseNote);
            if (etExpNote != null) etExpNote.getText().clear();
            EditText etIncNote = child.findViewById(R.id.etIncomeNote);
            if (etIncNote != null) etIncNote.getText().clear();
            EditText etDebtNote = child.findViewById(R.id.etDebtNote);
            if (etDebtNote != null) etDebtNote.getText().clear();

            TextView tvExpCat = child.findViewById(R.id.etExpenseCategory);
            if (tvExpCat != null) tvExpCat.setText("");
            TextView tvIncSrc = child.findViewById(R.id.etIncomeSource);
            if (tvIncSrc != null) tvIncSrc.setText("");
            EditText etDebtPerson = child.findViewById(R.id.etDebtPerson);
            if (etDebtPerson != null) etDebtPerson.getText().clear();

            selectedDateMillis = System.currentTimeMillis();
            updateAllDates();
        } else {
            finish();
        }
    }

    private interface OnTabSelect {
        void selectTab(String type);
    }

    private interface OnDebtTypeSelect {
        void selectDebtType(String t);
    }
}
