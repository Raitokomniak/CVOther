package com.example.raito_waifu.bluetoothcarcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

////////////////////////////////////
// BLUETOOTH CARKIT CONTROLLER
// Raitokomniak 6/10/2016
//
// Connects Android device with Arduino Bluetooth module
// and sends command letters as bytes to Arduino Serial monitor.
//
// This application is also used to handle the carkit's state
// machine and change control from BT to light sensor or
// ultra sound use.
//
// COMMANDS:
// Light sensor = -
// Ultrasound = I
//
// Forward = F
// Backward = B
// Left = L
// Right = R
//
// Backwards + Left = X
// Backwards + Right = Z
//
// Speed range 0 - 9
//
//
///////////////////////////////////

public class MainActivity extends AppCompatActivity {

    RelativeLayout controlPanel;

    // Bluetooth components
    private BluetoothAdapter BA;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothSocket BTSocket;
    private OutputStream BTOutput;
    public BluetoothDevice BTDevice;
    public BluetoothConnection BTConn = new BluetoothConnection();

    // Letter sending
    SendLetter sender = new SendLetter();
    public String letterToSend = "S";
    public int speed = 9;
    Thread speedThread;

    // Direction state machine
    public boolean wentForwards = false;
    public boolean goingForwards = false;
    public boolean goingBackwards = false;
    public boolean goingRight = false;
    public boolean goingLeft = false;

