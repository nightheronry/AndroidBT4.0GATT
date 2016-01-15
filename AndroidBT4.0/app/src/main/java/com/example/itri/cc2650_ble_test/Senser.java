package com.example.itri.cc2650_ble_test;

import android.bluetooth.BluetoothGattCharacteristic;

import static java.lang.Math.pow;

/**
 * Created by user on 2015/10/19.
 */
public class Senser
{
    //環境溫度
    public static double extractAmbientTemperature(byte [] v)
    {
        int offset = 2;
        return shortUnsignedAtOffset(v, offset) / 128.0;
    }

    //目標溫度
    public static double extractTargetTemperature(byte [] v, double ambient)
    {
        Integer twoByteValue = shortSignedAtOffset(v, 0);

        double Vobj2 = twoByteValue.doubleValue();
        Vobj2 *= 0.00000015625;

        double Tdie = ambient + 273.15;

        double S0 = 5.593E-14; // Calibration factor
        double a1 = 1.75E-3;
        double a2 = -1.678E-5;
        double b0 = -2.94E-5;
        double b1 = -5.7E-7;
        double b2 = 4.63E-9;
        double c2 = 13.4;
        double Tref = 298.15;
        double S = S0 * (1 + a1 * (Tdie - Tref) + a2 * pow((Tdie - Tref), 2));
        double Vos = b0 + b1 * (Tdie - Tref) + b2 * pow((Tdie - Tref), 2);
        double fObj = (Vobj2 - Vos) + c2 * pow((Vobj2 - Vos), 2);
        double tObj = pow(pow(Tdie, 4) + (fObj / S), .25);

        return tObj - 273.15;
    }

    //加速度計
    public static double[] extractACC(byte[] value)
    {
        final float SCALE = (float) 4096.0;

        int x = (value[7]<<8) + value[6];
        int y = (value[9]<<8) + value[8];
        int z = (value[11]<<8) + value[10];

        return new double[]{(x/SCALE)*-1,y/SCALE,(z/SCALE)*-1};
    }

    //陀螺儀
    public static double[] extractGYRO(byte[] value)
    {
        final float SCALE = (float) 128.0;

        int x = (value[1]<<8) + value[0];
        int y = (value[3]<<8) + value[2];
        int z = (value[5]<<8) + value[4];

        return new double[]{x/SCALE,y/SCALE,z/SCALE};
    }

    //指北針
    public static double[] extractCMP(byte[] value)
    {
        final float SCALE = (float) (32768 / 4912);
        if (value.length >= 18)
        {
            int x = (value[13]<<8) + value[12];
            int y = (value[15]<<8) + value[14];
            int z = (value[17]<<8) + value[16];
            return new double[]{x / SCALE, y / SCALE, z / SCALE};
        }
        else return new double[]{0,0,0};
    }

    //溼度計
    public static double extractHUM(byte[] v)
    {
        int a = shortUnsignedAtOffset(v, 2);
        a = a - (a % 4);

        return ((-6f) + 125f * (a / 65535f));
    }





    private static Integer shortSignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1]; // // Interpret MSB as signed
        return (upperByte << 8) + lowerByte;
    }

    private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1] & 0xFF;
        return (upperByte << 8) + lowerByte;
    }
}
