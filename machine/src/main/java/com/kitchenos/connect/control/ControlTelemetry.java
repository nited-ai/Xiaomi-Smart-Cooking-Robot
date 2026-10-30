package com.kitchenos.connect.control;

/** Passive protocol observations, never a source of actuator commands.
 * Raw units follow the OEM audit, not unverified amps, torque or food viscosity. */
public final class ControlTelemetry {
    public static String frame(String type,String raw,long receivedAt,int requestedTemperature,int requestedSpeed){
        int size="status".equals(type)?24:"info".equals(type)?20:0;
        if(size==0||raw==null||raw.length()!=size*2)return null;
        int[] bytes=new int[size];
        for(int i=0;i<size;i++){
            int high=Character.digit(raw.charAt(i*2),16),low=Character.digit(raw.charAt(i*2+1),16);
            if(high<0||low<0)return null;
            bytes[i]=(high<<4)|low;
        }
        StringBuilder out=new StringBuilder("{\"schema\":1,\"type\":\"").append(type)
            .append("\",\"receivedElapsedMs\":").append(receivedAt)
            .append(",\"requestedTemperature\":").append(requestedTemperature)
            .append(",\"requestedSpeed\":").append(requestedSpeed)
            .append(",\"raw\":\"").append(raw).append('"');
        if(size==24){
            field(out,"state",bytes[0]);field(out,"phase",bytes[1]);
            field(out,"ihControlRaw",bytes[2]);field(out,"ihPowerRaw",bytes[3]);
            field(out,"temperatureRaw",bytes[4]);field(out,"motorStatusRaw",bytes[6]);
            field(out,"error",bytes[11]);field(out,"motorRaw",bytes[12]|bytes[13]<<8);
            field(out,"weightRaw",(short)(bytes[14]|bytes[15]<<8));
        }else{
            field(out,"mcuVersion",bytes[0]);field(out,"ihVersion",bytes[1]);field(out,"motorVersion",bytes[3]);
            field(out,"ihVoltageRaw",bytes[4]);field(out,"ihCurrentRaw",bytes[5]);
            field(out,"ihTemperatureRaw",bytes[8]);field(out,"motorVoltageRaw",bytes[9]);
            // OEM getMotorCurrent() conflicts with its own 'motor control gear' description.
            field(out,"motorControlRaw",bytes[10]);field(out,"motorDriverTemperatureRaw",bytes[11]);
            field(out,"motorTemperatureRaw",bytes[12]);field(out,"switchesRaw",bytes[18]);
        }
        return out.append('}').toString();
    }
    private static void field(StringBuilder out,String name,int value){out.append(",\"").append(name).append("\":").append(value);}
    private ControlTelemetry(){}
}
