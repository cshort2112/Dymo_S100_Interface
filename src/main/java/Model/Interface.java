package Model;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Interface extends JFrame {
    private static int response = 1;
    private JLabel display_Current_Weight;
    private JButton cancel_Button;
    private JButton confirm_Button;
    private JPanel main_Panel;
    private JLabel label_Current_Weight;
    private static Interface GUI = new Interface();

    public static void main(String[] args) {
        //Initialize the Main Panel to Display Weight

        GUI.setContentPane(GUI.main_Panel);
        GUI.setTitle("Get Weight - Dymo S100");
        //Set Size to Half of the Screen Size
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        GUI.setSize(screenSize.width / 2, screenSize.height / 2);
        GUI.setLocationRelativeTo(null);
        GUI.confirm_Button.setPreferredSize(new Dimension(40, 80));
        GUI.cancel_Button.setPreferredSize(new Dimension(40, 80));
        GUI.setVisible(true);
        GUI.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);



        //scale stuff
        try {
            UsbScale scale = UsbScale.findScale();
            assert scale != null;
            scale.open();
            double weight = 0;
            while (response != 0) {
                weight = scale.syncSubmit();
                weight = (double) Math.round(weight * 10d) / 10d;
                GUI.display_Current_Weight.setText(String.valueOf(weight));
            }
        } catch (NullPointerException e) {
            create_Error("NullPointerException! " + e.getMessage());
        }



    }

    public Interface() {
        confirm_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String final_Weight = display_Current_Weight.getText();
                response = JOptionPane.showConfirmDialog(confirm_Button,"Are you sure the Package Weighs: " + final_Weight, "Confirmation", JOptionPane.YES_NO_OPTION);
                if (response == 0) {
                    create_File(final_Weight);
                } else {
                    final_Weight = "";
                }
            }
        });
        cancel_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                response = JOptionPane.showConfirmDialog(cancel_Button, "Are You Sure You Want to Cancel?","Confirmation", JOptionPane.YES_NO_OPTION);
                if (response == 0) {
                    create_File("Cancelled");
                }
            }
        });
    }



    public static void create_File(String Weight) {
        //Use Paths.get in older JVM's
        Path f = Path.of(System.getProperty("user.home"), "Documents", "Weight.txt");
        try {
            Files.writeString(f, Weight, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            String written = Files.readString(f);
            if (written.equals(Weight)) {
                GUI.dispose();
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            GUI.dispose();
            System.exit(0);
        }
    }


    public static void create_Error(String error) {
        //Use Paths.get in older JVM's
        Path f = Path.of(System.getProperty("user.home"), "Documents", "Error.txt");
        try {
            Files.writeString(f, error, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            String written = Files.readString(f);
            if (written.equals(error)) {
                GUI.dispose();
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            GUI.dispose();
            System.exit(0);
        }
    }
}
