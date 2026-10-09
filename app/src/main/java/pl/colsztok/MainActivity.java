package pl.colsztok;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.*;
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
    private TextView status, result;
    private EditText distance, name;
    private Button capture, save;
    private SharedPreferences prefs;
    private double angle, roll, offset, fov=65, base=Double.NaN, top=Double.NaN, baseDistance, height=Double.NaN;
    private long lastSensor;
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
    private Button button(String title, Runnable action) {
        Button b=new Button(this); b.setText(title); b.setTextColor(BG); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT)); b.setOnClickListener(v->action.run()); return b;
    }
    private EditText input(String hint, String value, boolean number) {
        EditText e=new EditText(this); e.setTextColor(Color.WHITE); e.setHintTextColor(0xff9caeaf); e.setTextSize(16); e.setHint(hint); e.setSingleLine();
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        e.setText(value); return e;
    }
    private void build() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(12),dp(4),dp(12),dp(8)); root.setBackgroundColor(BG); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{ root.setPadding(dp(12),insets.getSystemWindowInsetTop()+dp(4),dp(12),insets.getSystemWindowInsetBottom()+dp(8)); return insets.consumeSystemWindowInsets(); });
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Colsztok",27); title.setTextColor(ACCENT); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(button("Pamięć",this::history)); bar.addView(button("⋮",this::menu)); root.addView(bar);
        TextView intro=label("WYSOKOŚĆ OPRAWY  /  POMIAR DWÓCH KĄTÓW",11); root.addView(intro);
        frame=new FrameLayout(this); frame.setBackgroundColor(Color.BLACK); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        preview=new TextureView(this); frame.addView(preview,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        overlay=new Overlay(); frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fitPreview());
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s){closeCamera();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
        status=label("Uruchamianie czujników…",13); root.addView(status);
        LinearLayout fields=new LinearLayout(this);
        distance=input("Odległość pozioma [m]",prefs.getString("distance","15"),true);
        name=input("Numer słupa / uwagi","",false);
        fields.addView(distance,new LinearLayout.LayoutParams(0,-2,1)); fields.addView(name,new LinearLayout.LayoutParams(0,-2,1)); root.addView(fields);
        result=label("1. Wyceluj środkiem w podstawę słupa",17); root.addView(result);
        capture=button("1  •  ZŁAP PODSTAWĘ",this::captureAngle); root.addView(capture);
        LinearLayout actions=new LinearLayout(this); actions.addView(button("Od nowa",this::reset),new LinearLayout.LayoutParams(0,-2,1));
        save=button("Zapisz pomiar",this::saveMeasurement); save.setEnabled(false); actions.addView(save,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        if(!prefs.getBoolean("intro",false)) help();
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
        status.setText(String.format(Locale.US,"Kąt %+.1f°  •  Przechył %+.1f°  •  %s",angle,roll,stable()?"STABILNIE":"ustabilizuj telefon"));
        capture.setEnabled(cameraReady && stable()); overlay.invalidate();
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy) {}
    private void captureAngle() {
        if(!cameraReady || !stable()) { toast("Utrzymaj telefon nieruchomo i pionowo przez około sekundę."); return; }
        try {
            if(Double.isNaN(base)) {
                baseDistance=number(distance);
                if(!Double.isFinite(baseDistance)||baseDistance<1||baseDistance>200) throw new IllegalArgumentException("Wpisz odległość od 1 do 200 m.");
                base=mean(); distance.setEnabled(false); top=Double.NaN;
                result.setText("2. Bez zmiany stanowiska wyceluj w oprawę"); capture.setText("2  •  ZŁAP OPRAWĘ");
            } else {
                double candidate=mean(); double measured=Geometry.height(baseDistance,base,candidate);
                if(measured>50 || measured<.5) throw new IllegalArgumentException("Wynik poza zakresem 0,5–50 m. Sprawdź celowanie.");
                top=candidate; height=measured; capturedCalibrated=prefs.getBoolean("calibrated",false);
                if(shot!=null) shot.recycle(); shot=null;
                try { Bitmap b=Bitmap.createBitmap(frame.getWidth(),frame.getHeight(),Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(b); canvas.drawColor(Color.BLACK);
                    Bitmap p=preview.getBitmap(); if(p!=null){canvas.drawBitmap(p,preview.getLeft(),preview.getTop(),null);p.recycle();} overlay.draw(canvas); shot=b;
                } catch(RuntimeException e) { toast("Pomiar gotowy, zdjęcie niedostępne."); }
                int standard=Geometry.standard(height);
                result.setText(String.format(Locale.US,"%.2f m  •  %s",height,standard==0?"poza klasami ±0,5 m":"klasa "+standard+" m"));
                capture.setText("POWTÓRZ OPRAWĘ"); save.setEnabled(true);
            }
        } catch(Exception e) { toast(e instanceof NumberFormatException?"Wpisz poprawną odległość w metrach.":e.getMessage()); }
    }
    private void saveMeasurement() {
        if(!Double.isFinite(height)) return;
        String photo="";
        try {
            long now=System.currentTimeMillis();
            if(shot!=null) { photo="pomiar-"+UUID.randomUUID()+".jpg"; try(FileOutputStream out=openFileOutput(photo,MODE_PRIVATE)){if(!shot.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Zdjęcie");} }
            JSONObject row=new JSONObject(); row.put("id",UUID.randomUUID().toString()); row.put("time",now); row.put("name",name.getText().toString()); row.put("height_m",height);
            row.put("distance_m",baseDistance); row.put("base_deg",base); row.put("top_deg",top); row.put("offset_deg",offset); row.put("class_m",Geometry.standard(height)); row.put("calibrated",capturedCalibrated); row.put("photo",photo);
            JSONArray updated=new JSONArray(records.toString()); updated.put(row);
            if(!prefs.edit().putString("records",updated.toString()).putString("distance",distance.getText().toString()).commit()) throw new IOException("Brak miejsca na zapis.");
            records=updated; toast("Zapisano pomiar #"+records.length()); name.setText(""); reset();
        } catch(Exception e) { if(!photo.isEmpty())deleteFile(photo); toast("Nie zapisano: "+e.getMessage()); }
    }
    private void reset() {
        base=top=height=Double.NaN; if(shot!=null){shot.recycle();shot=null;}
        distance.setEnabled(true); save.setEnabled(false); capture.setText("1  •  ZŁAP PODSTAWĘ"); result.setText("1. Wyceluj środkiem w podstawę słupa"); overlay.invalidate();
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
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(16),dp(8),dp(16),dp(8));
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
        if(prefs.edit().putString("records",next.toString()).commit()){deleteFile(records.optJSONObject(index).optString("photo"));records=next;toast("Usunięto.");}else toast("Nie udało się usunąć.");
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
    private void menu() {new AlertDialog.Builder(this).setTitle("Colsztok").setItems(new String[]{"Instrukcja","Kalibracja poziomu","Eksport CSV / JSON","Informacje"},(d,i)->{if(i==0)help();if(i==1)calibrate();if(i==2)exports();if(i==3)new AlertDialog.Builder(this).setMessage("Colsztok 1.0.0\nPomiar offline • Android 8+\nWyniki i zdjęcia pozostają w telefonie. Odinstalowanie usuwa pamięć — wcześniej eksportuj dane.\nSiatka jest orientacyjna, korzysta z FOV aparatu.\nProjekt: github.com/XOOGKlastry/colsztok").setPositiveButton("OK",null).show();}).show();}
    private void help() {
        new AlertDialog.Builder(this).setTitle("Jak mierzyć latarnie").setMessage("1. Zmierz dalmierzem odległość POZIOMĄ do osi słupa, np. 15 m. Wpisz ją w metrach. Ukośny odczyt do podstawy wymaga przeliczenia.\n\n2. Utrzymuj aparat w jednym punkcie. Wyceluj krzyżykiem w podstawę i złap kąt. Następnie przechyl telefon w górę i złap punkt oprawy, którego wysokość chcesz zmierzyć. Nie zmieniaj pozycji aparatu.\n\n3. Sprawdź wynik i zapisz. Zdjęcie z siatką oraz dane trafią do Pamięci.\n\nKolory: czerwony 6 m, pomarańczowy 8 m, żółty 9 m, zielony 12 m. Siatka pojawia się po złapaniu podstawy.\n\nKalibracja: ustaw aparat na cel na tej samej wysokości co obiektyw. Dla dokładności ±0,5 m sprawdź wyniki na znanym słupie; wykonaj 3 powtórzenia. Wysięgnik skierowany do/od Ciebie zmienia odległość do oprawy — wybierz widok z boku.\n\nNie celuj w słońce. Mierz z bezpiecznego stanowiska.").setPositiveButton("Rozumiem",(d,w)->prefs.edit().putBoolean("intro",true).apply()).show();
    }
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

    private class Overlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Overlay(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float cx=preview.getLeft()+preview.getWidth()/2f,cy=preview.getTop()+preview.getHeight()/2f;
            paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);c.drawLine(cx-dp(17),cy,cx+dp(17),cy,paint);c.drawLine(cx,cy-dp(17),cx,cy+dp(17),paint);
            paint.setStyle(Paint.Style.STROKE);c.drawCircle(cx,cy,dp(25),paint);paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(12));paint.setShadowLayer(dp(3),0,0,Color.BLACK);
            c.drawText(Double.isNaN(base)?"CELUJ W PODSTAWĘ":"CELUJ W OPRAWĘ",dp(14),dp(25),paint);
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
