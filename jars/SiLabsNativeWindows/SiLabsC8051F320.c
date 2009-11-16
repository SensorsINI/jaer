// SiLabsC8051F320: implementations of the native methods in class SiLabsC8051F320.
/* 
source for USB AE Monitor java native method using simple Sevilla USB AE board and SiLabs USBXPress

Modified July 2006 for servo motor control by Tobi.


  Note: only supports a single device in a JVM now!  all vars are static and thus shared among
  DLL uses. this is so that subsequent calls can retrieve related data.


 -Copyright Tobi Delbruck 2005
 tobi feb 2005/oct 2005
 */

#include "stdafx.h"

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    return TRUE;
}


#define DEVICE_STILL_THERE_POLLING_INTERVAL_MS 1000
#define DRIVER_REOPEN_NO_EVENTS_TIMEOUT_MS 5000 // time in ms between closing/reopening driver when no events come
#define TIMEOUT_MS_READ		0	// defines timeout for read, write. This is set to 0 so that read will immediately 
								// return with available events at the time of the call
#define TIMEOUT_MS_WRITE 300 // timeout for write (e.g. biasgen commands)
// see these topics for timeouts
// http://www.cygnal.org/ubb/Forum9/HTML/000439.html -- timeouts are thread-local in windows, they are in SiUSBXp.dll, not device driver


#define TICK_US	1	// this is timer tick on USB AER microcontroller. See firmware in folder "Device"

#define USBAEMON_ALREADY_OPEN -2  // our own error returns
#define USBAEMON_NO_DEVICES -3
#define USBAEMON_NUM_DEVICES_ERROR -4
#define USBAEMON_DEVICE_NOT_OPEN -5

#define MSG_LENGTH 64
#define BIAS_FLASH_START 0x0f00 // start of flash memory for biases
#define BIAS_BIASES 1	// send biases out on SPI
#define BIAS_SETPOWER 2	// set the biasgen powerDown input
#define BIAS_FLASH 4	//  flash the biases
#define AE_EVENT_ACQUISITION_ENABLED 5 // set event aquisition enabled
#define CMD_RESETTIMESTAMPS 6 // reset the device timestamps
#define CMD_SETSERVO 7 // set a servo PWM output value

// MS VC should compile this ".c" file with the plain C compiler based on ".c" extension, so no extern "C" is needed

// these are declared static to be invisible outside
static void closeDevice(void); // exit handler
static void onError(SI_STATUS);
static SI_STATUS flushBuffers(void);
int checkStillThere(void);
void refreshStaleDriver(void);

