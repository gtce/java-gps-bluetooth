/* 
 * TODO: Optimisation
 * TODO: More Commenting
 * TODO: Options Screen
 * TODO: Messages for certain parts
 * TODO: Journey Grouping?
 * TODO: Fix Crashing Bug/Improve Stability
 */

/*
 * Main.java
 * Created on 04 April 2007, 09:21
 *
 * @author Teario
 */
package GPS;

import java.io.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.bluetooth.*;
import java.util.Vector;
import javax.microedition.rms.*;

public class Main extends MIDlet implements CommandListener, DiscoveryListener, Runnable
{
    // Displayable Items
    private Display m_Display = null;
    private Form m_Main;
    private List m_DeviceList;
    private Command m_Exit = new Command("Exit", Command.EXIT, 0);
    private StringItem m_LatDisp;
    private StringItem m_LngDisp;
    private StringItem m_SpdDisp;
    private StringItem m_AltDisp;
    private StringItem m_SatDisp;
    private StringItem m_SncDisp;
    private StringItem m_UpdateStatus;
    private StringItem m_DOPDisp;
    private StringItem m_UdTDisp;

    // Enum Constants
    private final int STATE_READY = 0;
    private final int STATE_DEVICE_SEARCH = 1;
    private final int STATE_SERVICE_SEARCH = 2;
    private final int STATE_TRACKING = 3;

    // Variables
    private boolean m_IsSearching;
    private int m_CurrentState = STATE_READY;
    private int m_SyncTime;
    private Vector m_FoundDevices;
    private String m_ConnectUrl;
    private String m_Alt;
    private String m_Sats;
    private double m_SyncLat = 0.0;
    private double m_SyncLon = 0.0;
    private double m_Speed = 0.0;
    private double m_DOP = 0.0;

    // Objects
    private UUID[] m_IdSet = { new UUID(0x1101) };
    private Thread m_DiscoveryThread = null;
    private LocalDevice m_LocalDevice = null;
    private DiscoveryAgent m_DiscoveryAgent = null;
    private RemoteDevice m_GPSDevice = null;
    private ServiceRecord[] m_ServiceRecords = null;
    private StreamConnection m_SConn = null;
    private InputStream m_IStrm = null;
    private RecordStore m_RecSt;

    public Main()
    {
        // Initialise the application
        m_DeviceList = new List("Searching for Devices", List.IMPLICIT);
        m_DeviceList.addCommand(m_Exit);
        m_DeviceList.setCommandListener(this);
    }

    public void run()
    {
        switch(m_CurrentState)
        {
            case STATE_DEVICE_SEARCH:
                m_IsSearching = true;

                try
                {
                    m_DiscoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
                }
                catch (BluetoothStateException ex)
                {
                    m_IsSearching = false;
                    makeError(ex.toString());
                }

                if(m_IsSearching)
                {
                    processSearch();
                }
                
                // Make sure we have found a device
                if(m_FoundDevices.isEmpty())
                {
                    makeError("No devices in range");
                }
            break;

            case STATE_SERVICE_SEARCH:
                m_IsSearching = true;
                m_ServiceRecords = null;
                m_DeviceList.setTitle("Connecting...");

                try
                {
                    m_DiscoveryAgent.searchServices(null,m_IdSet,m_GPSDevice,this);
                }
                catch (BluetoothStateException ex)
                {
                    m_IsSearching = false;
                    makeError(ex.toString());
                }

                if(m_IsSearching)
                {
                    processSearch();

                    if(null == m_ServiceRecords)
                    {
                        makeError("No Services");
                    }
                    else
                    {
                        // Connect to the device
                        m_ConnectUrl = m_ServiceRecords[0].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                        Runnable tracker = new Tracker(m_ConnectUrl, m_Display);
                        Thread trackThread = new Thread(tracker);
                        trackThread.run();
                    }
                }
            break;
        }

        m_CurrentState = STATE_READY;
    }