    boolean connected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BA = BluetoothAdapter.getDefaultAdapter();
        connected = false;
        SetUpUI();
    }

    ////////////////////////////
    //Set up listeners for UI

    public void SetUpUI(){
       controlPanel = (RelativeLayout)findViewById(R.id.controlPanel);

        /////////////
        // Connection state machine
        Button freeRoamButton = (Button)findViewById(R.id.freeRoamButton);
        Button lineButton = (Button)findViewById(R.id.lineButton);

        lineButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if(connected) {
                        letterToSend = "-";
                        new Thread(sender).start();
                        controlPanel.setVisibility(View.GONE);
                        Toast.makeText(getApplicationContext(), "Line follow mode on", Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "Connect bluetooth first", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });

        freeRoamButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if(connected) {
                        letterToSend = "I";
                        new Thread(sender).start();
                        controlPanel.setVisibility(View.GONE);
                        Toast.makeText(getApplicationContext(), "Freeroam mode on", Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "Connect bluetooth first", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });



        ///////////
        // Directions
        Button forward  = (Button)findViewById(R.id.forwardButton);
        Button back     = (Button)findViewById(R.id.backButton);
        Button right    = (Button)findViewById(R.id.rightButton);
        Button left     = (Button)findViewById(R.id.leftButton);


        // FORWARD
        forward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    if(!goingBackwards) {
                        letterToSend = "F";
                        goingForwards = true;
                        wentForwards = true;
                        new Thread(sender).start();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    goingForwards = false;
                    CheckResuming();
                    CheckForStop();
                }
                return true;
            }
        });

        // BACK
        back.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    if(!goingForwards) {
                        letterToSend = "B";
                        goingBackwards = true;
                        wentForwards = false;
                        new Thread(sender).start();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    goingBackwards = false;
                    CheckResuming();
                    CheckForStop();
                }
                return true;
            }
        });

        // RIGHT
       right.setOnTouchListener(new View.OnTouchListener() {
           @Override
           public boolean onTouch(View v, MotionEvent event) {
               if(event.getAction() == MotionEvent.ACTION_DOWN) {
                   if(!goingLeft && !goingBackwards) {
                       letterToSend = "R";
                       goingRight = true;
                       new Thread(sender).start();
                   }
                   else if(goingBackwards)
                   {
                       letterToSend = "Z";
                       goingRight = true;
                       new Thread(sender).start();
                   }
               } else if (event.getAction() == MotionEvent.ACTION_UP) {
                   goingRight = false;
                   CheckResuming();
                   CheckForStop();
               }
               return true;
           }
       });

        // LEFT
       left.setOnTouchListener(new View.OnTouchListener() {
           @Override
           public boolean onTouch(View v, MotionEvent event) {
               if(event.getAction() == MotionEvent.ACTION_DOWN) {
                   if(!goingRight && !goingBackwards) {
                       letterToSend = "L";
                       goingLeft = true;
                       new Thread(sender).start();
                   }
                   else if(goingBackwards)
                   {
                       letterToSend = "X";
                       goingLeft = true;
                       new Thread(sender).start();
                   }
               } else if (event.getAction() == MotionEvent.ACTION_UP) {
                   goingLeft = false;
                   CheckResuming();
                   CheckForStop();
               }
               return true;
           }
       });

        /////////////
        // Speed

        SeekBar speedBar = (SeekBar)findViewById(R.id.speedBar);
        final TextView speedText = (TextView)findViewById(R.id.speedText);

        speedText.setText("Speed: " + String.valueOf(speedBar.getProgress()));
        speedBar.setMax(9);
        speedBar.setProgress(9);
        speedText.setText("Speed: " + String.valueOf(speed));

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speed = progress;
                if(speed == 0) speed = 1;
                speedText.setText("Speed: " + String.valueOf(speed));
                letterToSend = String.valueOf(speed);
                speedThread = new Thread(sender);
                speedThread.start();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {


            }
        });
    }

    ////////////////////
    // Resumes movement in previous direction when
    // 1/2 dir buttons is released

    public void CheckResuming(){
            if(goingForwards)
                letterToSend = "F";
            else if(goingBackwards)
                letterToSend = "B";

            else if(goingRight) {
                if(wentForwards) letterToSend = "R";
                else letterToSend = "Z";
            }
            else if(goingLeft) {
                if(wentForwards) letterToSend = "L";
                else letterToSend = "X";
            }

             if(goingForwards || goingBackwards || goingLeft || goingRight)
                 new Thread(sender).start();

    }

    ////////////////////
    // Checks if car is not supposed to move, sends 0 signal to
    // set speed to 0, then after a delay sets it back to speed bar
    // value

    public void CheckForStop(){
        if(!goingForwards && !goingBackwards && !goingLeft && !goingRight) {
            if (letterToSend != "0") {
                letterToSend = "0";
                new Thread(sender).start();
            }
        }
    }

    ////////////////////
    // Sends the designated letter
    // after conversion to bytes

    public class SendLetter implements Runnable {
        @Override
        public void run() {
            System.out.println(letterToSend);
                try {
                    BTConn.write(letterToSend);
                } catch (Exception e) {
                    System.out.println("Can't write");
                }

            if(letterToSend == "0") {  // If 0 is sent as a stopping command, will resume set speed
                try {Thread.sleep(200);}
                catch(Exception e){}

                letterToSend = String.valueOf(speed);
                speedThread = new Thread(sender);
                speedThread.start();
            }

        }
    }


    public void ConnectButtonClicked(View v) {
        if (!BA.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
            Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_LONG).show();
        } else Toast.makeText(getApplicationContext(), "Bluetooth already on", Toast.LENGTH_LONG).show();

        pairedDevices = BA.getBondedDevices();
        ArrayList list = new ArrayList();
        controlPanel.setVisibility(View.VISIBLE);

        for (BluetoothDevice bt : pairedDevices) {
            list.add(bt.getName());
            BTDevice = bt;
            Toast.makeText(getApplicationContext(), "Paired " + bt.getName(), Toast.LENGTH_LONG).show();

            new Thread(BTConn).start();
            break;
        }
    }


    ////////////////////////////
    //BLUETOOTH CONNECTION CLASS
    ////////////////////////////

    public class BluetoothConnection implements Runnable {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        @Override
        public void run() {

            try { // Create Socket
                BTSocket = BTDevice.createRfcommSocketToServiceRecord(uuid);
            } catch (Exception e) {
                System.out.println("Can't create socket");
            } finally {
                System.out.println("Socket created");
            }

            try { // Connect
                BTSocket.connect();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                System.out.println("Connected");
                Toast.makeText(getApplicationContext(), "Connected to " + BTDevice.getName(), Toast.LENGTH_SHORT).show();
                connected = true;
            }

            try { // Fetch Output stream
                BTOutput = BTSocket.getOutputStream();
            } catch (IOException e) {
                Log.e("ConnectToBluetooth", "Error when getting output Stream");
            } finally {
                System.out.println("Outputstream found");
            }

        }

        ////////////////////
        // Write to output stream

        public void write(String str) {
            try {
                BTOutput.write(stringToBytes(str));
            } catch (IOException e) {
                Log.e("Writing to Stream", "Error when writing to btOutputStream");
            }
        }

        ////////////////////
        // Byte conversion

        public byte[] stringToBytes(String str) {
            char[] buffer = str.toCharArray();
            byte[] b = new byte[buffer.length << 1];
            for (int i = 0; i < buffer.length; i++) {
                int bpos = i << 1;
                b[bpos] = (byte) ((buffer[i] & 0xFF00) >> 8);
                b[bpos + 1] = (byte) (buffer[i] & 0x00FF);
            }
            return b;
        }

    }
}