#ifdef __cplusplus
extern "C" {
#endif

static jclass clazz=NULL;
static jfieldID addressesFieldID=NULL, timestampsFieldID=NULL, overrunOccuredFieldID=NULL;
// deviceHandle is static local var so that DLL exit handler could use it to close device	
static HANDLE deviceHandle;	// handle returned to device by SI_Open. used by SI_Close
static unsigned char aeBuffer[SI_MAX_READ_SIZE]; // static buffer for collected events
static unsigned short addressBuffer[SI_MAX_READ_SIZE/4]; // static buffer for collected events
static unsigned int timestampBuffer[SI_MAX_READ_SIZE/4]; // static buffer for collected events
static int numEvents;
static jboolean overrunOccured=JNI_FALSE;
static DWORD deviceNum;	//	usb device number (will almost always be 0)
static DWORD	numDevices;
static SI_STATUS status;
static char deviceString[SI_MAX_DEVICE_STRLEN];    
static unsigned int wrapAdd=0;		// static offset to add to spike timestamps
static unsigned short lastshortts=0;		// holds last 16 bit timestamp of previous event, to detect wrapping
static DWORD lastTimeCheckedDeviceThere;
static DWORD lastTimeGotEvent; // used to close and reopen periodically to refresh driver, which tends to go stale if nothing comes over the channel
static unsigned char commandBuffer[MSG_LENGTH];

// every so often, check if device is still actually there
// if there, just continue.
// if error or no device found, return error and clear device handle. 
// user code can try/catch around calls to maintain flexible connection
// returns 0=SI_SUCCESS if still there, otherwise returns USAEMON_NO_DEVICES or another error code
int checkStillThere(){
	refreshStaleDriver();
	if(GetTickCount()-lastTimeCheckedDeviceThere>DEVICE_STILL_THERE_POLLING_INTERVAL_MS){
		lastTimeCheckedDeviceThere=GetTickCount();
		status=(SI_GetNumDevices)(&numDevices);
		if(status!=SI_SUCCESS){
//			printf("error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
//			if(status==0xff){printf("SI_DEVICE_NOT_FOUND \n(USBXPress Device driver not installed, cable not connected, device not powered?)\n");}
			onError(status);
			fflush(stdout);
			return status;  // returns error
		}	
		if(numDevices==0){
			deviceHandle=NULL;
//			printf("error SI_GetNumDevices no devices"); // returns error
//			fflush(stdout);
			return USBAEMON_NO_DEVICES;
		}
	}
	return 0;
}

void refreshStaleDriver(){
	if(GetTickCount()-lastTimeGotEvent>DRIVER_REOPEN_NO_EVENTS_TIMEOUT_MS){
		printf("usbaemon: No events for a while, reopening driver to refresh it\n");
		closeDevice();

		status=(SI_GetNumDevices)(&numDevices);
		if(status!=SI_SUCCESS){
			printf("error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
			if(status==0xff){printf("SI_DEVICE_NOT_FOUND \n(USBXPress Device driver not installed, cable not connected, device not powered?)\n");}
			onError(status);
		}	
		if(numDevices==0){
			printf("error SI_GetNumDevices no devices");
		}else if(numDevices>1){
			//printf("usbaemon: %d device(s) found, choosing first one\n",numDevices);
		}
		deviceNum=0;	// always choose first device
						
		status = (SI_SetTimeouts)(TIMEOUT_MS_READ,TIMEOUT_MS_WRITE); // set readtimeout, writetimeout  for recovery from hang
															// write timeout set even though we never write (so far)
		if(status!=SI_SUCCESS){
			onError(status);
			printf("error SI_SetTimeouts");
		}
		//mexPrintf("setTimeouts OK\n");

		status = (SI_Open)(0, &deviceHandle);
		if(status!=SI_SUCCESS){
			onError(status);
			printf("error SI_Open");
		}
//		mexPrintf("device opened, device handle=%d\n",deviceHandle);
		
		// init timers
		lastTimeCheckedDeviceThere=GetTickCount();
		lastTimeGotEvent=GetTickCount();
		
	}
}

/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_initIDs
(JNIEnv *env, jclass clazz){
	 // get field IDs in class for later use and cache the field IDs in static vars
/* at present there are no fields in object to reference
	if(addressesFieldID==NULL){
		addressesFieldID=(*env)->GetFieldID(env,clazz,"addresses","[S");
		if(addressesFieldID==NULL) return -1;
		timestampsFieldID=(*env)->GetFieldID(env,clazz,"timestamps","[I");
		if(timestampsFieldID==NULL) return -1;
		overrunOccuredFieldID=(*env)->GetFieldID(env,clazz,"overrunOccured","Z");
		if(overrunOccuredFieldID==NULL) return -1;
	}
*/
	return 0; //success
}

/*

	We have already captured numEvents events into buffers addressBuffer and timestampBuffer.
	Here we construct the new java array, get a pointer to a native version of it, copy
	the events to the native version and release the array, returning it to the caller.

 */

 /*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    getAddresses
 * Signature: ()[S
 */
JNIEXPORT jshortArray JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeGetAddresses
(JNIEnv *env, jobject obj){
 	unsigned short*aePtr;
	jshortArray jaddresses=NULL;
	int i;

	if(numEvents==0) return NULL;

	// allocate java arrays to hold the events
	jaddresses=(*env)->NewShortArray(env,numEvents);
	if(jaddresses==NULL) return NULL; // memory?


	// get native pointers hopefully directly (or perhaps copy) to these memory segments
	// do not make any JNI calls between Get and Release of these arrays
	aePtr=(*env)->GetPrimitiveArrayCritical(env,jaddresses,0);
	if(aePtr==0) return NULL; // out of memory

	// copy events to the native mirrors of arrays
	for(i=0;i<numEvents;i++){
		aePtr[i]=(short)addressBuffer[i];
	}

	// release hold on arrays
	(*env)->ReleasePrimitiveArrayCritical(env,jaddresses,aePtr,0);
	return jaddresses;
}

/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    getTimestamps
 * Signature: ()[I
 */
JNIEXPORT jintArray JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeGetTimestamps
(JNIEnv *env, jobject obj){

	unsigned int *tsPtr;	// pointer to 32 bit returned timetamp arrar
	jintArray jtimestamps=NULL;
	int i;

	if(numEvents==0) return NULL;

	jtimestamps=(*env)->NewIntArray(env,numEvents);
	if(jtimestamps==NULL) return NULL; // memory?

	// get native pointers hopefully directly (or perhaps copy) to these memory segments
	// do not make any JNI calls between Get and Release of these arrays
	tsPtr=(*env)->GetPrimitiveArrayCritical(env,jtimestamps,0);
	if(tsPtr==0) return NULL; // out of memory

	// copy events to the native mirrors of arrays
	for(i=0;i<numEvents;i++){
		tsPtr[i]=(int)timestampBuffer[i];
	}

	// release hold on arrays
	(*env)->ReleasePrimitiveArrayCritical(env,jtimestamps,tsPtr,0);

	return jtimestamps;
}

/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    overrunOccured
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeOverrunOccured
(JNIEnv *env, jobject obj){
	return (jboolean)overrunOccured;
}

/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    open
 * Signature: ()V
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeOpen
(JNIEnv *env, jobject obj){
	// start actual use of functions from library, finally!!!
	if(deviceHandle==NULL){ // we only open device once
		//printf("SiLabsC8051F320.c: finding number of attached devices\n");
		status=(SI_GetNumDevices)(&numDevices);
		if(status!=SI_SUCCESS){
			printf("SiLabsC8051F320.c: error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
			if(status==0xff){printf("SiLabsC8051F320.c: SI_DEVICE_NOT_FOUND \n(USBXPress Device driver not installed, cable not connected, device not powered?)\n");}
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_GetNumDevices\n");
			fflush(stdout);
			return status;
		}	
		if(numDevices==0){
//			printf("SiLabsC8051F320.c: error SI_GetNumDevices no devices\n");
			fflush(stdout);
			return USBAEMON_NO_DEVICES;
		}
//		printf("SiLabsC8051F320.c: %d device(s) found, choosing first one\n",numDevices);
		deviceNum=0;	// always choose first device
		

		deviceString[0]=0;
		status=(SI_GetProductString)(deviceNum,deviceString,SI_RETURN_DESCRIPTION);
		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_GetProductString\n");
			fflush(stdout);
			return status;
		}
//		printf("SiLabsC8051F320.c: USB AE Mon product string: %s\n",deviceString); // this never returns something sensible
//		fflush(stdout);
		
		status = (SI_SetTimeouts)(TIMEOUT_MS_READ,TIMEOUT_MS_WRITE); 

		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_SetTimeouts");
//			fflush(stdout);
			return status;
		}
		//printf("SiLabsC8051F320.c: setTimeouts OK\n");

		status = (SI_Open)(0, &deviceHandle);
		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_Open");
//			fflush(stdout);
			return status;
		}
//		printf("SiLabsC8051F320.c: device opened, device handle=%d\n",deviceHandle);
		wrapAdd=0; // reset timestamps
		lastTimeCheckedDeviceThere=GetTickCount();
		lastTimeGotEvent=GetTickCount();
		return flushBuffers();
    
	}else{
		return 0; // device opened already, have a device handle
	}
}

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeOpen
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeOpen__I
(JNIEnv *env, jobject obj, jint devNum){
	deviceNum=devNum;
	// start actual use of functions from library, finally!!!
	if(deviceHandle==NULL){ // we only open device once
		//printf("SiLabsC8051F320.c: finding number of attached devices\n");
		status=(SI_GetNumDevices)(&numDevices);
		if(status!=SI_SUCCESS){
			printf("SiLabsC8051F320.c: error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
			if(status==0xff){printf("SiLabsC8051F320.c: SI_DEVICE_NOT_FOUND \n(USBXPress Device driver not installed, cable not connected, device not powered?)\n");}
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_GetNumDevices\n");
			fflush(stdout);
			return status;
		}	
		if(numDevices==0){
//			printf("SiLabsC8051F320.c: error SI_GetNumDevices no devices\n");
			fflush(stdout);
			return USBAEMON_NO_DEVICES;
		}
		//printf("SiLabsC8051F320.c: %d device(s) found, choosing first one\n",numDevices);
		fflush(stdout);

		deviceString[0]=0;
		status=(SI_GetProductString)(deviceNum,deviceString,SI_RETURN_DESCRIPTION);
		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_GetProductString\n");
			fflush(stdout);
			return status;
		}
//		printf("SiLabsC8051F320.c: USB AE Mon product string: %s\n",deviceString); // this never returns something sensible
//		fflush(stdout);
		
		status = (SI_SetTimeouts)(TIMEOUT_MS_READ,TIMEOUT_MS_WRITE); 

		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_SetTimeouts");
			fflush(stdout);
			return status;
		}
		//printf("SiLabsC8051F320.c: setTimeouts OK\n");

		status = (SI_Open)(deviceNum, &deviceHandle);
		if(status!=SI_SUCCESS){
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_Open");
			fflush(stdout);
			return status;
		}
//		printf("SiLabsC8051F320.c: device opened, device handle=%d\n",deviceHandle);
		wrapAdd=0; // reset timestamps
		lastTimeCheckedDeviceThere=GetTickCount();
		return flushBuffers();
    
	}else{
		return 0; // device opened already, have a device handle
	}
}

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeGetNumDevices
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeGetNumDevices
(JNIEnv *env, jobject obj){
	status=(SI_GetNumDevices)(&numDevices);
//	if(status!=SI_SUCCESS){
//		printf("SiLabsC8051F320.c: error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
//		onError(status);
//		fflush(stdout);
//		return USBAEMON_NUM_DEVICES_ERROR;
//	}
	return numDevices; // we could return more than one device, but only one can actually be accessed at a time from a single JVM
}