    protected void startApp() throws MIDletStateChangeException
    {
        if(null == m_Display)
        {
            m_Display = Display.getDisplay(this);
        }

        m_Display.setCurrent(m_DeviceList);

        try
        {
            m_LocalDevice = LocalDevice.getLocalDevice();
            m_DiscoveryAgent = m_LocalDevice.getDiscoveryAgent();
        }
        catch(BluetoothStateException ex)
        {
            makeError(ex.toString());
        }

        if(null != m_LocalDevice)
        {
            discoverDevices();
        }
    }

    protected void pauseApp()
    {
        // Application is paused on certain events, eg phone calls
        makeError("Application requested to be paused");
    }

    protected void destroyApp(boolean b) throws MIDletStateChangeException
    {
        //cleanUp();
    }

    public void commandAction(Command command, Displayable displayable)
    {
        if(command == m_Exit)
        {
            if(m_DeviceList == displayable)
            {
                cleanUp();
            }
            else
            {
                
            }
            notifyDestroyed();
        }
        else if(command == List.SELECT_COMMAND)
        {
            if( STATE_READY == m_CurrentState)
            {
                m_GPSDevice = (RemoteDevice)m_FoundDevices.elementAt(m_DeviceList.getSelectedIndex());
                discoverServices();
            }
        }
    }

    public void deviceDiscovered(RemoteDevice RemoteDev, DeviceClass DevClass)
    {
        try
        {
            m_FoundDevices.addElement(RemoteDev);
            m_DeviceList.append(RemoteDev.getFriendlyName(false)+GetClassInfo(DevClass), null);
        }
        catch(IOException ex)
        {
            makeError(ex.toString());
        }
    }

    public void servicesDiscovered(int i, ServiceRecord[] serviceRecord)
    {
        //TODO: Find out what trans id does (int i)
        m_ServiceRecords = serviceRecord;
    }

    public void serviceSearchCompleted(int i, int i0)
    {
        //TODO: Find out what inqury response does (int i)
        m_IsSearching = false;
        synchronized(this)
        {
            notify();
        }
    }

    public void inquiryCompleted(int i)
    {
        //TODO: find out about inquiry responses (int i)
        m_IsSearching = false;
        m_DeviceList.setTitle("Select Device");
        synchronized(this)
        {
            notify();
        }
    }

    private void discoverDevices()
    {
        m_CurrentState = STATE_DEVICE_SEARCH;
        m_FoundDevices = new Vector(10);
        m_DiscoveryThread = new Thread(this);
        m_DiscoveryThread.start();
    }

    private void discoverServices()
    {
        m_CurrentState = STATE_SERVICE_SEARCH;
        m_DiscoveryThread = new Thread(this);
        m_DiscoveryThread.start();
    }

    private void processSearch()
    {
        while(m_IsSearching)
        {
            // do nothing
            // wait();?
        }
    }

