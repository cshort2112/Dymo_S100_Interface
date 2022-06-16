package Model;

import org.usb4java.*;

import javax.usb.*;
import javax.usb.event.UsbPipeDataEvent;
import javax.usb.event.UsbPipeErrorEvent;
import javax.usb.event.UsbPipeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsbScale implements UsbPipeListener {

    private final UsbDevice device;
    private UsbInterface iface;
    private UsbPipe pipe;
    private final byte[] data = new byte[6];
    private double finalWeight;
    private Context context;

    private UsbScale(UsbDevice device) {
        this.device = device;
    }


    public static UsbScale findScale() {
        try {
            UsbServices services = UsbHostManager.getUsbServices();
            UsbHub rootHub = services.getRootUsbHub();
            // Dymo M10 Scale:
            UsbDevice device = findDevice(rootHub, (short) 0x0922, (short) 0x8003);
            // Dymo M25 Scale:
            if (device == null) {
                device = findDevice(rootHub, (short) 0x0922, (short) 0x8004);
            }
            // Dymo S100 Scale:
            if (device == null) {
                device = findDevice(rootHub, (short) 0x0922, (short) 0x8009);
            }
            if (device == null) {
                return null;
            }
            return new UsbScale(device);
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
            return null;
        }
    }

    private static UsbDevice findDevice(UsbHub hub, short vendorId, short productId) {
        for (UsbDevice device : (List<UsbDevice>) hub.getAttachedUsbDevices()) {
            UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
            if (desc.idVendor() == vendorId && desc.idProduct() == productId) {
                return device;
            }
            if (device.isUsbHub()) {
                device = findDevice((UsbHub) device, vendorId, productId);
                if (device != null) {
                    return device;
                }
            }
        }
        return null;
    }

    public Device findDevice(short vendorId, short productId)
    {
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to initialize libusb.", result);
        // Read the USB device list
        DeviceList list = new DeviceList();
        result = LibUsb.getDeviceList(context, list);
        if (result < 0) throw new LibUsbException("Unable to get device list", result);
        try
        {
            // Iterate over all devices and scan for the right one
            for (Device device: list)
            {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to read device descriptor", result);
                if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId) {
                    return device;
                }
            }
        }
        finally
        {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // Device not found
        return null;
    }

    public void open()  {
        try {
            context = new Context();
            UsbConfiguration configuration = device.getActiveUsbConfiguration();
            iface = configuration.getUsbInterface((byte) 0);
            // this allows us to steal the lock from the kernel
            DeviceHandle handle = new DeviceHandle();
            int result = LibUsb.open( findDevice((short) 0x0922, (short) 0x8009), handle);
            if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to open USB device", result);
            result = LibUsb.setConfiguration(handle, 0);
            if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to set Configuration", result);
            iface.claim(usbInterface -> true);
            final List<UsbEndpoint> endpoints = iface.getUsbEndpoints();
            pipe = endpoints.get(0).getUsbPipe(); // there is only 1 endpoint
            pipe.addUsbPipeListener(this);
            pipe.open();
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

    public double syncSubmit() {
        try {
            pipe.syncSubmit(data);
            return finalWeight;
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
            return finalWeight;
        }

    }


    public void close() throws UsbException {
        try {
            pipe.close();
            iface.release();
            LibUsb.exit(context);
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

    @Override
    public void dataEventOccurred(UsbPipeDataEvent upde) {
        if (data[2] == 12) { //This means it is in imperial Mode
            if (data[1] == 4) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = scaleWeight(weight, scalingFactor); //final weight, applies to both metric and imperial
            }else if (data[1] == 5) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = scaleWeight(weight, scalingFactor)*(-1); //final weight, applies to both metric and imperial
            } else if (data[1] == 2) {
                finalWeight = 0;
            }
        } else { //This would mean it is in metric
            if (data[1] == 4) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = (scaleWeight(weight, scalingFactor)*2.20462); //final weight, applies to both metric and imperial
            } else if (data[1] == 5) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = (scaleWeight(weight, scalingFactor)*2.20462)*(-1); //final weight, applies to both metric and imperial
            } else if (data[1] == 2) {
                finalWeight = 0;
            }

        }

    }

    private double scaleWeight(int weight, int scalingFactor) {
        return weight * Math.pow(10, scalingFactor);
    }


    @Override
    public void errorEventOccurred(UsbPipeErrorEvent usbPipeErrorEvent) {
        Logger.getLogger(UsbScale.class.getName()).log(Level.SEVERE, "Scale Error", usbPipeErrorEvent);
        try {
            String home = System.getProperty("user.home");
            File f = new File(home + File.separator + "Desktop" + File.separator + "Java.txt");

            BufferedWriter out = new BufferedWriter(new FileWriter(f));
            try {
                out.write("Error! " + usbPipeErrorEvent);
            } finally {
                out.close();
                System.exit(0);
            }
        } catch (IOException exception) {
            System.out.println("An error occurred.");
            exception.printStackTrace();
        }
    }
}
