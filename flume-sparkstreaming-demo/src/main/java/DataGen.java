import org.apache.log4j.Logger;

public class DataGen {

    private static Logger logger = Logger.getLogger(DataGen.class);

    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        while (i < 100) {
            Thread.sleep(1000);
            logger.warn("hello " + "world");
            logger.warn("hello " + "spark");
            i++;
        }
    }
}
