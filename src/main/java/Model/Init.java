package Model;

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
                FileWriter myWriter = new FileWriter("Scale_Weight.txt");
                myWriter.write(String.valueOf(weight));
                myWriter.close();
                System.out.println("Successfully wrote to the file.");
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        } catch (Exception e) {
            try {
                FileWriter myWriter = new FileWriter("Scale_Weight.txt");
                myWriter.write("Error! " + e.getMessage());
                myWriter.close();
                System.out.println("Successfully wrote to the file.");
            } catch (IOException exception) {
                System.out.println("An error occurred.");
                exception.printStackTrace();
            }
        }

    }

}