/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeClose
(JNIEnv *env, jobject obj){
	closeDevice();
	return 0;
}

/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    getNumEventsAcquired
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeGetNumEventsAcquired
(JNIEnv *env, jobject obj){
	return (jint)numEvents;
}


/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    acquireAvailableEventsFromDriver
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeAcquireAvailableEventsFromDriver
(JNIEnv *env, jobject obj){
	unsigned int ui;
 	int eventCounter;
 	unsigned short addr,*aePtr;
	unsigned short shortts;	// raw 16 bit timestamp from device
	unsigned int *tsPtr;	// pointer to 32 bit returned timetamp arrar
	//int readyToRead;	// for rcv queue
	//int bytes_to_copy;	

	DWORD bytesRead; // unsigned long
	DWORD bytesInQueue;
	DWORD queueStatus; // ,bytesWritten;

	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;
	// DLL is left resident and device is left open between native method calls

    status=checkStillThere();
	if(status!=0) return status;

	// we read all bytes available *if* driver says its ok to read anything
	// if driver isn't ready to read, we just return 0 events
	// if there is an overrun just detect it and return the events that have been stored

	status=(SI_CheckRXQueue)(deviceHandle,&bytesInQueue,&queueStatus);
	if(status!=SI_SUCCESS){
//	 	printf("SiLabsC8051F320.c: error SI_CheckRXQueue: status=0x%x queueStatus=0x%x\n",status,queueStatus);
		onError(status);
//		printf("SiLabsC8051F320.c: error SI_CheckRXQueue");
		fflush(stdout);
		return status;
	}

	overrunOccured=queueStatus&SI_RX_OVERRUN?JNI_TRUE:JNI_FALSE;
	
	// read events
	//SI_STATUS SI_Read (HANDLE Handle, LPVOID Buffer, DWORD NumBytesToRead,DWORD*NumBytesReturned)
	bytesRead=0;

	// if there is an overrun, then bytesInQueue is set to 0. we set it to max anyhow to get all available events

	bytesInQueue=overrunOccured?SI_MAX_READ_SIZE:bytesInQueue;
	status=(SI_Read)(deviceHandle,aeBuffer,bytesInQueue,&bytesRead);
	if(status!=SI_SUCCESS){
		if(status&SI_RX_QUEUE_NOT_READY) {
			numEvents=0;
			return 0;
		}else{
//			printf("SiLabsC8051F320.c: error reading bytes: status=%d, bytesInQueue=%d\n",(int)status,(int)bytesInQueue);
			onError(status);
//			printf("SiLabsC8051F320.c: error SI_Read\n");
			numEvents=0;
			fflush(stdout);
			return status;
		}
	}

	if(overrunOccured) flushBuffers(); // clears the flag

//		printf("SiLabsC8051F320.c: read %d bytes\n",bytesRead);
//		for(ui=0;ui<bytesRead;ui++){
//			printf("SiLabsC8051F320.c: %d,",(int)aeBuffer[ui]);
//		}
//		printf("SiLabsC8051F320.c: \n");
//	printf("SiLabsC8051F320.c: %d bytes read\n",(int)bytesRead);

	if (bytesRead%4!=0){
		printf("SiLabsC8051F320.c: read %d bytes, which is not divisible by 4, something's wrong, closing\n", bytesRead);
		closeDevice();
		printf("SiLabsC8051F320.c: error in reading events");
		numEvents=0;
		fflush(stdout);
		return status;
	}
	if(bytesInQueue>0){
		lastTimeGotEvent=GetTickCount();
	}

	/* 
	First, grab bytes from driver, unpack addresses and timestamps (unwrapping these) into local static buffers.

	ae and timestamps are sent from USB device in BIG-ENDIAN format. MSB comes first,
	The addresses are simply copied over, and the timestamps are unwrapped to make uint32 timestamps.
	wrapping is detected when the present timestamp is less than the previous one.
	then we assume the counter has wrapped--but only once--and add into this and subsequent
	timestamps the wrap value of 2^16. this offset is maintained and increases every time there 
	is a wrap. hence it integrates the wrap offset.
	the timestamp is returned in 1us ticks, although the microcontroller uses 2us ticks.
	this conversion is to make values more compatible with other CAVIAR software components.
	
	from http://www.codeproject.com/cpp/endianness.asp
	Little-endian: Least significant byte is stored at the lowest byte address.
	The Intel x86 family and Digital Equipment Corporation architectures (PDP-11, VAX, Alpha) 
	are representatives of Little-Endian.

	8051 Keil compiler, however, is big-endian.
	*/
	eventCounter=0;
	aePtr=addressBuffer;
	tsPtr=timestampBuffer;
	for(ui=0;ui<bytesRead;ui+=4){
		addr=aeBuffer[ui+1]+((aeBuffer[ui])<<8); // remember addr is sent big-endian
		aePtr[eventCounter]=addr;	// simply copy address
		{	// if we're returning timestamps
			shortts=aeBuffer[ui+3]+((aeBuffer[ui+2])<<8); // this is 16 bit value of timestamp in 2us tick
			if(addr==0xFFFF){ // changed to handle this address as special wrap event // if(shortts<lastshortts){
				wrapAdd+=0x10000;	// if we wrapped then increment wrap value by 2^16
//				printf("wrapAdd=%d\n",wrapAdd); fflush(stdout);
				continue; // skip timestamp and continue to next address without incrementing eventCounter
//					printf("SiLabsC8051F320.c: event %d wraps: lastshortts=0x%X, shortts=0x%X, wrapAdd now=0x%X, final ts/2=0x%X, final ts=0x%X\n",
//						eventCounter+1,lastshortts,shortts,wrapAdd,(shortts+wrapAdd),(shortts+wrapAdd)*2);
			}
			tsPtr[eventCounter]=(shortts+wrapAdd)*TICK_US; //add in the wrap offset and convert to 1us tick
			lastshortts=shortts;	// save last timestamp
		}
		eventCounter++;
	}
//	printf("SiLabsC8051F320.c: %d events\n",eventCounter);
	numEvents=eventCounter;

	return numEvents; // successful
		
}	// end of mexFunction

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeResetTimestamps
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeResetTimestamps
(JNIEnv *env, jobject o){
	int bytesWritten;
	wrapAdd=0;
	lastshortts=0;
	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	status=checkStillThere();
	if(status!=0) return SI_DEVICE_NOT_FOUND;

	//printf("SiLabsC8051F320: nativeSetPowerdown called\n");
	//fflush(stdout);
	commandBuffer[0]=CMD_RESETTIMESTAMPS;
	status=(SI_Write)(deviceHandle,commandBuffer,1,&bytesWritten);
	if(status!=SI_SUCCESS || bytesWritten!=1 ){
		onError(status);
		return status;
	}
	return status;
}

