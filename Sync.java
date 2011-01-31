package GPS;
import java.io.*;
import java.util.Vector;
import javax.microedition.io.*;
import javax.microedition.lcdui.StringItem;
import javax.microedition.rms.*;

public class Sync implements Runnable
{
    private String m_Lat;
    private String m_Lon;
    private String m_Spd;
    private String m_Alt;
    private String m_Sat;
    private RecordStore m_RecSt;
    private StringItem m_SncDisp;

    public Sync(String lat, String lon, String spd, String alt, String sat, StringItem sncdisp)
    {
        m_Lat = lat;
        m_Lon = lon;
        m_Spd = spd;
        m_Alt = alt;
        m_Sat = sat;
        m_SncDisp = sncdisp;

        try
        {
            m_RecSt.openRecordStore("GPS",true);
        }
        catch (RecordStoreException ex)
        {
        }
    }

    public void run()
    {
//        if(updateRecorded())
//        {
            syncPosition(m_Lat, m_Lon, m_Spd, m_Alt, m_Sat);
//        }
    }
    
    private boolean syncPosition(String lat, String lon, String spd, String alt, String sat)
    {
        HttpConnection NetConn = null;

        try
        {
            /* Traffic:
             * 490 bytes out
             * 425 bytes in
            */
            NetConn = (HttpConnection)Connector.open("http://yourserver.com/updatescript.php?lat="+lat+"&lon="+lon+"&speed="+spd+"&alt="+alt+"&sats="+sat);
            m_SncDisp.setText(NetConn.getResponseMessage());
        }
        catch(IOException ex)
        {
        }
        finally
        {
            try
            {
                if( NetConn != null )
                {
                    NetConn.close();
                    return true;
                }
                return false;
            }
            catch(IOException ex)
            {
                return false;
            }
        }
    }
    
    private boolean updateRecorded()
    {
        try
        {
            if(m_RecSt.getNumRecords() > 0)
            {
                // update from records
                RecordEnumeration RecEnum = m_RecSt.enumerateRecords(null,null,false);
                String[] Data;
                while(RecEnum.hasNextElement())
                {
                    try
                    {
                        Data = split(RecEnum.nextRecord().toString(), ' ');
                        if(!syncPosition(Data[0],Data[1],Data[2],Data[3],Data[4]))
                        {
                            return false;
                        }
                        else
                        {
                            // remove record from db
                            m_RecSt.deleteRecord(RecEnum.nextRecordId());
                        }
                    }
                    catch (InvalidRecordIDException ex)
                    {
                        // There was an error updating the records. Return
                        // true (at end of function) so that the app will
                        // still try to sync the latest position, as we
                        // dont know the state of the server, but we assume
                        // it to be working
                    }
                    catch (RecordStoreNotOpenException ex)
                    {
                    }
                    catch (RecordStoreException ex)
                    {
                    }
                }
            }
        }
        catch (RecordStoreNotOpenException ex)
        {
        }
        return true;
    }

    private void saveLocation(String lat, String lon, String spd, String alt, String sat)
    {
        String locationData = lat+' '+lon+' '+spd+' '+alt+' '+sat;

        try
        {
            m_RecSt.addRecord(locationData.getBytes(), 0, locationData.length());
        }
        catch (RecordStoreException ex)
        {
        }
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