package pl.colsztok;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.*;
import android.hardware.Camera;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SensorEventListener {
    private final int BG=Color.rgb(16,28,31), ACCENT=Color.rgb(216,255,101);
    private SensorManager sensors;
    private Sensor gravity;
    private Camera camera;
    private TextureView preview;
    private FrameLayout frame;
    private Overlay overlay;
    private TextView status, result, resultHint, pitchValue, rollValue, memoryButton;
    private final TextView[] stages=new TextView[3];
    private RollLevel rollLevel;
    private boolean measurementSaved;
    private EditText distance, name;
    private Button capture, save, newMeasurement;
    private SharedPreferences prefs;
    private double angle, roll, offset, fov=65, base=Double.NaN, top=Double.NaN, baseDistance, height=Double.NaN;
    private long lastSensor, lastUiUpdate;
    private final ArrayDeque<Double> samples=new ArrayDeque<>();
    private boolean cameraReady, active, started, capturedCalibrated;
    private JSONArray records;
    private JSONObject pending;
    private Bitmap shot;
    private String exportData;
    private int previewW=1280, previewH=720;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("colsztok",MODE_PRIVATE);
        offset=prefs.getFloat("offset",0);
        try { records=new JSONArray(prefs.getString("records","[]")); }
        catch(JSONException e) { records=new JSONArray(); toast("Nie udało się odczytać historii. Nie zapisuj nowych danych przed wykonaniem kopii."); finish(); return; }
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        gravity=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if(gravity==null) gravity=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        build();
    }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private TextView label(String text, int size) {
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(dp(8),dp(4),dp(8),dp(4)); return t;
    }
    private GradientDrawable surface(int color,int radius) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private void styleButton(Button b, boolean primary) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},surface(0xff263b3f,14));
        states.addState(new int[]{android.R.attr.state_pressed},surface(primary?0xffb4d74d:0xff365258,14));
        states.addState(new int[]{},surface(primary?ACCENT:0xff20373c,14));
        b.setBackgroundTintList(null);b.setBackground(states);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff819799,primary?BG:0xffedf4f0}));
    }
    private Button button(String title, Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));styleButton(b,false);b.setOnClickListener(v->action.run());return b;
    }
    private EditText input(String hint, String value, boolean number) {
        EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff9caeaf);e.setTextSize(16);e.setHint(hint);e.setSingleLine();e.setPadding(dp(12),dp(8),dp(12),dp(8));e.setBackground(surface(0xff20373c,12));
        e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED:InputType.TYPE_CLASS_TEXT);
        e.setText(value);return e;
    }
    private LinearLayout field(String title,EditText edit) {
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);
        TextView caption=label(title,11);caption.setPadding(dp(2),dp(2),0,dp(6));caption.setTextColor(0xffb4c8c9);group.addView(caption);group.addView(edit,new LinearLayout.LayoutParams(-1,dp(46)));return group;
    }
    private void build() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(8),dp(16),dp(10));root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(16),insets.getSystemWindowInsetTop()+dp(8),dp(16),insets.getSystemWindowInsetBottom()+dp(10));return insets.consumeSystemWindowInsets();});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("Colsztok",27);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);title.setTextColor(ACCENT);title.setPadding(0,0,0,0);brand.addView(title);
        TextView subtitle=label("Wysokość latarni",12);subtitle.setPadding(0,0,0,dp(6));subtitle.setTextColor(0xffa7babc);brand.addView(subtitle);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        memoryButton=button("Pamięć · "+records.length(),this::history);bar.addView(memoryButton);
        Button more=button("⋮",this::menu);more.setContentDescription("Menu: instrukcja, kalibracja, eksport");LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(44),dp(48));moreParams.leftMargin=dp(8);bar.addView(more,moreParams);root.addView(bar);
        LinearLayout progress=new LinearLayout(this);
        for(int i=0;i<3;i++){stages[i]=label(new String[]{"1  Podstawa","2  Oprawa","3  Zapis"}[i],12);stages[i].setGravity(Gravity.CENTER);stages[i].setPadding(dp(4),dp(7),dp(4),dp(7));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=dp(6);progress.addView(stages[i],p);}
        LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,-2);progressParams.topMargin=dp(8);progressParams.bottomMargin=dp(10);root.addView(progress,progressParams);
        frame=new FrameLayout(this);frame.setBackground(surface(Color.BLACK,18));frame.setClipToOutline(true);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        preview=new TextureView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        overlay=new Overlay();frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        Button guide=button("Jak mierzyć?",this::help);guide.setTextSize(12);FrameLayout.LayoutParams guideParams=new FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP|Gravity.RIGHT);guideParams.setMargins(0,dp(8),dp(8),0);frame.addView(guide,guideParams);
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fitPreview());
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s){closeCamera();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
        ScrollView controlsScroll=new ScrollView(this);controlsScroll.setFillViewport(true);controlsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controlsScroll.addView(controls);
        // Cap controls on short screens so the camera and actions remain reachable, including with a keyboard.
        int maxControls=(int)(getResources().getDisplayMetrics().heightPixels*.53f);
        root.addView(controlsScroll,new LinearLayout.LayoutParams(-1,Math.min(dp(358),maxControls)));
        LinearLayout angles=new LinearLayout(this);pitchValue=label("Celowanie —°",13);rollValue=label("Na bok —°",13);rollValue.setGravity(Gravity.RIGHT);
        angles.addView(pitchValue,new LinearLayout.LayoutParams(0,-2,1));angles.addView(rollValue,new LinearLayout.LayoutParams(0,-2,1));controls.addView(angles);
        rollLevel=new RollLevel();controls.addView(rollLevel,new LinearLayout.LayoutParams(-1,dp(24)));
        status=label("Uruchamianie czujników…",12);status.setTextColor(0xffb4c8c9);controls.addView(status);
        LinearLayout fields=new LinearLayout(this);distance=input("np. 15",prefs.getString("distance","15"),true);name=input("np. L-024","",false);
        fields.addView(field("ODLEGŁOŚĆ POZIOMA [m]",distance),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.leftMargin=dp(10);fields.addView(field("NUMER SŁUPA / UWAGI",name),nameParams);controls.addView(fields);
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(12),dp(7),dp(12),dp(7));tile.setBackground(surface(0xff1a2f33,14));
        result=label("Złap podstawę słupa",18);result.setPadding(0,0,0,0);tile.addView(result);resultHint=label("Ustaw krzyżyk na styku słupa z gruntem.",12);resultHint.setTextColor(0xffb4c8c9);resultHint.setPadding(0,dp(2),0,0);tile.addView(resultHint);
        LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(-1,-2);tileParams.setMargins(0,dp(10),0,dp(8));controls.addView(tile,tileParams);
        capture=button("1 · Złap podstawę",this::captureAngle);styleButton(capture,true);capture.setEnabled(false);controls.addView(capture,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout actions=new LinearLayout(this);newMeasurement=button("＋ Nowy pomiar",this::requestNewMeasurement);actions.addView(newMeasurement,new LinearLayout.LayoutParams(0,dp(48),1));save=button("Zapisz pomiar",this::saveMeasurement);save.setEnabled(false);LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(0,dp(48),1);saveParams.leftMargin=dp(8);actions.addView(save,saveParams);LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,-2);actionParams.topMargin=dp(8);controls.addView(actions,actionParams);
        updateStages();
        if(!prefs.getBoolean("guide_v2",false)){prefs.edit().putBoolean("guide_v2",true).apply();root.post(this::help);}
    }
    private void updateStages() {
        int current=measurementSaved?3:Double.isNaN(base)?0:Double.isNaN(top)?1:2;
        for(int i=0;i<3;i++){stages[i].setTextColor(i==current?BG:i<current?ACCENT:0xffa7babc);stages[i].setBackground(surface(i==current?ACCENT:0xff20373c,10));stages[i].setContentDescription(stages[i].getText()+(i==current?", bieżący etap":i<current?", gotowe":""));}
    }
    private void requestNewMeasurement() {
        if(!measurementSaved&&!Double.isNaN(base))new AlertDialog.Builder(this).setTitle("Nowy pomiar?").setMessage("Bieżący pomiar nie został zapisany. Rozpocząć od nowa?").setPositiveButton("Nowy pomiar",(d,w)->reset()).setNegativeButton("Wróć",null).show();else reset();
    }
    private double number(EditText e) { return Double.parseDouble(e.getText().toString().trim().replace(',','.')); }
    private boolean stable() {
        if(SystemClock.elapsedRealtime()-lastSensor>600 || samples.size()<12 || Math.abs(roll)>3) return false;
        double min=Collections.min(samples),max=Collections.max(samples);
        return max-min<.6;
    }
    private double mean() { double sum=0; for(double x:samples) sum+=x; return sum/samples.size(); }
    @Override public void onSensorChanged(SensorEvent event) {
        float[] g=event.values; double next=Geometry.elevation(g[0],g[1],g[2])+offset;
        roll=Math.toDegrees(Math.atan2(g[0],g[1])); angle=next;
        samples.add(next); while(samples.size()>18) samples.remove(); lastSensor=SystemClock.elapsedRealtime();
        if(lastSensor-lastUiUpdate<120)return;
        lastUiUpdate=lastSensor;
        pitchValue.setText(String.format(Locale.US,"Celowanie %+.1f°",angle));
        rollValue.setText(String.format(Locale.US,"Na bok %+.1f°",roll));rollValue.setTextColor(Math.abs(roll)>3?0xffffa18c:ACCENT);
        String message=!cameraReady?"Aparat niedostępny — sprawdź uprawnienia.":Math.abs(roll)>3?"Wyprostuj na bok — dopuszczalne ±3°.":stable()?"● Stabilnie — możesz złapać punkt.":"Poczekaj — utrzymaj obiektyw nieruchomo.";
        status.setText(message);status.setTextColor(stable()&&cameraReady?ACCENT:0xffb4c8c9);
        capture.setEnabled(!measurementSaved && cameraReady && stable());rollLevel.invalidate();overlay.invalidate();
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy) {}
    private void captureAngle() {
        if(measurementSaved)return;
        if(!cameraReady || !stable()) { toast("Utrzymaj telefon nieruchomo i pionowo przez około sekundę."); return; }
        try {
            if(Double.isNaN(base)) {
                baseDistance=number(distance);
                if(!Double.isFinite(baseDistance)||baseDistance<1||baseDistance>200) throw new IllegalArgumentException("Wpisz odległość od 1 do 200 m.");
                base=mean(); distance.setEnabled(false); top=Double.NaN;
                result.setText("Teraz wyceluj w oprawę");resultHint.setText(String.format(Locale.US,"Podstawa %+.1f° · aparat zostaje w tym samym miejscu.",base));capture.setText("2 · Złap oprawę");updateStages();
            } else {
                double candidate=mean(); double measured=Geometry.height(baseDistance,base,candidate);
                if(measured>50 || measured<.5) throw new IllegalArgumentException("Wynik poza zakresem 0,5–50 m. Sprawdź celowanie.");
                top=candidate; height=measured; capturedCalibrated=prefs.getBoolean("calibrated",false);
                if(shot!=null) shot.recycle(); shot=null;
                try { Bitmap b=Bitmap.createBitmap(frame.getWidth(),frame.getHeight(),Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(b); canvas.drawColor(Color.BLACK);
                    Bitmap p=preview.getBitmap(); if(p!=null){canvas.drawBitmap(p,preview.getLeft(),preview.getTop(),null);p.recycle();} overlay.draw(canvas); shot=b;
                } catch(RuntimeException e) { toast("Pomiar gotowy, zdjęcie niedostępne."); }
                int standard=Geometry.standard(height);
                result.setText(String.format(Locale.US,"%.2f m",height));result.setTextSize(28);result.setTextColor(ACCENT);
                resultHint.setText((standard==0?"Poza klasami ±0,5 m":"Klasa "+standard+" m")+" · uzupełnij numer i zapisz.");
                capture.setText("Powtórz celowanie w oprawę");styleButton(capture,false);save.setEnabled(true);styleButton(save,true);updateStages();
            }
        } catch(Exception e) { toast(e instanceof NumberFormatException?"Wpisz poprawną odległość w metrach.":e.getMessage()); }
    }
    private void saveMeasurement() {
        if(measurementSaved||!Double.isFinite(height)) return;
        String photo="";
        try {
            long now=System.currentTimeMillis();
            if(shot!=null) { photo="pomiar-"+UUID.randomUUID()+".jpg"; try(FileOutputStream out=openFileOutput(photo,MODE_PRIVATE)){if(!shot.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Zdjęcie");} }
            JSONObject row=new JSONObject(); row.put("id",UUID.randomUUID().toString()); row.put("time",now); row.put("name",name.getText().toString()); row.put("height_m",height);
            row.put("distance_m",baseDistance); row.put("base_deg",base); row.put("top_deg",top); row.put("offset_deg",offset); row.put("class_m",Geometry.standard(height)); row.put("calibrated",capturedCalibrated); row.put("photo",photo);
            JSONArray updated=new JSONArray(records.toString()); updated.put(row);
            if(!prefs.edit().putString("records",updated.toString()).putString("distance",distance.getText().toString()).commit()) throw new IOException("Brak miejsca na zapis.");
            records=updated;measurementSaved=true;memoryButton.setText("Pamięć · "+records.length());save.setText("Zapisano ✓");save.setEnabled(false);capture.setEnabled(false);capture.setText("Pomiar zapisany w pamięci");name.setEnabled(false);resultHint.setText(photo.isEmpty()?"Zapisano dane bez zdjęcia · wybierz Nowy pomiar.":"Zapisano ze zdjęciem · wybierz Nowy pomiar.");styleButton(newMeasurement,true);updateStages();toast("Zapisano pomiar #"+records.length());
        } catch(Exception e) { if(!photo.isEmpty())deleteFile(photo); toast("Nie zapisano: "+e.getMessage()); }
    }
    private void reset() {
        base=top=height=Double.NaN;measurementSaved=false;if(shot!=null){shot.recycle();shot=null;}
        distance.setEnabled(true);name.setEnabled(true);name.setText("");save.setText("Zapisz pomiar");save.setEnabled(false);styleButton(save,false);styleButton(capture,true);styleButton(newMeasurement,false);
        capture.setText("1 · Złap podstawę");capture.setEnabled(cameraReady&&stable());result.setText("Złap podstawę słupa");result.setTextSize(18);result.setTextColor(Color.WHITE);resultHint.setText("Ustaw krzyżyk na styku słupa z gruntem.");updateStages();overlay.invalidate();
    }
    private void history() {
        if(records.length()==0){toast("Pamięć jest pusta. Zapisz pierwszy pomiar.");return;}
        String[] names=new String[records.length()];
        for(int i=0;i<names.length;i++){JSONObject r=records.optJSONObject(names.length-1-i); names[i]=String.format(Locale.US,"%.2f m  ·  %s\n%s",r.optDouble("height_m"),r.optString("name",""),date(r.optLong("time")));}
        new AlertDialog.Builder(this).setTitle("Pamięć • "+records.length()+" pomiarów").setItems(names,(d,i)->detail(records.length()-1-i)).setPositiveButton("Eksport",(d,w)->exports()).setNegativeButton("Zamknij",null).show();
    }
    private String date(long t) { return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date(t)); }
    private void detail(int index) {
        JSONObject row=records.optJSONObject(index);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView t=new TextView(this);t.setText(String.format(Locale.US,"%s\n%s\nWysokość: %.2f m\nOdległość pozioma: %.2f m\nPodstawa: %.2f°  •  Oprawa: %.2f°\nKalibracja: %s\nKlasa: %s",row.optString("name"),date(row.optLong("time")),row.optDouble("height_m"),row.optDouble("distance_m"),row.optDouble("base_deg"),row.optDouble("top_deg"),row.optBoolean("calibrated")?"wykonana":"brak",row.optInt("class_m")==0?"poza klasami":row.optInt("class_m")+" m"));layout.addView(t);
        Bitmap photo=BitmapFactory.decodeFile(new File(getFilesDir(),row.optString("photo")).getPath());
        if(photo!=null){ImageView image=new ImageView(this);image.setImageBitmap(photo);image.setAdjustViewBounds(true);layout.addView(image,new LinearLayout.LayoutParams(-1,dp(250)));}
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        new AlertDialog.Builder(this).setTitle("Zapisany pomiar").setView(scroll).setPositiveButton("OK",null).setNeutralButton("Zdjęcie",(d,w)->{
            if(row.optString("photo").isEmpty()){toast("Brak zdjęcia.");return;} pending=row;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,row.optString("photo"));startActivityForResult(intent,8);
        }).setNegativeButton("Usuń",(d,w)->new AlertDialog.Builder(this).setMessage("Usunąć ten pomiar i zdjęcie?").setPositiveButton("Usuń",(a,b)->delete(index)).setNegativeButton("Anuluj",null).show()).show();
    }
    private void delete(int index) {
        JSONArray next=new JSONArray();for(int i=0;i<records.length();i++)if(i!=index)next.put(records.opt(i));
        if(prefs.edit().putString("records",next.toString()).commit()){deleteFile(records.optJSONObject(index).optString("photo"));records=next;memoryButton.setText("Pamięć · "+records.length());toast("Usunięto.");}else toast("Nie udało się usunąć.");
    }
    private String cell(String text) { return "\""+text.replace("\"","\"\"")+"\""; }
    private void exports() {
        new AlertDialog.Builder(this).setTitle("Eksport pomiarów").setItems(new String[]{"CSV • tabela / QGIS","JSON • pełne dane"},(d,i)->{
            try {
                if(i==1)exportData=records.toString(2);
                else { StringBuilder b=new StringBuilder("id;czas;opis;wysokosc_m;odleglosc_pozioma_m;podstawa_deg;oprawa_deg;klasa_m;kalibracja;zdjecie\r\n");
                    for(int n=0;n<records.length();n++){JSONObject r=records.getJSONObject(n);String desc=r.optString("name"); if(desc.matches("^[=+@-].*"))desc="'"+desc;
                        b.append(cell(r.optString("id"))).append(';').append(cell(date(r.optLong("time")))).append(';').append(cell(desc)).append(';').append(r.getDouble("height_m")).append(';').append(r.getDouble("distance_m")).append(';').append(r.getDouble("base_deg")).append(';').append(r.getDouble("top_deg")).append(';').append(r.optInt("class_m")).append(';').append(r.optBoolean("calibrated")).append(';').append(cell(r.optString("photo"))).append("\r\n");}exportData=b.toString(); }
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(i==0?"text/csv":"application/json").putExtra(Intent.EXTRA_TITLE,"Colsztok-"+System.currentTimeMillis()+(i==0?".csv":".json"));startActivityForResult(intent,7);
            }catch(Exception e){toast("Błąd eksportu: "+e.getMessage());}
        }).show();
    }
    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IOException("Brak dostępu do pliku.");
            if(req==7&&exportData!=null)out.write(exportData.getBytes(StandardCharsets.UTF_8));
            else if(req==8&&pending!=null)try(InputStream in=openFileInput(pending.optString("photo"))){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            else throw new IOException("Powtórz eksport po ponownym otwarciu aplikacji.");
            toast("Wyeksportowano.");
        }catch(Exception e){toast("Nie udało się zapisać pliku: "+e.getMessage());}
    }
    private void menu() {new AlertDialog.Builder(this).setTitle("Colsztok").setItems(new String[]{"Instrukcja","Kalibracja poziomu","Eksport CSV / JSON","Informacje"},(d,i)->{if(i==0)help();if(i==1)calibrate();if(i==2)exports();if(i==3)new AlertDialog.Builder(this).setMessage("Colsztok 1.1.0\nPomiar offline • Android 8+\nWyniki i zdjęcia pozostają w telefonie. Odinstalowanie usuwa pamięć — wcześniej eksportuj dane.\nSiatka jest orientacyjna, korzysta z FOV aparatu.\nProjekt: github.com/XOOGKlastry/colsztok").setPositiveButton("OK",null).show();}).show();}
    private void help() {startActivity(new Intent(this,GuideActivity.class));}
    private void calibrate() {
        new AlertDialog.Builder(this).setTitle("Kalibracja kąta 0°").setMessage("Wyceluj krzyżykiem w punkt dokładnie na wysokości środka obiektywu. Telefon pionowo, aparat nieruchomo. Kalibracja zeruje błąd kąta; nie kalibruje FOV siatki.\n\nZapis usunie bieżący, niezapisany pomiar.").setPositiveButton("Ustaw zero",(d,w)->{
            if(!stable()||!cameraReady){toast("Najpierw ustabilizuj aparat.");return;}
            double newOffset=offset-mean();
            if(Math.abs(newOffset)>10){toast("Korekta >10°. Sprawdź ustawienie aparatu.");return;}
            if(prefs.edit().putFloat("offset",(float)newOffset).putBoolean("calibrated",true).commit()){offset=newOffset;samples.clear();reset();toast("Zapisano kalibrację.");}
        }).setNeutralButton("Wyzeruj korektę",(d,w)->{if(prefs.edit().remove("offset").remove("calibrated").commit()){offset=0;samples.clear();reset();}}).setNegativeButton("Anuluj",null).show();
    }
    private void fitPreview() {
        if(frame==null||frame.getWidth()==0)return;
        float aspect=(float)previewH/previewW;int w=frame.getWidth(),h=Math.round(w/aspect);
        if(h>frame.getHeight()){h=frame.getHeight();w=Math.round(h*aspect);}
        if(preview.getLayoutParams().width==w && preview.getLayoutParams().height==h)return;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,Gravity.CENTER); preview.setLayoutParams(p);
    }
    private void openCamera() {
        if(!active||camera!=null||!preview.isAvailable())return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){if(!started){started=true;requestPermissions(new String[]{Manifest.permission.CAMERA},1);}return;}
        try {
            int id=-1;for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}
            if(id<0)throw new IOException("Brak tylnej kamery.");
            camera=Camera.open(id);Camera.Parameters params=camera.getParameters();
            Camera.Size best=null;for(Camera.Size s:params.getSupportedPreviewSizes())if(s.width<=1920&&(best==null||Math.abs((double)s.width/s.height-4./3)<Math.abs((double)best.width/best.height-4./3)||Math.abs((double)s.width/s.height-(double)best.width/best.height)<.01&&s.width>best.width))best=s;
            if(best==null)best=params.getSupportedPreviewSizes().get(0);
            previewW=best.width;previewH=best.height;params.setPreviewSize(previewW,previewH);
            if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            if(params.isZoomSupported())params.setZoom(0);camera.setParameters(params);fov=camera.getParameters().getHorizontalViewAngle();
            Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(id,info);
            if(info.orientation!=90)throw new IOException("Nieobsługiwana orientacja tylnej kamery (wymagana 90°).");
            camera.setDisplayOrientation(90);camera.setPreviewTexture(preview.getSurfaceTexture());camera.startPreview();cameraReady=true;fitPreview();
        }catch(Exception e){closeCamera();toast("Aparat niedostępny: "+e.getMessage()+". Sprawdź uprawnienie w ustawieniach Androida.");}
    }
    private void closeCamera() {cameraReady=false;if(camera!=null){try{camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}}
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();else toast("Aparat wymaga zgody. Włącz ją w Ustawienia → Aplikacje → Colsztok.");}
    @Override protected void onResume(){super.onResume();active=true;samples.clear();if(gravity!=null)sensors.registerListener(this,gravity,SensorManager.SENSOR_DELAY_GAME);else toast("Brak czujnika pochylenia — pomiar niedostępny.");if(preview!=null&&preview.isAvailable())openCamera();}
    @Override protected void onPause(){active=false;sensors.unregisterListener(this);closeCamera();super.onPause();}
    @Override protected void onDestroy(){if(shot!=null)shot.recycle();super.onDestroy();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}

    private class RollLevel extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        RollLevel(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,span=getWidth()*.36f;
            p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xff365258);c.drawLine(cx-span,cy,cx+span,cy,p);
            p.setColor(0xff788e90);p.setStrokeWidth(dp(1));for(int i=-1;i<=1;i++)c.drawLine(cx+i*span*.3f,cy-dp(5),cx+i*span*.3f,cy+dp(5),p);
            p.setColor(Math.abs(roll)<=3?ACCENT:0xffffa18c);float x=cx+(float)Math.max(-10,Math.min(10,roll))/10*span;c.drawCircle(x,cy,dp(5),p);
        }
    }
    private class Overlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Overlay(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float cx=preview.getLeft()+preview.getWidth()/2f,cy=preview.getTop()+preview.getHeight()/2f;
            paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);c.drawLine(cx-dp(17),cy,cx+dp(17),cy,paint);c.drawLine(cx,cy-dp(17),cx,cy+dp(17),paint);
            paint.setStyle(Paint.Style.STROKE);c.drawCircle(cx,cy,dp(25),paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(12));paint.setShadowLayer(dp(3),0,0,Color.BLACK);
            c.drawText(measurementSaved?"ZAPISANO":Double.isNaN(base)?"PODSTAWA":"OPRAWA",dp(14),dp(31),paint);
            if(!Double.isNaN(base)&&Math.abs(roll)<3&&cameraReady){
                c.save();c.clipRect(preview.getLeft(),preview.getTop(),preview.getRight(),preview.getBottom());
                int[] heights={6,8,9,12};int[] colors={0xffff0000,0xffffa500,0xffffff00,0xff00ff00};
                for(int i=0;i<heights.length;i++){
                    double target=Math.toDegrees(Math.atan(heights[i]/baseDistance+Math.tan(Math.toRadians(base))));
                    if(Math.abs(target-angle)>75)continue;
                    float y=preview.getTop()+(float)Geometry.lineY(target,angle,fov,preview.getHeight());
                    paint.setColor(colors[i]);c.drawLine(preview.getLeft()+dp(8),y,preview.getRight()-dp(8),y,paint);c.drawText(heights[i]+" m",preview.getLeft()+dp(12),y-dp(5),paint);
                }c.restore();
            }
            paint.setColor(Color.WHITE);paint.setTextSize(dp(10));c.drawText("SIATKA ORIENTACYJNA • 1× • bez zoomu",dp(12),getHeight()-dp(12),paint);paint.clearShadowLayer();
        }
    }
}