//////////// methods for biasgen control 

/*
	SI_USB_XP_API 
SI_STATUS WINAPI SI_Write(
	HANDLE cyHandle,
	LPVOID lpBuffer,
	DWORD dwBytesToWrite,
	LPDWORD lpdwBytesWritten
	);
*/

// for simplicity, there is no facility to transmit 
// more than a single 64 byte message at present. this will need to be fixed when
// we have systems with more than 62 bytes of bias (2 bytes used for message header).

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeSetPowerdown
 * Signature: (Z)I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeSetPowerdown
(JNIEnv *env, jobject o, jboolean b){
	SI_STATUS status;
	DWORD bytesWritten;

	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	status=checkStillThere();
	if(status!=0) return status;

	//printf("SiLabsC8051F320: nativeSetPowerdown called\n");
	//fflush(stdout);
	commandBuffer[0]=BIAS_SETPOWER;
	commandBuffer[1]=(unsigned char)b;
	status=(SI_Write)(deviceHandle,commandBuffer,2,&bytesWritten);
	if(status!=SI_SUCCESS || bytesWritten!=2 ){
		onError(status);
		return status;
	}
	return SI_SUCCESS;
}

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeSendBiases
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeSendBiases
  (JNIEnv *env, jobject o, jbyteArray bytes){
	SI_STATUS status;
	DWORD bytesWritten;
	jint length;
	jbyte *biasBytes;
	int i;

	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	
	status=checkStillThere();
	if(status!=0) return status;

	length=(*env)->GetArrayLength(env,bytes); // get number of bytes
	biasBytes=(*env)->GetByteArrayElements(env,bytes,0); // get the bytes, don't make copy of them
	commandBuffer[0]=BIAS_BIASES;  // this is command
	commandBuffer[1]=(unsigned char)length;	// second byte is number of bytes
	for(i=0;i<length;i++){
		commandBuffer[i+2]=biasBytes[i];
	}
	//printf("sending biases: length=%d\n",length);
	//fflush(stdout);
	status=(SI_Write)(deviceHandle,commandBuffer,(DWORD)(2+length),&bytesWritten);
	if(status!=SI_SUCCESS){ // || bytesWritten!=(DWORD)(2+length) 
		printf("SiLabsC8051F320.c: nativeSendBiases: tried to write %d bytes, but %d bytes actually written, status=0x%x\n",length,bytesWritten,status);
		fflush(stdout);
		onError(status);
		return status;
	}
	return SI_SUCCESS;

}


