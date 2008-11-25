/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.wowwee;
/**
 * Command codes for WowWee RoboQuad.
 * 
 * From http://profmason.com/wp-content/uploads/2007/12/roboquadircodes.txt
 * @author tobi
 */
public class RoboQuadCommands {
//RoboQuadCommands Header is 0x6.  It uses a 12 bit 1200 baud protocal with no stop bits.  It wants about 500 ms before retransmission
    public static final int Stop=0x600;
    public static final int Walk_Forward=0x601;
    public static final int Right_Crab_Walk=0x604;
    public static final int Left_Crab_Walk=0x603;
    public static final int Left_Crab_Four_Steps=0x623;
    public static final int Right_Crab_Four_Steps=0x624;
    public static final int Backward_Four_Steps=0x622;
    public static final int Walk_Backward=0x602;
    public static final int Forward_Four_Steps=0x621;
    public static final int Rotate_Counter_Clockwise=0x60a;
    public static final int Counter_Clockwise_Four_Steps=0x62a;
    public static final int Rotate_Clockwise=0x609;
    public static final int Clockwise_Four_Steps=0x629;
    public static final int Head_Up=0x681;
    public static final int Head_Down=0x682;
    public static final int Head_Clockwise=0x689;
    public static final int Head_Counter_Clockwise=0x68a;
    public static final int Top_Left_Shuffle=0x605;
    public static final int Top_Right_Shuffle=0x606;
    public static final int Bottom_Left_Shuffle=0x607;
    public static final int Bottom_Right_Shuffle=0x608;
    public static final int Left_Strafe=0x625;
    public static final int Right_Strafe=0x626;
    public static final int Left_Turn_Roll=0x627;
    public static final int Right_Turn_Roll=0x628;
    public static final int Burst=0x64a;
    public static final int Single_Shot=0x649;
    public static final int Stomp_Walk=0x641;
    public static final int Left_Legs_In=0x643;
    public static final int Left_Legs_Out=0x683;
    public static final int Left_Forward_Leg_In=0x645;
    public static final int Left_Forward_Leg_Out=0x685;
    public static final int Left_Backward_Leg_In=0x647;
    public static final int Left_Backward_Leg_Out=0x687;
    public static final int Right_Legs_In=0x644;
    public static final int Right_Legs_Out=0x684;
    public static final int Right_Forward_Leg_In=0x646;
    public static final int Right_Forward_Leg_Out=0x686;
    public static final int Right_Backward_Leg_In=0x648;
    public static final int Right_Backward_Leg_Out=0x688;
    public static final int Program=0x614;
    public static final int Play_Program=0x615;
    public static final int Program_Delete_Last_Step=0x634;
    public static final int Erase_Program=0x654;
    public static final int Scan_Left_For_Object=0x632;
    public static final int Scan_Right_For_Object=0x652;
    public static final int Smart_Scan=0x631;
    public static final int Approach_Nearest_Object=0x650;
    public static final int Escape_Walk=0x690;
    public static final int Toggle_Activity_Level_1=0x6d0;
    public static final int Toggle_Activity_Level_2=0x6d1;
    public static final int Toggle_Activity_Level_3=0x6d2;
    public static final int Toggle_Aggression_1=0x6c0;
    public static final int Toggle_Aggression_2=0x6d0;
    public static final int Toggle_Aggression_3=0x6e0;
    public static final int Toggle_Awareness_1=0x6e0;
    public static final int Toggle_Awareness_2=0x6e4;
    public static final int Toggle_Awareness_3=0x6e8;
    public static final int Leg_Reset=0x620;
    public static final int Full_Reset=0x640;
    public static final int Volume_Up=0x695;
    public static final int Volume_Down=0x694;
    public static final int Guard=0x630;
    public static final int Sleep=0x680;
    public static final int Toggle_Autonomy=0x610;
    public static final int Toggle_Sensors=0x635;
    public static final int Twitch=0x651;
    public static final int Surprise=0x642;
    public static final int Wave=0x696;
    public static final int Dizzy=0x655;
    public static final int Attack=0x653;
    public static final int Roar=0x633;
    public static final int Aware_Stance=0x692;
    public static final int High_Stance=0x691;
    public static final int Aggressive_Stance=0x693;
    public static final int Dance_Demo=0x616;
    public static final int Movement_Demo=0x636;
    public static final int Leg_Check=0x656;
}
