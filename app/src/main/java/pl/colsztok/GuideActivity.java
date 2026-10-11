package pl.colsztok;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** The lens is the fixed pivot in the side view; the roll view is a separate front view. */
public class GuideActivity extends Activity {
    private static final int BG=0xff101c1f, PANEL=0xff1c3034, INK=0xffeef4f1, MUTED=0xffa7babc, LIME=0xffd8ff65;
    private int step;
    private TextView heading, description, count;
    private Button previous, next;
    private Diagram diagram;
    private final String[] titles={"1. Celuj w podstawę", "2. Unieś kierunek celowania", "3. Pilnuj przechyłu na bok", "4. Zapisz i rozpocznij kolejny"};
    private final String[] descriptions={
        "Zmierz odległość POZIOMĄ do osi słupa i wpisz ją w aplikacji. Telefon trzymaj ekranem do siebie, w orientacji portretowej. Opuść kierunek tylnego aparatu tak, aby środek krzyżyka trafił w podstawę. Poczekaj na stabilny odczyt i naciśnij „Złap podstawę”. Kąt do podstawy zwykle będzie ujemny.",
        "Zostań w tym samym miejscu. Obróć telefon, kierując tylny aparat w górę. Krzyżyk ustaw na wybranym punkcie oprawy i naciśnij „Złap oprawę”. Obracaj możliwie wokół obiektywu: nie podnoś go razem z całą ręką i nie przesuwaj do przodu. Pochylenie do góry jest potrzebne — nie musi wynosić 45°.",
        "To inny ruch: widzisz go jako obrót ekranu jak kierownicy. Lewa i prawa krawędź mają pozostawać pionowe w tym kierunku. Poziomica na ekranie powinna mieć znacznik blisko środka. Przy przechyle bocznym ponad 3° pomiar jest zablokowany. Patrząc na oprawę, nadal możesz pochylać aparat do góry.",
        "Po złapaniu oprawy zobaczysz wynik. Uzupełnij numer słupa lub uwagi i naciśnij „Zapisz pomiar”. Pomiar i zdjęcie znajdziesz w Pamięci. „Nowy pomiar” czyści bieżące celowanie i numer słupa; zachowuje wpisaną odległość do następnego stanowiska. Sprawdź ją przed każdym pomiarem."
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(state!=null)step=state.getInt("step",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(20),dp(12),dp(20),dp(16));
        root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(20),i.getSystemWindowInsetTop()+dp(12),dp(20),i.getSystemWindowInsetBottom()+dp(16));return i.consumeSystemWindowInsets();});
        setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("Colsztok / Jak mierzyć",17,LIME);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(action("Zamknij",()->finish()));root.addView(bar);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        heading=text("",25,INK);content.addView(heading);
        diagram=new Diagram();content.addView(diagram,new LinearLayout.LayoutParams(-1,dp(270)));
        description=text("",16,INK);description.setLineSpacing(dp(4),1);description.setPadding(0,dp(14),0,dp(16));content.addView(description);
        TextView note=text("Przykład: 15 m od słupa, obiektyw 1,6 m nad podstawą, oprawa 9 m. Kąty −6,1° i +26,3°. W terenie będą inne.\n\nPrzed inwentaryzacją: kalibracja poziomu i 3 powtórzenia na znanej wysokości. ±0,5 m wymaga sprawdzenia w terenie. Siatka jest orientacyjna. Wysięgnik oglądaj z boku, żeby oprawa nie była bliżej lub dalej od osi słupa.",13,MUTED);content.addView(note);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        previous=action("Wstecz",()->show(step-1));next=action("Dalej",()->{if(step==3)finish();else show(step+1);});
        nav.addView(previous,new LinearLayout.LayoutParams(0,dp(52),1));count=text("",14,MUTED);count.setGravity(Gravity.CENTER);nav.addView(count,new LinearLayout.LayoutParams(dp(62),-2));nav.addView(next,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(nav);show(step);
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button action(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(LIME);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(v->r.run());return b;}
    private void show(int index){step=Math.max(0,Math.min(3,index));heading.setText(titles[step]);description.setText(descriptions[step]);count.setText((step+1)+" / 4");previous.setEnabled(step>0);next.setText(step==3?"Mierzę":"Dalej");diagram.setContentDescription(titles[step]+". "+descriptions[step]);diagram.invalidate();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("step",step);super.onSaveInstanceState(state);}
    private class Diagram extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Diagram(){super(GuideActivity.this);}
        void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
        void text(Canvas c,String s,float x,float y,int color,float size){p.setColor(color);p.setTextSize(size);c.drawText(s,x,y,p);}
        void phone(Canvas c,float x,float y,float degrees,boolean front,int color){
            c.save();c.rotate(degrees,x,y);p.setColor(PANEL);c.drawRoundRect(x-15,y-13,x+15,y+49,5,5,p);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(x-15,y-13,x+15,y+49,5,5,p);p.setStyle(Paint.Style.FILL);
            if(front){line(c,x-8,y+17,x+8,y+17,color,1.5f);line(c,x,y+9,x,y+25,color,1.5f);}else{p.setColor(LIME);c.drawCircle(x+12,y,3,p);}
            c.restore();
        }
        @Override protected void onDraw(Canvas canvas){
            // 360 units keeps structural positions proportional; label size compensates for narrow views.
            float scale=getWidth()/360f;Canvas c=canvas;c.save();c.scale(scale,scale);float textSize=dp(12)/scale;
            p.setColor(PANEL);c.drawRoundRect(0,14,360,Math.min(getHeight()/scale-3,270),16,16,p);
            if(step==2){
                text(c,"WIDOK OD STRONY EKRANU",18,40,MUTED,textSize);
                line(c,90,65,90,187,MUTED,1);line(c,270,65,270,187,MUTED,1);
                phone(c,90,100,0,true,LIME);phone(c,264,100,-18,true,0xffff957d);
                text(c,"0° · poprawnie",28,215,INK,textSize);text(c,"18° · blokada",199,215,INK,textSize);
                text(c,"Na bok: najwyżej ±3°",18,249,INK,textSize);
            }else if(step==3){
                p.setColor(LIME);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawCircle(180,109,36,p);p.setStyle(Paint.Style.FILL);
                line(c,162,109,176,122,LIME,4);line(c,176,122,201,96,LIME,4);
                text(c,"9,00 m",18,190,INK,textSize*2);text(c,"Zapisz → Pamięć → Nowy pomiar",18,234,INK,textSize);
            }else{
                float lensX=72,lensY=173,poleX=294,ground=197,tip=64;
                line(c,20,ground,330,ground,MUTED,1);line(c,poleX,ground,poleX,tip,INK,4);line(c,poleX,tip,poleX+25,tip,INK,4);
                p.setColor(INK);c.drawRoundRect(poleX+18,tip-3,poleX+33,tip+4,2,2,p);
                line(c,lensX,lensY,poleX,lensY,MUTED,1);
                int beamColor=LIME;float targetY=step==0?ground:tip;
                line(c,lensX,lensY,poleX,targetY,beamColor,2.5f);
                // Portrait phone side, rotation centered on the rear lens, not on the hand.
                c.save();float rot=step==0?6.1f:-26.3f;c.rotate(rot,lensX,lensY);p.setColor(INK);c.drawRoundRect(lensX-9,lensY-8,lensX,lensY+39,2,2,p);c.restore();
                p.setColor(LIME);c.drawCircle(lensX,lensY,4,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawArc(lensX-49,lensY-49,lensX+49,lensY+49,step==0?0:-26.3f,step==0?6.1f:26.3f,false,p);p.setStyle(Paint.Style.FILL);
                text(c,"WIDOK Z BOKU",18,40,MUTED,textSize);text(c,step==0?"Podstawa · −6,1°":"Oprawa · +26,3°",18,71,INK,textSize);
                text(c,"Obiektyw",18,ground+29,INK,textSize);text(c,"15 m w poziomie",133,ground+29,INK,textSize);
                text(c,"Ten sam punkt aparatu",18,ground+54,MUTED,textSize);
            }c.restore();
        }
    }
}