/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeFlashBiases
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeFlashBiases
(JNIEnv *env, jobject o, jbyteArray bytes){
	SI_STATUS status;
	DWORD bytesWritten;
	jint length;
	jbyte *biasBytes;
	int i;

	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	status=checkStillThere();
	if(status!=0) return status;

	length=(*env)->GetArrayLength(env,bytes); // get number of bytes
	biasBytes=(*env)->GetByteArrayElements(env,bytes,0); // get the bytes, don't make copy of them
	commandBuffer[0]=BIAS_FLASH;
	commandBuffer[1]=(unsigned char)length;	// second byte is number of bytes
	for(i=0;i<length;i++){
		commandBuffer[i+2]=biasBytes[i];
	}
	status=(SI_Write)(deviceHandle,commandBuffer,(DWORD)(2+length),&bytesWritten);
	if(status!=SI_SUCCESS  ){ // || bytesWritten!=1
		onError(status);
		return status;
	}
	return SI_SUCCESS;

}

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeSetEventAcquisitionEnabled
 * Signature: (Z)I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeSetEventAcquisitionEnabled
(JNIEnv *env, jobject o, jboolean enabled){
	SI_STATUS status;
	DWORD bytesWritten;
	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	status=checkStillThere();
	if(status!=0) return status;

	commandBuffer[0]=AE_EVENT_ACQUISITION_ENABLED;
	commandBuffer[1]=(unsigned char)enabled;
	status=(SI_Write)(deviceHandle,commandBuffer,(DWORD)2,&bytesWritten);
	if(status!=SI_SUCCESS ){ //  || bytesWritten!=2
		onError(status);
		return status;
	}
	return SI_SUCCESS;
}

