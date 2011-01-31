// This class should contain all the infomation for connecting to the device and tracking the position
// it also needs to be able to create an instance of sync class to sync with the server
package GPS;

import java.io.*;
import java.util.Vector;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;

public class Tracker implements Runnable
{
    private String m_ConnectUrl;
    private Display m_Display;

    private double m_Speed;
    private double m_DOP;
    private String m_Alt;
    private String m_Sats;
    private double m_SyncLat;
    private double m_SyncLon;

    public Tracker(String connectUrl, Display display)
    {
        m_ConnectUrl = connectUrl;
        m_Display = display;
    }
    
    public void run()
    {
        if(null != m_ConnectUrl)
        {
            try
            {
                StreamConnection m_SConn = (StreamConnection)Connector.open(m_ConnectUrl);
                InputStream m_IStrm = m_SConn.openInputStream();

                int m_SyncTime = 5;
                int i = 0;
                char c;
                String s = "";
                String[] Data;

                // Create the GUI
                Form m_Main = new Form("GPS Tracker");

                StringItem m_LatDisp = new StringItem("Lat: ","");
                StringItem m_LngDisp = new StringItem("Lon: ","");
                StringItem m_SpdDisp = new StringItem("Spd: ","");
                StringItem m_AltDisp = new StringItem("Alt: ","");
                StringItem m_SatDisp = new StringItem("Sat: ","");
                StringItem m_SncDisp = new StringItem("Last Update: ","N/A");
                StringItem m_UdTDisp = new StringItem("","Sync in "+m_SyncTime+" seconds");
                StringItem m_DOPDisp = new StringItem("DOP: ","");
                
                m_LatDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_LngDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_SpdDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_AltDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_SatDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_SncDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_UdTDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_DOPDisp.setLayout(StringItem.LAYOUT_NEWLINE_AFTER);
                m_UdTDisp.setLayout(StringItem.LAYOUT_CENTER);

                m_Main.append(m_UdTDisp);
                m_Main.append(m_LatDisp);
                m_Main.append(m_LngDisp);
                m_Main.append(m_SpdDisp);
                m_Main.append(m_SatDisp);
                m_Main.append(m_AltDisp);
                m_Main.append(m_SncDisp);
                m_Main.append(m_DOPDisp);

                m_Display.setCurrent(m_Main);

                do
                {
                    i = m_IStrm.read();
                    c = (char)i;
                    s = s + c;

                    if(i == 36)
                    {
                        if(s.length() > 5)
                        {
                            Data = split(s,',');

                            if(Data[0].compareTo("GPRMC") == 0)
                            {
                                /* Position Information Received
                                 * Format:
                                 * $GPRMC,time,Mode,lat,N/S,lon,W/E,speed,bearing,date,magnetic variation,E(/W?),checksum
                                */
                                m_SyncTime--;

                                if(Data[6].compareTo("W") == 0)
                                {
                                    Data[5] = '-'+Data[5];
                                }

                                // Speed is received in knots, convert it to mph and
                                // round it to 2 decimal places
                                m_Speed = Double.parseDouble(Data[7]);
                                m_Speed *= 1.15077945;
                                m_Speed = round(m_Speed,2);

                                m_LatDisp.setText(Data[3]+" "+Data[4]);
                                m_LngDisp.setText(Data[5]+" "+Data[6]);
                                m_SpdDisp.setText(m_Speed+" Mph");

                                if(m_SyncTime <= 0)
                                {
                                    //m_SyncTime = 300;
                                    m_SyncTime = 60;
                                    m_UdTDisp.setText("Synchronising");

                                    if(m_DOP < 1.75)
                                    {
                                        // Check if we have moved a distance worth updating (more than 0.03 degrees)
                                        if(checkMovement(Double.parseDouble(Data[3]),Double.parseDouble(Data[5])))
                                        {
                                            Runnable sync = new Sync(Data[3],Data[5],Data[7],m_Alt,m_Sats,m_SncDisp);
                                            Thread syncThread = new Thread(sync);
                                            syncThread.run();
                                        }
                                    }
                                }
                                else
                                {
                                    m_UdTDisp.setText("Sync in "+m_SyncTime+" seconds");
                                }
                            }
                            else if(Data[0].compareTo("GPGGA") == 0)
                            {
                                /* Altitude Information Received
                                 * Format:
                                 * $GPGGA,time,lat,lon,fix quality,satellites in view,horz dop,altitude,M,geoid height above wgs84 ellipsoid,M,time since dgps update,dgps ref station id,checksum
                                 * Fix Quality:
                                 * 0 = Invalid
                                 * 1 = GPS
                                 * 2 = DGPS
                                */
                                m_Alt = Data[9];
                                m_AltDisp.setText(m_Alt+" "+Data[10]);
                            }
                            else if(Data[0].compareTo("GPGSV") == 0)
                            {
                                /* Visisble Satellites Information Received
                                 * Format:
                                 * $GPGSV,GPGSV Messages this cycle,message num,satellites in view,satellite PRN,elevation (max 90 degrees),azimuth (degrees from true north) 000-359,same as last 4 but for next sat,same but for 3rd sat,same but for 4th sat,checksum
                                */
                                m_Sats = Data[3];
                                m_SatDisp.setText(m_Sats);
                            }
                            else if(Data[0].compareTo("GPGSA") == 0)
                            {
                                /* Satellite and Accuracy Information Received
                                 * Format:
                                 * $GPGSA,Mode1,Mode2,(3-16)PRNs of satellite vehicles used in fix,Position DOP,Hoz DOP,Vert DOP,checksum
                                 * Mode1:
                                 * M = Manual - operate in 2D/3D
                                 * A = Automatic 2D/3D
                                 * Mode2:
                                 * 1 = Fix not available
                                 * 2 = 2D
                                 * 3 = 3D
                                */
                                if(Data[16].length() > 0)
                                {
                                    m_DOPDisp.setText(Data[16]);
                                    m_DOP = Double.parseDouble(Data[16]);
                                }
                                //TODO: check hdop, if it is too high, dont update with server or send it to server for notification in display
                                // Average DOP is under 1.75
                            }
                            else
                            {
                                // The GPS Firmware will send some messages occasionally,
                                // non sirfstar chips can send out a message when changing
                                // the tracking mode to DGPRS. Sirfstar chips havent been
                                // tested
                            }
                        }

                        s = "";
                        /*try
                        {
                            wait(1);
                        }
                        catch(InterruptedException ex)
                        {
                        }*/
                    }
                }
                while(i != -1);
                /* TODO: We have stopped reading data, possibly the
                 * bluetooth connection was lost, or the device
                 * was turned off. Rescan for it, if its not
                 * found again then either alert user and exit,
                 * or do another device search and prompt them to
                 * select a new device
                */
            }
            catch (IOException ex)
            {
            }
        }
    }
    
    private boolean checkMovement(double lat, double lon)
    {
        if(m_SyncLat - lat > 0.03 || m_SyncLat - lat < -0.03)
        {
            if(m_SyncLon - lon > 0.03 || m_SyncLon - lon < -0.03)
            {
                m_SyncLat = lat;
                m_SyncLon = lon;
                return true;
            }
        }
        m_SyncLat = lat;
        m_SyncLon = lon;
        return false;
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
    
    //http://calum.org/posts/j2me-functions-that-arent-included-but-should-be
    public static double round(double arg, int places)
    {
        double tmp = (double)arg*(pow(10,places));
        int tmp1 = (int)Math.floor(tmp+0.5);
        double tmp2 = (double)tmp1/(pow(10,places));
        return tmp2;
    }

    //http://calum.org/posts/j2me-functions-that-arent-included-but-should-be
    public static int pow(int arg, int times)
    {
        int ret = 1;
        for(int i = 1;i <= times;i++)
        {
            ret = ret*arg;
        }
        return ret;
    }
}
