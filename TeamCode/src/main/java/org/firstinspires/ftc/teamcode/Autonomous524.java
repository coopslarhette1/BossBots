/*
Copyright (c) 2016 Robert Atkinson

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted (subject to the limitations in the disclaimer below) provided that
the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of Robert Atkinson nor the names of his contributors may be used to
endorse or promote products derived from this software without specific prior
written permission.

NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESSFOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.firstinspires.ftc.teamcode;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import com.qualcomm.robotcore.eventloop.opmode.*;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.util.ElapsedTime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;


@com.qualcomm.robotcore.eventloop.opmode.Autonomous (name = "Team 524 Autonomous", group = "Iterative Opmode")
// @Autonomous(...) is the other common choice
@Disabled
public class Autonomous524 extends MecanumOpMode {
    /* Declare OpMode members. */
    private ElapsedTime runtime = new ElapsedTime();
    private DcMotor belt;
    private DcMotor eightyTwenty;
    private DcMotor sweeper;

    //Phone sensors
    private Sensor magnetometer;
    private Sensor accelerometer;
    private float compassX;
    private float compassY;
    private float compassZ;
    public float accX;
    public float accY;
    public float accZ;

    //PID VARS
    private double setx, sety, seth; //set points for x, y and theta
    private double setxNorm, setyNorm; //lengths of the setpoint vectors (needed after conversion into phone coordinate system)
    private double lerrx, lerry, lerth; //last errors in x, y and theta
    private double errx, erry, erth; //errors in x, y and theta
    private double curx, cury, cuth; //current x, y and theta from IMU (pun on cury!)
    private double outx, outy, outh; //the output velocity values for x, y and theta
    private double derx, dery, dert; //derivatives of x, y and t(heta)
    //private double ierx, iery, iert; //integrals of x, y and t(heta) (not needed)
    //private double serx, sery, sert; //sums of the value-time products (not needed)
    private double px, dx, py, dy, pt, dt; //the proportional, integral(not needed, so removed) and differential constants for x, y and t(heta)
    private long timeFromStart;//get the timer class thingy
    private long currTime; //current time
    private double[] forward; //the vector for taking me forward
    private double[] right; //the vector to take me right
    private double[] ccw; //the vector to take me counterclockwise
    private double[] resultant; //the resultant vector

    private double[] b; //initial magnetic field vector
    private double[] bprime; //projection of magnetic field on the plane perpendicular to the gravitational field vector
    private double[] bcurrPrime; //projection of magnetic field on the plane perpendicular to the gravitational field vector
    private double[][] matrixT; //transformation matrix that takes in vectors in the gamefield reference frame and outputs vectors in the phone reference frame
    private double[][] matrixR; //rotation matrix that depends on "calibration" and rotates the x and y basis vectors
    private double[] g; // initial gravitational accl vector
    //private double[] c; // I don't know what this is.
    private double[] gprime; //unit vector in the direction of g
    private double[] x; //cross product of y and g
    private double[] y; // unit vector in the direction of bprime
    private double[] h; //runtime acceleration vector with gravity
    private double[] a; //runtime acceleration vector withOUT gravity
    private double[] s; //runtime displacement vector
    private double[] bcurr; //runtime magnetic field vector (pun!)
    private double[] setxT; //x setpoint in phone coordinate system
    private double[] setyT; //y setpoint in phone coordinate system
    private double[] curxT; //y current value in phone coordinate system
    private double[] curyT; //y current value in phone coordinate system
    private double[] sx; //projection of s[] on curxT
    private double[] sy; //projection of s[] on curyT
    private double anglecos; //the cosine of the angle between bcurrPrime and y, in radians
    private double crossBcpYNorm; //magnitude of crossBcpY
    private double crossBcpYPrimeNorm; //magnitude of crossBcpYPrime
    private double[] crossBcpY; //cross product of bcurrPrime and y
    private double[] crossBcpYPrime; //cross product of y and bcurrPrime
    private String teamColor;
    private int interval = SensorManager.SENSOR_DELAY_FASTEST; //interval for integration; should be the same as the sample period for the IMU
    private double calibration; //stores the offset between the magnetic y-axis and the gamefield y-axis in RADIANS


    /*
    *   Motor position
    *
    * motor4     motor3
    *    []-------[]
    *      |     |
    *      |     |
    *      |     |
    *    []-------[]
    *  motor1    motor2
    */

    /*
     * Code to run ONCE when the driver hits INIT
     */
    @Override
    public void init() {


        motor1 = hardwareMap.dcMotor.get("motor1");
        motor1.setDirection(DcMotorSimple.Direction.REVERSE);
        motor2 = hardwareMap.dcMotor.get("motor2");
        motor3 = hardwareMap.dcMotor.get("motor3");
        motor4 = hardwareMap.dcMotor.get("motor4");
        motor4.setDirection(DcMotorSimple.Direction.REVERSE);

        light = hardwareMap.lightSensor.get("light");

        color = hardwareMap.colorSensor.get("color");

        teamColor = "r";

        //Initialize sensor service
        sensorService = (SensorManager) hardwareMap.appContext.getSystemService(Context.SENSOR_SERVICE);
        telemetry.addData("Status", "Created SensorService");
        //Magnetometer initialization
        magnetometer = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        telemetry.addData("Status", "Created Magnenetometer");
        //Accelerometer initialization
        accelerometer = sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        telemetry.addData("Status", "Created Accelerometer");
        sensorService.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        telemetry.addData("Status", "Registered acclerometer");
        sensorService.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        telemetry.addData("Status", "Registered magnetometer");

        //x,y,z
        b = new double[3];
        g = new double[3];
        //c = new double[3]; //I don't think we need this
        h = new double[3];
        a = new double[3];
        gprime = new double[3];
        bcurr = new double[3];
        setxT = new double[3];
        setyT = new double[3];
        curxT = new double[3];
        curyT = new double[3];
        sx = new double[3];
        sy = new double[3];
        crossBcpY = new double[3];
        crossBcpYPrime = new double[3];
        bprime = new double[3];
        bcurrPrime = new double[3];
        gprime = new double[3];
        x = new double[3];
        y = new double[3];
        s = new double[3];
        matrixT = new double[3][3];
        matrixR = new double[3][3];
        //setx = new double[3]; setx and sety can be scalars because they are entered in the gamefield coordinate system
        //sety = new double[3];

        //Stores acceleration due to gravity
        g[0] = accX;
        g[1] = accY;
        g[2] = accZ;
        //Stores all unit vector value
        b[0] = compassX;
        b[1] = compassY;
        b[2] = compassZ;


        for (int i = 0; i <= 2; i++) {
            gprime[i] = (euclidianNorm(g[i], g[0], g[1], g[2]));
        }

        for (int i = 0; i <= 2; i++) {
            bprime[i] = (b[i] - ((dotProduct(b, g) / Math.pow(Norm(g), 2)) * b[i]));
        }

        for (int i = 0; i <= 2; i++) {
            y[i] = (euclidianNorm(bprime[i], bprime[0], bprime[1], bprime[2]));
        }
        //this is the 3-D rotation matrix for rotating y about the g[] vector by the angle calibration
        //this is what I have done: https://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle
        //as x is calculated as the cross product between y and g, we do not need to separately rotate x
        matrixR[0][0] = Math.cos(calibration) + (gprime[0] * gprime[0] * (1 - Math.cos(calibration)));
        matrixR[0][1] = (gprime[0] * gprime[1] * (1 - Math.cos(calibration))) - (gprime[2] * Math.sin(calibration));
        matrixR[0][2] = (gprime[0] * gprime[2] * (1 - Math.cos(calibration))) + (gprime[1] * Math.sin(calibration));
        matrixR[1][0] = (gprime[1] * gprime[0] * (1 - Math.cos(calibration))) + (gprime[2] * Math.sin(calibration));
        matrixR[1][1] = Math.cos(calibration) + (gprime[1] * gprime[1] * (1 - Math.cos(calibration)));
        matrixR[1][2] = (gprime[1] * gprime[2] * (1 - Math.cos(calibration))) - (gprime[0] * Math.sin(calibration));
        matrixR[2][0] = (gprime[2] * gprime[0] * (1 - Math.cos(calibration))) - (gprime[1] * Math.sin(calibration));
        matrixR[2][1] = (gprime[2] * gprime[1] * (1 - Math.cos(calibration))) + (gprime[0] * Math.sin(calibration));
        matrixR[2][2] = Math.cos(calibration) + (gprime[2] * gprime[2] * (1 - Math.cos(calibration)));

        //multiply matrixR and y
        for (int i = 0; i <= 2; i++) {
            y[i] = (matrixR[i][0] * y[0]) + (matrixR[i][1] * y[1]) + (matrixR[i][2] * y[2]);
        }
        //x is given by the cross product y X g
        x[0] = (y[1] * gprime[2]) - (y[2] * gprime[1]);
        x[1] = (y[2] * gprime[0]) - (y[0] * gprime[2]);
        x[2] = (y[0] * gprime[1]) - (y[1] * gprime[0]);

        for (int i = 0; i <= 2; i++) {
            matrixT[i][0] = x[i];
            matrixT[i][1] = y[i];
            matrixT[i][2] = gprime[i];
        }
        telemetry.addData("Status", "Initialized");
    }

    /*
     * Code to run REPEATEDLY after the driver hits INIT, but before they hit PLAY
     */
    @Override
    public void init_loop() {
    }

    /*
     * Code to run ONCE when the driver hits PLAY
     */
    @Override
    public void start() {
        runtime.reset();
    }

    /*
     * Code to run REPEATEDLY after the driver hits PLAY but before they hit STOP
     */
    private boolean task1=false;
    @Override
    public void loop() {
        //telemetry.addData("Status", "Running: " + runtime.toString());

        if(!task1){
            if(light.getLightDetected()>0.049){
                motor1.setPower(0);
                motor3.setPower(0);
                motor2.setPower(0);
                motor4.setPower(0);
                task1=true;
            } else {
                motor1.setPower(0.1);
                motor3.setPower(0.1);
                motor2.setPower(0.1);
                motor4.setPower(0.1);
            }
        }
    }

    /*
     * Code to run ONCE after the driver hits STOP
     */
    @Override
    public void stop() {
    }

    //this needs to repeat once every "interval"
    public double[] resultant(double mySetx, double mySety, double mySeth) {
        setx = mySetx;
        sety = mySety;
        seth = mySeth;

        /**technically, the math here can be simplified significantly (the need for matrixT can be eliminated) but that is too much work
         *I will do that sometime later, maybe after the first competition
         */
        //transform the setpoint vectors into the phone coordinate system from the gamefield coordinate system
        for (int i = 0; i <= 2; i++) {
            setxT[i] = setx * matrixT[i][0];
        }
        for (int i = 0; i <= 2; i++) {
            setyT[i] = sety * matrixT[i][1];
        }

        setxNorm = Norm(setxT);
        setyNorm = Norm(setyT);

        //errors in the robot's state are calculated as (setpoint-current)
        errx = setxNorm - curx;
        erry = setyNorm - cury;
        erth = seth - cuth;

        //the derivatives
        derx = (errx - lerrx) / interval;
        dery = (erry - lerry) / interval;
        dert = (erth - lerth) / interval;

   /* //the integrals
    ierx = (errx * interval);
    serx = serx + ierx;
    iery = (erry * interval);
    sery = sery + iery;
    iert = (erth * interval);
    sert = sert + iert;
    */
        /**set the p and d constants after calibration
         *(how about make a neural network to do machine learning and let the phone figure these out on its own?)
         */
        px = 2;
        py = 2;
        pt = 2;
        dx = 2;
        dy = 2;
        dt = 2;


        // outputs for the variables
        outx = (px * errx) + (dx * derx);
        outy = (py * erry) + (dy * dery);
        outh = (pt * erth) + (dt * dert);

        telemetry.addData("outX", outx);
        telemetry.addData("outY", outy);
        telemetry.addData("outh", outh);


        //limit the outputs to [-1,1]
        if (outx >= 1) {
            outx = 1;
        }

        if (outy >= 1) {
            outy = 1;
        }


        if (outh >= 1) {
            outh = 1;
        }


        if (outx <= -1) {
            outx = -1;
        }


        if (outy <= -1) {
            outy = -1;
        }


        if (outh <= -1) {
            outh = -1;
        }


        resultant = new double[4];
        //assign the vectors
        double forward[] = {(outx * -1), (outx * 1), (outx * 1), (outx * -1)};
        double right[] = {(outy * 1), (outy * 1), (outy * -1), (outy * -1)};
        double ccw[] = {(outh * 1), (outh * 1), (outh * 1), (outh * 1)};


        for (int i = 0; i <= 3; i++) {
            resultant[i] = forward[i] + right[i] + ccw[i];
        }


        //current error becomes last error
        lerrx = errx;
        lerry = erry;
        lerth = erth;


        return resultant;


    }


    //this is a misnomer; you actually get a UNIT VECTOR out of this
    public double euclidianNorm(double numerator, double a, double b, double c) {
        return numerator / Math.sqrt(Math.pow(a, 2) + Math.pow(b, 2) + Math.pow(c, 2));
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Ignoring this for now
    }

    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accX = sensorEvent.values[0];
                accY = sensorEvent.values[1];
                accZ = sensorEvent.values[2];

                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                compassX = sensorEvent.values[0];
                compassY = sensorEvent.values[1];
                compassZ = sensorEvent.values[2];

                break;
        }
        // Print out data
        telemetry.addData("red", color.red());
        telemetry.addData("blue", color.blue());
        telemetry.addData("green", color.green());
        telemetry.addData("light", light.getLightDetected());

        // measured accelerations with gravity
        h[0] = accX;
        h[1] = accY;
        h[2] = accZ;

        for (int i = 0; i <= 2; i++) {
            a[i] = h[i] - g[i];
        }

        //removing gravity
        for (int i = 0; i <= 2; i++) {
            a[i] = h[i] - g[i];
        }

        //integrating twice to get displacement (remember kinematics?)
        for (int i = 0; i <= 2; i++) {
            s[i] = (0.5 * a[i] * interval * interval);
        }

        //convert curx and cury to curxT and curyT with matrixT, add s[] (see below), and take the norm to get curxNorm and curyNorm
        //find projection of s[] on curxT and curyT, then add the projections to curxT and curyT (because s[] is the resultant displacement)

        //these loops transform curx and cury in gamefield coords into curxT and curyT in phone coords
        for (int i = 0; i <= 2; i++) {
            curxT[i] = curx * matrixT[i][0];
        }


        for (int i = 0; i <= 2; i++) {
            curyT[i] = cury * matrixT[i][1];
        }

        //these loops project s[] onto curxT and curyT
        for (int i = 0; i <= 2; i++) {
            sx[i] = (dotProduct(curxT, s) / Math.pow(Norm(curxT), 2)) * s[i];
        }

        for (int i = 0; i <= 2; i++) {
            sy[i] = (dotProduct(curyT, s) / Math.pow(Norm(curyT), 2)) * s[i];
        }
        //find projection with the assumption that curxT ad curyT are orthogonal and see if results are equal for troubleshooting


        //now we add sx to curxT and sy to curyT to update them
        for (int i = 0; i <= 2; i++) {
            curxT[i] += sx[i];
        }

        for (int i = 0; i <= 2; i++) {
            curyT[i] += sy[i];
        }
        //update curx and cury by converting the vectors curxT and curyT into the scalars curx and cury using the Norm function
        curx = Norm(curxT);
        cury = Norm(curyT);


        //current magnetometer values
        bcurr[0] = compassX;
        bcurr[1] = compassY;
        bcurr[2] = compassZ;


        //project the bcurr[] vector onto the plane perpendicular to the g[] vector to obtain bcurrPrime[]
        for (int i = 0; i <= 2; i++) {
            bcurrPrime[i] = (bcurr[i] - (bcurr[i] * euclidianNorm((g[i] * bcurr[i]), g[0], g[1], g[2])));
        }


        //angle between bcurrPrime and y
        anglecos = dotProduct(bcurrPrime, y) / (Norm(bcurrPrime) * Norm(y));


        //cross product of bcurrPrime and y (counter-clockwise rotation is positive) (y X bcurrPrime)
        crossBcpY[0] = (y[1] * bcurrPrime[2]) - (y[2] * bcurrPrime[1]);
        crossBcpY[1] = (y[2] * bcurrPrime[0]) - (y[0] * bcurrPrime[2]);
        crossBcpY[2] = (y[0] * bcurrPrime[1]) - (y[1] * bcurrPrime[0]);


        //cross product of y and bcurrPrime (clockwise rotation is positive) (bcurrPrime X y)
        crossBcpYPrime[0] = (bcurrPrime[1] * y[2]) - (bcurrPrime[2] * y[1]);
        crossBcpYPrime[1] = (bcurrPrime[2] * y[0]) - (bcurrPrime[0] * y[2]);
        crossBcpYPrime[2] = (bcurrPrime[0] * y[1]) - (bcurrPrime[1] * y[0]);


        crossBcpYNorm = Norm(crossBcpY);
        crossBcpYPrimeNorm = Norm(crossBcpYPrime);


        //angle in radians between current magnetic field vector and initialization magnetic field vector
        cuth = Math.acos(anglecos);


        //if 0 <= cuth <= pi
        if (crossBcpYNorm >= crossBcpYPrimeNorm) {
            cuth = 1 * cuth;
        }
        //if pi < cuth < 2pi
        if (crossBcpYNorm < crossBcpYPrimeNorm) {
            cuth = (2 * Math.PI) - cuth;
        }
        telemetry.addData("Resultant",resultant(1,0,0)[0]);
    }


    //function to calculate the DOT PRODUCT of two 3-D vectors
    public double dotProduct(double[] p, double[] q) {
        return (p[0] * q[0]) + (p[1] * q[1]) + (p[2] * q[2]);
    }

    //function to calculate the LENGTH (Euclidean norm) of a 3-D vector
    public double Norm(double[] vector) {
        return Math.sqrt(Math.pow(vector[0], 2) + Math.pow(vector[1], 2) + Math.pow(vector[2], 2));
    }

}