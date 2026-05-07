public class InputMetricsAndSizesViolations {
    private Object a;
    private String b;
    private Integer c;
    private Double d;
    private Long e;
    private Float f;

    public void veryLongMethod(int a, int b, int c, int d, int e, int f, int g, int h) {
        int x = 0;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        if (a > 0 && b > 0 && c > 0 && d > 0 && e > 0 && f > 0) {
            x++;
        }
        else if (a == 0) {
            x++;
        }
        else if (b == 0) {
            x++;
        }
        else {
            x++;
        }

        Runnable r = new Runnable() {
            public void run() {
                int y = 0;
                y++;
                y++;
                y++;
                y++;
                y++;
            }
        };

        Runnable lambda = () -> {
            int z = 0;
            z++;
            z++;
            z++;
            z++;
            z++;
        };
    }

    public void m1() {}
    public void m2() {}
    public void m3() {}
    public void m4() {}
    public void m5() {}

    class Inner1 {}
    class Inner2 {}
}