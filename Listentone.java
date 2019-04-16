package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.IllegalFormatCodePointException;
import java.util.List;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }

    public void PreRequest() {
        //mAudioRecord.
        int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[blocksize];

        int bufferedReadResult;

        List<Integer> packet = new ArrayList<>();
        while(true){
            bufferedReadResult = mAudioRecord.read(buffer, 0,blocksize);

            double[] transformed = new double[buffer.length];
            for (int j=0;j<buffer.length;j++) {
                transformed[j] = (double)buffer[j];
            }


            int freq = (int)findFrequency(transformed);

            Log.d("Listentone freq", freq+"");


            if(startFlag && (Math.abs(freq - HANDSHAKE_END_HZ)) < 20){
                extract_packet(packet);

                packet = new ArrayList<>();
                startFlag = false;
                break;
            }
            else if(startFlag){
                if (freq > START_HZ){
                    packet.add(freq);
                }
            }
            else if (Math.abs(freq - HANDSHAKE_START_HZ) < 20){
                startFlag = true;
            }
        }
    }

    public int findPowerSize(int round) {
        int square = 1;
        int i =0;
        while(true){
            if (Math.pow(2,i) >round )break;
            square = (int) Math.pow(2,i);
            i++;
        }
        return square;
    }

    private double findFrequency(double[] toTransform){
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        Double[] freq = this.fftfreq(complx.length, 1);

        for (int i = 0; i < complx.length; i++) {
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum*realNum)+ (imgNum* imgNum));
        }

        int largestIndex = 0;
        for ( int i = 1; i < mag.length; i++ )
        {
            if ( complx[i].abs() > complx[largestIndex].abs() ) largestIndex = i;
        }

        return Math.abs(freq[largestIndex] * mSampleRate);
    }
    public Double[] fftfreq(int length, int d){
        Double[] array = new Double[length];
        if (array.length % 2 == 0){ // 짝수일 때
            for (int index = 0; index < array.length/2; index++) {
                array[index] = index*1.0 / (d*array.length);
            }
            for (int index = array.length/2; index > 0; index--){
                array[array.length-index] = index * -1.0 / (d*array.length);
            }
        }
        else{ // 홀수일 때
            for (int index = 0; index <= array.length/2; index++) {
                array[index] = index*1.0 / (d*array.length);
            }
            for (int index = array.length/2 -1 ; index > 0; index--){
                array[array.length-index] = index * -1.0 / (d*array.length);
            }
        }
        return array;
    }
    public void extract_packet(List<Integer> packet){
        List<Double> freqeuncy = new ArrayList<>();
        for (int index = 0; index < packet.size(); index += 2) {
            freqeuncy.add((double)packet.get(index));
        }
        int[] bit_chunks = new int[freqeuncy.size()];
        for (int index = 0; index < bit_chunks.length; index++) {
            bit_chunks[index] = (int)Math.round(( freqeuncy.get(index) - START_HZ ) / STEP_HZ );
        }
        List<Integer> chunks = new ArrayList<>();
        for (int index = 1; index < bit_chunks.length; index++) {
            if (bit_chunks[index] >= 0 && bit_chunks[index] < Math.pow(2,BITS)){
                chunks.add(bit_chunks[index]);
            }
        }
        List<Integer> out_bytes = new ArrayList<>();
        String result="";
        int next_read_chunk = 0;
        int next_read_bit = 0;

        int bytes = 0;
        int bits_left = 8;

        while (next_read_chunk < (chunks.size()+1)/2) {
            int can_fill = BITS - next_read_bit;
            int to_fill = Math.min(bits_left, can_fill);
            int offset = BITS - next_read_bit - to_fill;
            bytes <<=to_fill;
            int shifted = chunks.get(next_read_chunk) & (((1 << to_fill) - 1) << offset);
            bytes |= shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;
            if(bits_left <= 0){
                out_bytes.add(bytes);
                result = result+(char)bytes;
                Log.d("Listentone bytes",bytes+"");
                Log.d("Listentone StringData",(char)bytes+"");
                Log.d("Listentone ChunkData",result);
                bytes =0;
                bits_left = 8;
            }
            if(next_read_bit >= BITS){
                next_read_chunk += 1;
                next_read_bit -= BITS;
            }
        }
        Log.d("Listentone RESULT",result);
    }
}
