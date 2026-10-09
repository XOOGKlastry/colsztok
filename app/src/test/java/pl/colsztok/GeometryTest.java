package pl.colsztok;
import org.junit.Test;
import static org.junit.Assert.*;
public class GeometryTest {
    @Test public void unequalGround() { assertEquals(12, Geometry.height(15, 5, Math.toDegrees(Math.atan(12./15+Math.tan(Math.toRadians(5))))), 1e-9); }
    @Test public void classic45() { assertEquals(16.6, Geometry.height(15, Math.toDegrees(Math.atan(-1.6/15)), 45), 1e-9); }
    @Test public void standards() { assertEquals(9, Geometry.standard(9.4)); assertEquals(0, Geometry.standard(10)); }
    @Test public void rearAxis() { assertEquals(0,Geometry.elevation(0,9.81,0),1e-9); assertEquals(45,Geometry.elevation(0,1,-1),1e-9); assertEquals(-45,Geometry.elevation(0,1,1),1e-9); }
    @Test public void projection() { assertEquals(500,Geometry.lineY(30,30,60,1000),1e-9); assertEquals(0,Geometry.lineY(30,0,60,1000),1e-9); }
    @Test(expected=IllegalArgumentException.class) public void invalidDistance() { Geometry.height(0,0,45); }
    @Test(expected=IllegalArgumentException.class) public void invalidOrder() { Geometry.height(15,45,0); }
    @Test(expected=IllegalArgumentException.class) public void invalidNaN() { Geometry.height(Double.NaN,0,45); }
}
