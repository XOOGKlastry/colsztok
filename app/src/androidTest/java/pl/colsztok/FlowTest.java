package pl.colsztok;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FlowTest {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object get(Object a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void set(Object a,String name,Object value)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
    private void call(Object a,String name)throws Exception{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private TextView text(View v,String needle){if(v instanceof TextView&&((TextView)v).getText().toString().equals(needle))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),needle);if(t!=null)return t;}return null;}
    private void click(Activity a,String value){inst.runOnMainSync(()->{TextView t=text(a.getWindow().getDecorView(),value);assertNotNull(value,t);assertTrue(t.performClick());});inst.waitForIdleSync();}
    private void screenshot(Activity a,String name)throws Exception{inst.waitForIdleSync();SystemClock.sleep(400);Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(a.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked") private void aim(MainActivity a,double degrees)throws Exception{
        ArrayDeque<Double> samples=(ArrayDeque<Double>)get(a,"samples");samples.clear();for(int i=0;i<18;i++)samples.add(degrees);set(a,"lastSensor",SystemClock.elapsedRealtime());set(a,"roll",0d);set(a,"angle",degrees);set(a,"cameraReady",true);call(a,"captureAngle");
    }
    @Test public void saveThenNewMeasurementAndGuide()throws Exception{
        Context context=inst.getTargetContext();context.getSharedPreferences("colsztok",Context.MODE_PRIVATE).edit().clear().putBoolean("guide_v2",true).commit();
        try(android.os.ParcelFileDescriptor ignored=inst.getUiAutomation().executeShellCommand("pm grant pl.colsztok android.permission.CAMERA")){SystemClock.sleep(400);}
        MainActivity a=(MainActivity)inst.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        screenshot(a,"01-main");
        inst.runOnMainSync(()->{try{((SensorManager)get(a,"sensors")).unregisterListener(a);((EditText)get(a,"name")).setText("TEST-024");aim(a,Math.toDegrees(Math.atan(-1.6/15)));aim(a,Math.toDegrees(Math.atan(7.4/15)));}catch(Exception e){throw new RuntimeException(e);}});
        screenshot(a,"02-result");
        click(a,"Zapisz pomiar");assertEquals(1,new org.json.JSONArray(context.getSharedPreferences("colsztok",Context.MODE_PRIVATE).getString("records","[]")).length());assertEquals(true,get(a,"measurementSaved"));
        screenshot(a,"03-saved");click(a,"＋ Nowy pomiar");assertTrue(Double.isNaN((Double)get(a,"base")));assertTrue(Double.isNaN((Double)get(a,"height")));assertEquals("",((EditText)get(a,"name")).getText().toString());assertEquals("15",((EditText)get(a,"distance")).getText().toString());assertFalse(((Button)get(a,"save")).isEnabled());
        GuideActivity guide=(GuideActivity)inst.startActivitySync(new Intent(context,GuideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        screenshot(guide,"04-guide-base");click(guide,"Dalej");assertNotNull(text(guide.getWindow().getDecorView(),"2. Unieś kierunek celowania"));screenshot(guide,"05-guide-top");click(guide,"Dalej");screenshot(guide,"06-guide-roll");click(guide,"Dalej");click(guide,"Mierzę");
        inst.runOnMainSync(a::finish);
    }
}
