public class InputMetricsAndSizesViolations {

    private Object dependency1;
    private String dependency2;
    private Integer dependency3;
    private Double dependency4;
    private Long dependency5;
    private Float dependency6;

    public void veryLongMethod(
            int a, int b, int c, int d,
            int e, int f, int g, int h, int i) {

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

        Runnable anonymousRunnable = new Runnable() {
            public void run() {
                int y = 0;
                y++;
                y++;
                y++;
                y++;
                y++;
                y++;
            }
        };

        Runnable longLambda = () -> {
            int z = 0;
            z++;
            z++;
            z++;
            z++;
            z++;
            z++;
        };

        anonymousRunnable.run();
        longLambda.run();
    }

    public void complexDecisionMethod(int a, int b, int c) {

        if (a > 0) {
            if (b > 0) {
                if (c > 0) {
                    System.out.println("A");
                }
                else {
                    System.out.println("B");
                }
            }
            else if (b < 0) {
                System.out.println("C");
            }
            else {
                System.out.println("D");
            }
        }
        else {
            System.out.println("E");
        }
    }

    public void methodOne() {}

    public void methodTwo() {}

    public void methodThree() {}

    public void methodFour() {}

    public void methodFive() {}

    class InnerTypeOne {}

    class InnerTypeTwo {}
}