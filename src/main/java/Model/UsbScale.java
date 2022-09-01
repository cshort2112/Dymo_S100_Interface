package Model;

import org.usb4java.*;

import javax.usb.*;
import javax.usb.event.UsbPipeDataEvent;
import javax.usb.event.UsbPipeErrorEvent;
import javax.usb.event.UsbPipeListener;
import java.util.List;

public class UsbScale implements UsbPipeListener, AutoCloseable {

    private boolean isOpened = false;
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
        UsbServices services;
        UsbHub rootHub = null;
        try {
            services = UsbHostManager.getUsbServices();
            rootHub = services.getRootUsbHub();
        } catch (UsbException e) {
            Interface.create_Error("Error! " + e.getMessage());
        }
        // Dymo S100 Scale:
        assert rootHub != null;
        UsbDevice device = findDevice(rootHub, (short) 0x0922, (short) 0x8009);
        // Dymo M25 Scale:
        //if (device == null) {
        //    device = findDevice(rootHub, (short) 0x0922, (short) 0x8004);
        //}
        if (device == null) {
            return null;
        }
        return new UsbScale(device);
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
            isOpened = true;
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
        }catch (UsbException e) {
            Interface.create_Error("Error! "+ e.getMessage());
        }


    }

    public void close() {
        if (!isOpened) return;
        try {
            pipe.close();
            iface.release();
            LibUsb.exit(context);
        } catch (UsbException e) {
            Interface.create_Error("Error! "+ e.getMessage());
        }
    }

    public double syncSubmit() {
        try {
            pipe.syncSubmit(data);
        } catch (UsbException e) {
            Interface.create_Error("Error! "+ e.getMessage());
        }
        return finalWeight;
    }


    @Override
    public void dataEventOccurred(UsbPipeDataEvent upde) {
        //System.out.println(data[1] + ", " + data[2] + ", " + data[3] + ", " + data[4] + ", " + data[5]);
        //if data[1] == 4 value is stable, if data[1] == 3 value is unstable, if data[1] == 5 value is negative, if data[1] == 2 value is 0
        if (data[2] == 12) { //This means it is in imperial Mode
            if (data[1] == 4 || data[1]== 3) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = scaleWeight(weight, scalingFactor); //final weight, applies to both metric and imperial
            } else if (data[1] == 5) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = scaleWeight(weight, scalingFactor) * (-1); //final weight, applies to both metric and imperial
            } else if (data[1] == 2) {
                finalWeight = 0;
            }
        } else { //This would mean it is in metric
            if (data[1] == 4 || data[1]== 3) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = (scaleWeight(weight, scalingFactor) * 2.20462); //final weight, applies to both metric and imperial
            } else if (data[1] == 5) {
                int weight = (data[4] & 0xFF) + (data[5] << 8);
                int scalingFactor = data[3];
                finalWeight = (scaleWeight(weight, scalingFactor) * 2.20462) * (-1); //final weight, applies to both metric and imperial
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
        Interface.create_Error(usbPipeErrorEvent.toString());
    }
}
