package Model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Init {

    //Vendor ID = 0x0922
    //Product ID = 0x8009

    public static void main(String[] args) {
        try {
            UsbScale scale = UsbScale.findScale();
            assert scale != null;
            scale.open();
            double weight = 0;
            try {
                while (weight == 0) {
                    weight = scale.syncSubmit();
                }
            } finally {
                scale.close();
            }
            weight = (double)Math.round(weight * 10d) / 10d;
            try {
                String home = System.getProperty("user.home");
                File f = new File(home + File.separator + "Desktop" + File.separator + "Java.txt");

                BufferedWriter out = new BufferedWriter(new FileWriter(f));
                try {
                    out.write(String.valueOf(weight));
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        } catch (Exception e) {
            try {
                String home = System.getProperty("user.home");
                File f = new File(home + File.separator + "Desktop" + File.separator + "Java.txt");

                BufferedWriter out = new BufferedWriter(new FileWriter(f));
                try {
                    out.write("Error! " + e.getMessage());
                } finally {
                    out.close();
                }
            } catch (IOException exception) {
                System.out.println("An error occurred.");
                exception.printStackTrace();
            }
        }

    }

}