/*
 * Class:     ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320
 * Method:    nativeSetServo
 * Signature: (IS)I
 */
JNIEXPORT jint JNICALL Java_net_sf_jaer_hardwareinterface_usb_silabs_SiLabsC8051F320_nativeSetServo
(JNIEnv *env, jobject obj, jint servo, jshort value){
	SI_STATUS status;
	DWORD bytesWritten;
	unsigned short v;
	if(deviceHandle==NULL) return USBAEMON_DEVICE_NOT_OPEN;

	v=(unsigned short)(value & 0xffffffff);

	status=checkStillThere();
	if(status!=0) return status;

	// the message consists of
	// msg header: the command (1 byte)
	// servo to control, 1 byte
	// servo PWM PCA capture-compare register value, 2 bytes, this encodes the LOW time of the PWM output
	// 				this is send MSB, then LSB (big endian)

	commandBuffer[0]=CMD_SETSERVO;
	commandBuffer[1]=(unsigned char)servo;
	commandBuffer[2]=(unsigned char)((value>>8));  // first send MSB
	commandBuffer[3]=(unsigned char)((value&0xff)); // then LSB
	status=(SI_Write)(deviceHandle,commandBuffer,(DWORD)4,&bytesWritten);
	if(status!=SI_SUCCESS ){ //  || bytesWritten!=3
		onError(status);
		return status;
	}
	return SI_SUCCESS;
}



