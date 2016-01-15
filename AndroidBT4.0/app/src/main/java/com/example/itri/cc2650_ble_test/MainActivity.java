package com.example.itri.cc2650_ble_test;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MainActivity extends Activity implements View.OnClickListener
{
    private UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //定義手機的UUID

    private String TAG = "";
    private int ser;

    private String deviceName = "CC2650 SensorTag";
    private BluetoothAdapter BtAdapter = null;
    private BluetoothDevice BtDevice = null;
    private BluetoothGatt BtGatt = null;

    private boolean scanning = false;
    private Handler handler;

    private static final long SCAN_PERIOD = 15000;// Stops scanning after 15 seconds.
    private static final int REQUEST_ENABLE_BT = 1;

    private ArrayList<UUID> serviceUuid = new ArrayList<UUID>();
    private ArrayList<UUID> configUuid = new ArrayList<UUID>();
    private ArrayList<UUID> dataUuid = new ArrayList<UUID>();

    //GUI

    private TextView tv_temp,tv_hum,tv_mon,tv_cs;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ((Button) findViewById(R.id.buttonScan)).setOnClickListener(this);
        ((Button) findViewById(R.id.buttonClear)).setOnClickListener(this);
        tv_temp = (TextView)findViewById(R.id.tv_temp);
        tv_hum = (TextView)findViewById(R.id.tv_hum);
        tv_mon = (TextView)findViewById(R.id.tv_motion);
        tv_cs = (TextView)findViewById(R.id.tv_console);

        handler = new Handler();

        //初始化Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BtAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (BtAdapter == null)
        {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (BtAdapter == null || !BtAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        BleDeviceScan();

    }

    @Override
    public void onDestroy()
    {
        if (BtGatt != null) {
            BtGatt.disconnect();
            BtGatt.close();
        }
        super.onDestroy();
    }

    /*== GATT client callbacks ==*/
    public BluetoothGattCallback GattCallback = new BluetoothGattCallback()
    {
        int step = 0;

        //當連接狀態改變時
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            if (newState == BluetoothProfile.STATE_CONNECTED)//連接成功
            {
                Log.d(TAG, "connected success.");
                //成功連接後，開始搜尋設備的服務
                ser = 0;
                Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)//連接失敗
            {
                Log.d(TAG, "connect failed.");
                gatt.disconnect();
                gatt.close();
            }
        }

        //發現新的服務
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            //BluetoothDevice device = gatt.getDevice();
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                step = 0;
                SetupSensor(gatt);
            }
            else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //Characteristic的狀態為可寫時的callback
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                step = 1;
            }
            else
            {
                System.out.println("CharacteristicWrite error.");
                System.out.println(ser);
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            SetupSensor(gatt);
        }

        //Descriptor的狀態為可寫時的callback
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
        {
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                ser++;
                step = 0;
            }
            else
            {
                System.out.println("DescriptorWrite error.");
                System.out.println(ser);
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            if(ser < serviceUuid.size())
            {
                SetupSensor(gatt);
            }
        }

        int Colornum = 0;

        //當characteristic發生改變時
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            String outputString;
            //Log.w(TAG,characteristic.getUuid().toString());

            //溫度計
            if (GATT.UUID_IRT_DATA.equals(characteristic.getUuid()))
            {
                double ambient = Senser.extractAmbientTemperature(characteristic.getValue());
                double target = Senser.extractTargetTemperature(characteristic.getValue(), ambient);
                //target = target * 1.8 + 32; //轉換華氏
                outputString = "環境溫度： " + String.format("%.2f", ambient) + "'C\n";
                outputString += "溫度： " + String.format("%.2f", target) + "'C";
                output(outputString,tv_temp,Colornum);
            }
            if(GATT.UUID_MOV_DATA.equals(characteristic.getUuid()))//9軸動態感測儀
            {
                double acc[] = Senser.extractACC(characteristic.getValue());
                double gyro[] = Senser.extractGYRO(characteristic.getValue());
                double cmp[] = Senser.extractCMP(characteristic.getValue());

                outputString = "加速度： X:" + String.format("%.3f", acc[0]) + "G";
                outputString += " Y:"+ String.format("%.3f", acc[1]) + "G";
                outputString += " Z:"+ String.format("%.3f", acc[2]) + "G\n";

                outputString += "陀螺儀： X:" + String.format("%.3f", gyro[0]) + "'/s";
                outputString += " Y:"+ String.format("%.3f", gyro[1]) + "'/s";
                outputString += " Z:"+ String.format("%.3f", gyro[2]) + "'/s\n";

                outputString += "指北針： X:" + String.format("%.3f", cmp[0]) + "uT";
                outputString += " Y:"+ String.format("%.3f", cmp[1]) + "uT";
                outputString += " Z:"+ String.format("%.3f", cmp[2]) + "uT";

                output(outputString,tv_mon,Colornum);
            }
            if(GATT.UUID_HUM_DATA.equals(characteristic.getUuid()))//濕度
            {
                double hum = Senser.extractHUM(characteristic.getValue());
                outputString = "濕度： " + String.format("%.2f", hum) + "%rH";
                output(outputString,tv_hum,Colornum);
            }
            Colornum++;
            Colornum = Colornum%ColorTable.length;
        }

        /*== Setup the Senser ==*/
        private void SetupSensor(BluetoothGatt gatt)
        {

            BluetoothGattService Service = gatt.getService(serviceUuid.get(ser));

            switch (step)
            {
                case 0:
                    //Enable Sensor

                    BluetoothGattCharacteristic Char = Service.getCharacteristic(configUuid.get(ser));
                    if(GATT.UUID_MOV_SERV.toString().equals(serviceUuid.get(ser).toString()))
                        Char.setValue(new byte[]{0x7F, 0x02});
                    else
                        Char.setValue(new byte[]{1});
                    gatt.writeCharacteristic(Char);

                    break;

                case 1:
                    //Setup Sensor

                    BluetoothGattCharacteristic DataCharacteristic = Service.getCharacteristic(dataUuid.get(ser));
                    gatt.setCharacteristicNotification(DataCharacteristic, true); //Enabled locally

                    BluetoothGattDescriptor config = DataCharacteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                    config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(config); //Enabled remotely

                    break;
            }
        }
    };

    //回報由手機設備掃描過程中發現的BLE設備
    public BluetoothAdapter.LeScanCallback DeviceLeScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
        {
            if (deviceName.equals(device.getName()))
            {
                if (BtDevice == null)
                {
                    BtDevice = device;
                    BtGatt = BtDevice.connectGatt(getApplicationContext(), false, GattCallback); // 連接GATT
                }
                else
                {
                    if (BtDevice.getAddress().equals(device.getAddress()))
                    {
                        return;
                    }
                }
                output("*<small> " + device.getName() + ":" + device.getAddress() + ", rssi:" + rssi + "</small>",tv_cs);
            }
        }
    };

    /*== scan the BLE devices ==*/
    private void BleDeviceScan()
    {
        if (scanning == false)
        {
            handler.postDelayed(new Runnable()
            {
                public void run()
                {
                    scanning = false;
                    BtAdapter.stopLeScan(DeviceLeScanCallback);
                    //output("Stop scanning",tv_cs);
                }
            }, SCAN_PERIOD);

            scanning = true;
            BtDevice = null;
            if(BtGatt != null)
            {
                BtGatt.disconnect();
                BtGatt.close();
            }
            BtGatt = null;

            BtAdapter.startLeScan(DeviceLeScanCallback);
            output("Start scanning",tv_cs);
        }
    }



    /*== Click事件 ==*/
    @Override
    public void onClick(View view)
    {
        switch (view.getId()) {
            case R.id.buttonScan:
                BleDeviceScan();
                break;
            case R.id.buttonClear:
                clear();
                break;
        }
    }

    /*== UI ==*/

    private int[] ColorTable = new int[]{Color.BLACK,Color.RED,Color.BLUE,Color.MAGENTA};

    //訊息輸出到TextView
    public void output(String msg, TextView tv)
    {
        //tv.setText(msg);
        Activity acty = (Activity)tv.getContext();
        acty.runOnUiThread(new TextViewOutput(tv, msg));
    }
    public void output(String msg, TextView tv, int Colornum)
    {
        //tv.setTextColor(ColorTable[Colornum]);
        //tv.setText(msg);
        Activity acty = (Activity)tv.getContext();
        acty.runOnUiThread(new TextViewOutput(tv, msg,Colornum));
    }
    //清除TextView
    public void clear()
    {
        tv_cs.setText("");
        tv_hum.setText("");
        tv_mon.setText("");
        tv_temp.setText("");
    }

    //選單(EXIT)
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.action_exit:
                if (BtGatt != null) {
                    BtGatt.disconnect();
                    BtGatt.close();
                }
                finish();
                System.exit(0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    class TextViewOutput implements Runnable
    {
        public TextView console;
        public String message;
        public int colornum;

        public TextViewOutput(TextView paramTextView, String paramString)
        {
            this.console = paramTextView;
            this.message = paramString;
            colornum = 0;
        }
        public TextViewOutput(TextView paramTextView, String paramString, int c)
        {
            this.console = paramTextView;
            this.message = paramString;
            colornum = c;
        }

        public void run()
        {
            this.console.setText(this.message);
            this.console.setTextColor(ColorTable[colornum]);
        }
    }

    //initialize
    private void initialize()
    {
        serviceUuid.add(GATT.UUID_IRT_SERV);
        serviceUuid.add(GATT.UUID_HUM_SERV);
        serviceUuid.add(GATT.UUID_MOV_SERV);

        configUuid.add(GATT.UUID_IRT_CONF);
        configUuid.add(GATT.UUID_HUM_CONF);
        configUuid.add(GATT.UUID_MOV_CONF);

        dataUuid.add(GATT.UUID_IRT_DATA);
        dataUuid.add(GATT.UUID_HUM_DATA);
        dataUuid.add(GATT.UUID_MOV_DATA);
    }

}
