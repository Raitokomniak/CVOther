package com.example.raito_waifu.bluetoothcarcontroller;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import android.util.Log;
import android.widget.EditText;

public class Main2Activity extends AppCompatActivity {

    /*------------------------------------------------------------------------------
     GLOBAL Variables to be used between a number of classes.
    -----------------------------------------------------------------------------*/
    public BluetoothDevice btShield = null;
    public BluetoothSocket btSocket = null;
    public OutputStream btOutputStream = null;
    public Connect2BtDevice ConBTdevice=new Connect2BtDevice();


    /*------------------------------------------------------------------------------
     The setup() method is used to ConnectButtonClicked to the Bluetooth Device, and setup
     the GUI on the phone.
    -----------------------------------------------------------------------------*/
    void setup(){
        new Thread(ConBTdevice).start(); //Connect to SeeedBTSlave device
    }

    void sendClicked(){
        EditText line = (EditText)findViewById(R.id.lineToSend);
        String letrToSend= line.getText().toString();

        if(ConBTdevice!=null){
            ConBTdevice.write(letrToSend);
        }
    }



    /*==============================================================================
     CLASS: Connect2BtDevice implements Runnable
     - used to ConnectButtonClicked to remote bluetooth device and send values to the Arduino
    ==================================================================================*/
    public class Connect2BtDevice implements Runnable{

        /*------------------------------------------------------------------------------
         Connect2BtDevice CLASS Variables
        -----------------------------------------------------------------------------*/
        BluetoothAdapter btAdapter=null;
        BroadcastReceiver broadcastBtDevices=null;
        private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        public Connect2BtDevice(){
            broadcastBtDevices = new btBroadcastReceiver();
            getBtAdapter();
            enableBtAdapter();
            discoverBtDevices();
        }

        @Override
        public void run() {
            registerReceiver(broadcastBtDevices, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        }

        void getBtAdapter(){
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        void enableBtAdapter(){
            if (!btAdapter.isEnabled()) {
                btAdapter.enable();
            }
        }

        void discoverBtDevices(){
            while(!btAdapter.isEnabled()){
                //Wait until the Bluetooth Adapter is enabled before continuing
            }
            if (!btAdapter.isDiscovering()){
                btAdapter.startDiscovery();
            }
        }

        void connect2Bt(){
            try{
                btSocket = btShield.createRfcommSocketToServiceRecord(uuid);

                try{
                    btSocket.connect();
                    while(btSocket==null){
                        //Do nothing
                    }
                    try {
                        btOutputStream = btSocket.getOutputStream();

                        write("1"); //Green LED (Successful connection)
                    }catch (IOException e) {
                        Log.e("ConnectToBluetooth", "Error when getting output Stream");
                    }
                }catch(IOException e){
                    Log.e("ConnectToBluetooth", "Error with Socket Connection");

                }
            }catch(IOException e){
                Log.e("ConnectToBluetooth", "Error with Socket Creation");

                try{
                    btSocket.close(); //try to close the socket
                }catch(IOException closeException){
                    Log.e("ConnectToBluetooth", "Error Closing socket");
                }return;
            }
        }


        /*------------------------------------------------------------------------------
         write(String str) method
         - Allows you to write a String to the remote Bluetooth Device
        -----------------------------------------------------------------------------*/
        public void write(String str) {
            try {
                btOutputStream.write(stringToBytes(str));
            } catch (IOException e) {
                Log.e("Writing to Stream", "Error when writing to btOutputStream");
            }
        }



        /*------------------------------------------------------------------------------
         byte[] stringToBytes(String str) method
         - Used by the write() method
         - This method is used to convert a String to a byte[] array
         - This code snippet is from Byron:
         http://www.javacodegeeks.com/2010/11/java-best-practices-char-to-byte-and.html
        -----------------------------------------------------------------------------*/
        public byte[] stringToBytes(String str) {
            char[] buffer = str.toCharArray();
            byte[] b = new byte[buffer.length << 1];
            for(int i = 0; i < buffer.length; i++) {
                int bpos = i << 1;
                b[bpos] = (byte) ((buffer[i]&0xFF00)>>8);
                b[bpos + 1] = (byte) (buffer[i]&0x00FF);
            }
            return b;
        }



        /*------------------------------------------------------------------------------
         cancel() method
         - Can be called to close the Bluetooth Socket
        -----------------------------------------------------------------------------*/
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e){
            }
        }

    }


    /*==============================================================================
     CLASS: btBroadcastReceiver extends BroadcastReceiver
     - Broadcasts a notification when the "SeeedBTSlave" is discovered/found.
     - Use this notification as a trigger to ConnectButtonClicked to the remote Bluetooth device
    ==================================================================================*/
    public class btBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
 /* Notification that BluetoothDevice is FOUND */
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
 /* Get the discovered device Name */
                String discoveredDeviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);

 /* If the discovered Bluetooth device Name =SeeedBTSlave then CONNECT */
                if(discoveredDeviceName.equals("HC-06")){
 /* Get a handle on the discovered device */
                    btShield = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
 /* Connect to the discovered device. */
                    ConBTdevice.connect2Bt();
                }
            }
        }
    }
}