    public String GetClassInfo(DeviceClass DevClass)
    {
        // This function is a little redundant now. Originally
        // I thought I could use it to pick out devices only supporting
        // a positioning service, but after testing, my GPS device gives
        // out a Major device class not covered in the document, and a minor
        // and service class of 0. Still, it is good to show the major class
        // to the user to help them identify the items, so the function will
        // stay, in a basic form.

        String info = "";
        int MinClass = DevClass.getMinorDeviceClass();
        int MajClass = DevClass.getMajorDeviceClass();
        int SrvClass = DevClass.getServiceClasses();
        
        // Sevice Classes:
        // 8192    (0x200)   - Limited Discoverable Mode
        // 16384   (0x400)   - Reserved
        // 32768   (0x800)   - Reserved
        // 65536   (0x1000)  - Positioning
        // 131072  (0x2000)  - Networking
        // 262144  (0x4000)  - Rendering
        // 524288  (0x8000)  - Capturing
        // 1048576 (0x10000) - Object Transfer
        // 2097152 (0x20000) - Audio
        // 4194304 (0x40000) - Telephony
        // 8388608 (0x80000) - Information
        
        if(MajClass == 0x100)
        {
            info = " (Computer)";
            // Minor Classes:
            // 4  (0x004) - Desktop
            // 8  (0x008) - Server
            // 12 (0x00C) - Laptop
            // 20 (0x014) - PDA
        }
        else if(MajClass == 0x200)
        {
            info = " (Phone)";
            // Minor Classes:
            // 4  (0x004) - "cellular"
            // 8  (0x008) - House/Cordless
            // 12 (0x00C) - SmartPhone
            // 16 (0x010) - Wired Modem/Voice Gateway
            // 20 (0x014) - Common ISDN Access
        }
        else if(MajClass == 0x300)
        {
            info = " (Lan/AP)";
            // Minor Classes:
            // 0  (0x000) - Fully Available
            // 32 (0x020) - 0 to 17% utilised
            // 64 (0x040) - 17 to 32% utilised
            // etc...
        }
        else if(MajClass == 0x400)
        {
            info = " (Audio/Video)";
            // Minor Classes:
            // 4  (0x004) - Wearable Headset Device
            // 8  (0x008) - Hands Free Device
            // 12 (0x00C) - Reserved
            // 16 (0x010) - Microphone
            // 20 (0x014) - Loudspeaker
            // 24 (0x018) - Headphones
            // 28 (0x01C) - Portable Audio
            // 32 (0x020) - Car Audio
            // 36 (0x024) - Set Top Box
            // 40 (0x028) - HiFi Audio Device
            // 44 (0x02C) - VCR
            // 48 (0x030) - Video Camera
            // 52 (0x034) - Camcorder
            // 56 (0x038) - Video Monitor
            // 60 (0x03C) - Video Display and Loudspeaker
            // 64 (0x040) - Video Conferencing
            // 68 (0x044) - Reserved
            // 72 (0x048) - Gaming/Toy
        }
        else if(MajClass == 0x500)
        {
            info = " (Peripheral)";
            // Minor Classes:
            // 0   (0x000) - Not Keyboard or Pointing Device
            // 64  (0x040) - Keyboard
            // 128 (0x080) - Pointing Device
            // 192 (0x0C0) - Combo Keyboard and Pointing Device

            // Above values can be Bit-Wise OR'ed with the following:
            // 0  (0x000) - Uncategorised Device
            // 4  (0x004) - Joystick
            // 8  (0x008) - Gamepad
            // 12 (0x00C) - Remote Control
            // 16 (0x010) - Sensing Device
            // 20 (0x014) - Digitiser Tablet
            // 24 (0x018) - Card Reader
        }
        else if(MajClass == 0x600)
        {
            info = " (Imaging)";
            // Minor Classes:
            // Devices can be more than one of these, if so
            // the values are bit-wise OR'ed
            // 32  (0x020) - Camera
            // 64  (0x040) - Scanner
            // 128 (0x080) - Printer
        }
        else if(MajClass == 0x680)
        {
            info = " (Wearable)";
            // Minor Classes:
            // 4  (0x004) - Wrist Watch
            // 8  (0x008) - Pager
            // 12 (0x00C) - Jacket
            // 16 (0x010) - Helmet
            // 20 (0x014) - Glasses
        }
        else if(MajClass == 0x800)
        {
            info = " (Toy)";
            // Minor Classes:
            // 4  (0x004) - Robot
            // 8  (0x008) - Vehicle
            // 12 (0x00C) - Doll or Action Figure
            // 16 (0x010) - Controller
            // 20 (0x014) - Game
        }

        return info;
    }

    private void makeError(String ErrorMsg)
    {
        Alert alert = new Alert("Error",ErrorMsg, null, AlertType.ERROR);
        m_Display.setCurrent(alert,m_Display.getCurrent());
    }

    private void cleanUp()
    {
        
    }

    // http://discussion.forum.nokia.com/forum/archive/index.php/t-2822.html
    public String[] split(String str,char x)
    {
        Vector v=new Vector();
        String str1=new String();

        for(int i=0;i<str.length();i++)
        {
            if(str.charAt(i)==x)
            {
                v.addElement(str1);
                str1=new String();
            }
            else
            {
                str1+=str.charAt(i);
            }
        }
        
        v.addElement(str1);
        String array[];
        array=new String[v.size()];
        
        for(int i=0;i<array.length;i++)
        {
            array[i]=new String((String)v.elementAt(i));
        }

        return array;
    }
}