void throwHardwareInterfaceException(JNIEnv *env, jobject o, char *msg){
	jclass c;
	c=(*env)->FindClass(env,"ch/unizh/ini/caviar/hardwareinterface/HardwareInterfaceException");
	if(c==NULL){
		printf("SiLabsC8051F320: can't find exception class to throw exception with msg %s\n",msg);
		fflush(stdout);
		return;
	}
	(*env)->ThrowNew(env,c,"msg");
}


static void closeDevice(void)
{
  SI_STATUS status;
  //printf("SiLabsC8051F320.c: calling SI_Close\n");
  fflush(stdout);
  if(deviceHandle!=NULL) {
  		status = (SI_Close)(deviceHandle);
		if(status!=SI_SUCCESS){
			printf("SiLabsC8051F320.c: error SI_Close, returned status=0x%x\n",status);
			deviceHandle=NULL;
			fflush(stdout);
		}
		//printf("SiLabsC8051F320.c: SI_Close successful\n");
		fflush(stdout);
  }else{
	  printf("SiLabsC8051F320.c: closeDevice(): device not open\n");
	  fflush(stdout);
  }
	deviceHandle=NULL;
}

// utilities

/* Here is the error function, which gets run when the MEX-file is
   cleared and when the user exits MATLAB. The mexAtExit function
   should always be declared as static. */
// prints an error message and closes device
void onError(SI_STATUS status){

	printf("SiLabsC8051F320.c: error status=0x%x\n",status);
	printf("SI_SUCCESS 0x00\n");
	printf("SI_DEVICE_NOT_FOUND 0xFF\n");
	printf("SI_INVALID_HANDLE 0x01\n");
	printf("SI_READ_ERROR 0x02\n");
	printf("SI_RX_QUEUE_NOT_READY 0x03\n");
	printf("SI_WRITE_ERROR 0x04\n");
	printf("SI_RESET_ERROR 0x05\n");
	printf("SI_INVALID_PARAMETER 0x06\n");
	printf("SI_INVALID_REQUEST_LENGTH 0x07\n");
	printf("SI_DEVICE_IO_FAILED 0x08\n");
	printf("SI_INVALID_BAUDRATE	 0x09\n");
	fflush(stdout);

	closeDevice();
}

// this resets overrun flag, according to reply to post to si labs usb user forum

SI_STATUS flushBuffers(){
	SI_STATUS status;
	status = (SI_FlushBuffers)(deviceHandle,1,1); // flush both TX and RX buffers
	if(status!=SI_SUCCESS){
//		printf("SiLabsC8051F320.c: error SI_FlushBuffers, SI_STATUS=%d\n",status);
		onError(status);
//		printf("SiLabsC8051F320.c: error SI_FlushBuffers\n");
//		fflush(stdout);
	}
//	wrapAdd=0;	// we used to reset timestamps but don't anymore, because we probably are just clearing overrun flag
	return status;
}

#ifdef __cplusplus
}
#endif
