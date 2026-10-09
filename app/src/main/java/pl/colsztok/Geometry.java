package pl.colsztok;

public final class Geometry {
    private Geometry() {}
    public static double height(double distance, double base, double top) {
        if (!Double.isFinite(distance) || distance <= 0 || distance > 200 || !Double.isFinite(base) || !Double.isFinite(top)
                || Math.abs(base) >= 80 || Math.abs(top) >= 80 || top <= base)
            throw new IllegalArgumentException("Sprawdź odległość i kąty (−80° do 80°). Oprawa musi być nad podstawą.");
        return distance * (Math.tan(Math.toRadians(top)) - Math.tan(Math.toRadians(base)));
    }
    public static int standard(double height) {
        int best = 6;
        for (int h : new int[]{8,9,12}) if (Math.abs(h-height) < Math.abs(best-height)) best=h;
        return Math.abs(best-height) <= .5 ? best : 0;
    }
    public static double elevation(double x, double y, double z) {
        return Math.toDegrees(Math.atan2(-z, Math.hypot(x,y)));
    }
    public static double lineY(double target, double aim, double fov, double pixels) {
        return pixels/2 - pixels/(2*Math.tan(Math.toRadians(fov/2))) * Math.tan(Math.toRadians(target-aim));
    }
}
