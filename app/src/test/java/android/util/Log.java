package android.util;

public class Log {
    public static int v(String tag, String msg) {
        return o("V", tag, msg);
    }

    public static int d(String tag, String msg) {
        return o("D", tag, msg);
    }

    public static int i(String tag, String msg) {
        return o("I", tag, msg);
    }

    public static int w(String tag, String msg) {
        return o("W", tag, msg);
    }

    public static int e(String tag, String msg) {
        return o("E", tag, msg);
    }

    private static int o(String prefix, String tag, String msg) {
        String out = prefix + " " + tag + ": " + msg;
        System.out.println(out);
        return out.length();
    }
}