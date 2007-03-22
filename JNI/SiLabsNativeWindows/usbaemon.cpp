// usbaemon.cpp : Defines the entry point for the DLL application.
//

#include "stdafx.h"

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    return TRUE;
}

/* 
source for USB AE Monitor java native method using simple Sevilla USB AE board and SiLabs USBXPress

  NOTE: file extension MUST BE .cpp and not .c in order to implicitly link to the SI DLL functions!
	
 -tobi feb 2005
 */

/* already included in StdAfx.h

  #include <windows.h>	// include this to define DWORD, etc.
#include "mex.h"
#include "SiUSBXp.h"	// customized version of SILabs header modified for C from C++
*/

#define SLEEP_WAIT_MS	3	// defines sleep time in ms during polling of USB driver for data
#define TIMEOUT_MS		25	// defines timeout for read, write. read will return with available events in this time
							// even if all are not available

// MS VC should compile this ".c" file with the plain C compiler based on ".c" extension, so no extern "C" is needed

// these are declared static to be invisible outside
static void closeDevice(void); // exit handler
static void onError(SI_STATUS);
static void flushBuffers(void);
// deviceHandle is static local var so that mex exit handler can use it to close device	
static HANDLE deviceHandle;	// handle returned to device by SI_Open. used by SI_Close and mexAtExit/CloseStream

#ifdef __cplusplus
extern "C" {
#endif


/*
 * Class:     ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor
 * Method:    nativeUSBAEMon
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_ch_unizh_ini_caviar_usbaemonitor_USBAEMonitor_nativeUSBAEMon
  (JNIEnv *env, jobject obj)
{
	static jfieldID addressesFieldID=NULL, timestampsFieldID=NULL, overrunOccuredFieldID=NULL;

	jboolean joverrunOccure;
	jshortArray jaddresses;
	jintArray jtimestamps;

	unsigned int j;
 	int numEvents,eventCounter;
// 	int bytes_to_copy;
 	unsigned short addr,*aePtr;
	unsigned short shortts;	// raw 16 bit timestamp from device
	unsigned int *tsPtr;	// pointer to 32 bit returned timetamp arrar
	//int readyToRead;	// for rcv queue
	bool overrunOccured=false;

	// static so persistent
	static DWORD deviceNum;	//	usb device number (will almost always be 0)
	//static HMODULE lib;	// DLL SiUSBXp.dll
	static unsigned char aeBuffer[SI_MAX_READ_SIZE]; // static buffer for collected events
	static unsigned int wrapAdd=0;		// static offset to add to spike timestamps
	static unsigned short lastshortts=0;		// holds last 16 bit timestamp of previous event, to detect wrapping

	DWORD	numDevices;
	SI_STATUS status;
	char deviceString[SI_MAX_DEVICE_STRLEN];    
	DWORD bytesRead;
	DWORD bytesInQueue;
	DWORD queueStatus; // ,bytesWritten;

	if(addressesFieldID==NULL){
		jclass clazz=(*env)->GetObjectClass(env,obj);

		addressesFieldID=(*env)->GetFieldID(env,clazz,"addresses","[S");
		if(addressesFieldID==null) return -1;
		timestampsFieldID=(*env)->GetFieldID(env,clazz,"timestamps","[I");
		if(timestampsFieldID==null) return -1;
		overrunOccuredFieldID=(*env)->GetFieldID(env,clazz,"overrunOccured","Z");
		if(overrunOccuredFieldID==null) return -1;

	}

	jaddresses=(*env)->GetObjectField(env,obj,addressesFieldID);
	jtimestamps=(*env)->GetObjectField(env,obj,timestampsFieldID);
	joverrunOccured=(*env)->GetObjectField(env,obj,overrunOccuredFieldID);

	//printf("starting usbaemon\n");
	
	// DLL is left resident and device is left open between native method calls

	// start actual use of functions from library, finally!!!
	if(deviceHandle==NULL){ // we only open device once
		//printf("finding number of attached devices\n");
		status=(SI_GetNumDevices)(&numDevices);
		if(status!=SI_SUCCESS){
			printf("error SI_GetNumDevices, numDevices=%d, SI_STATUS=0x%x\n",numDevices,status);
			if(status==0xff){printf("SI_DEVICE_NOT_FOUND \n(USBXPress Device driver not installed, cable not connected, device not powered?)\n");}
			onError(status);
			printf("error SI_GetNumDevices");
		}	
		if(numDevices==0){
			printf("error SI_GetNumDevices no devices");
		}
//		printf("%d device(s) found, choosing first one\n",numDevices);
		deviceNum=0;	// always choose first device
		

		deviceString[0]=0;
		status=(SI_GetProductString)(deviceNum,deviceString,SI_RETURN_DESCRIPTION);
		if(status!=SI_SUCCESS){
			onError(status);
			printf("error SI_GetProductString");
		}
		printf("USB AE Mon product string: %s\n",deviceString); // this never returns something sensible
			
		status = (SI_SetTimeouts)(TIMEOUT_MS,TIMEOUT_MS); // set readtimeout=100ms, writetimeout to 100ms for recovery from hang
															// write timeout set even though we never write (so far)
		if(status!=SI_SUCCESS){
			onError(status);
			printf("error SI_SetTimeouts");
		}
		//printf("setTimeouts OK\n");

		status = (SI_Open)(0, &deviceHandle);
		if(status!=SI_SUCCESS){
			onError(status);
			printf("error SI_Open");
		}
//		printf("device opened, device handle=%d\n",deviceHandle);
		
		/* Register an exit function. You should only register the
		   exit function after the device has been opened successfully*/
//		mexAtExit(closeDevice);
//		printf("registered onExit function OK\n");

		flushBuffers();
		wrapAdd=0;
		//printf("setTimeouts OK\n");
    
	} // open device

    
    
	// we don't check queue anymore, just read all bytes available *if* driver says its ok to read anything
	// if driver isn't ready to read, we just return 0 events
	// if there is an overrun we don't detect it, we just return the events that have been stored

	status=(SI_CheckRXQueue)(deviceHandle,&bytesInQueue,&queueStatus);
	if(status!=SI_SUCCESS){
	 	printf("error SI_CheckRXQueue: status=%d, queueStatus=%d\n",status,queueStatus);
		onError(status);
		printf("error SI_CheckRXQueue");
	}
	if(queueStatus&SI_RX_OVERRUN){
	 	if(nlhs<3) // only print warning if user doesn't record overrun flag
			mexWarnMsgTxt("Driver event buffer overrun, can only buffer 16k events. Flushing buffers.");
		overrunOccured=true;
		flushBuffers();
		bytesRead=0;
	}else{
	
		// read events
		//SI_STATUS SI_Read (HANDLE Handle, LPVOID Buffer, DWORD NumBytesToRead,DWORD*NumBytesReturned)
		bytesRead=0;
		bytesInQueue=SI_MAX_READ_SIZE; // read all if available, otherwise return all available within timeout
		status=(SI_Read)(deviceHandle,aeBuffer,bytesInQueue,&bytesRead);
		if(status!=SI_SUCCESS){
			if(status&SI_RX_QUEUE_NOT_READY) {
				bytesRead=0;
			}else{
				printf("error reading bytes: status=%d, bytesInQueue=%d\n",(int)status,(int)bytesInQueue);
				onError(status);
				printf("error SI_Read");
			}
		}
	//		printf("read %d bytes\n",bytesRead);
	//		for(j=0;j<bytesRead;j++){
	//			printf("%d,",(int)aeBuffer[j]);
	//		}
	//		printf("\n");
	//	printf("%d bytes read\n",(int)bytesRead);
	}
	if (bytesRead%4!=0){
		printf("read %d bytes, which is not divisible by 4, something's wrong, closing\n", bytesRead);
		closeDevice();
		printf("error in reading events");
	}
	numEvents=bytesRead/4;
//	printf("%d bytesRead, %d events\n",(int)bytesRead,(int)numEvents);
	
//		printf("creating output matrix 2 x %d\n",numEvents);

	// simply assign overrun flag directly
	joverrunOccured=overrunOccured;

	// allocate java arrays to hold the events
	jaddresses=(*env)->NewShortArray(env,numEvents);
	jtimestamps=(*env)->NewIntArray(env,numEvents);
	aePtr=(*env)->GetPrimitiveArrayCritical(env,jaddresses,0);
	tsPtr=(*env)->GetPrimitiveArrayCritical(env,jtimestamps,0);
	if(aePtr==0||tsPtr==0) return -1; // out of memory

	
/*
		plhs[0] = mxCreateNumericMatrix(1,numEvents,mxUINT16_CLASS,mxREAL);
		aePtr = (unsigned short *)mxGetData(plhs[0]);
		if(nlhs>1) {
			plhs[1]=mxCreateNumericMatrix(1,numEvents,mxUINT32_CLASS,mxREAL);
			tsPtr=(unsigned int *)mxGetData(plhs[1]);
		}
		if(nlhs>2) {
			plhs[2]=mxCreateDoubleScalar((double)overrunOccured);
		}
*/	

		/* 
		Populate the real part of the created array.
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
		for(j=0;j<bytesRead;j+=4){
			addr=aeBuffer[j+1]+((aeBuffer[j])<<8); // remember addr is sent big-endian
			aePtr[eventCounter]=addr;	// simply copy address
			if(nlhs==2) {	// if we're returning timestamps
				shortts=aeBuffer[j+3]+((aeBuffer[j+2])<<8); // this is 16 bit value of timestamp in 2us tick
				if(shortts<lastshortts){
					wrapAdd+=0x10000;	// if we wrapped then increment wrap value by 2^16
//					printf("event %d wraps: lastshortts=0x%X, shortts=0x%X, wrapAdd now=0x%X, final ts/2=0x%X, final ts=0x%X\n",
//						eventCounter+1,lastshortts,shortts,wrapAdd,(shortts+wrapAdd),(shortts+wrapAdd)*2);
				}
				tsPtr[eventCounter]=(shortts+wrapAdd)*2; //add in the wrap offset and convert to 1us tick
				lastshortts=shortts;	// save last timestamp
			}
			eventCounter++;
		}
//		printf("%d events\n",eventCounter);

		// release hold on arrays

		(*env)->ReleasePrimitiveArrayCritical(env,jtimestamps,0);
		(*env)->ReleasePrimitiveArrayCritical(env,jaddresses,0);


		return 0;
		
}	// end of mexFunction

/* Here is the error function, which gets run when the MEX-file is
   cleared and when the user exits MATLAB. The mexAtExit function
   should always be declared as static. */
static void closeDevice(void)
{
  SI_STATUS status;
  //printf("calling SI_Close\n");
  if(deviceHandle!=NULL) {
  		status = (SI_Close)(deviceHandle);
		if(status!=SI_SUCCESS){
			printf("error SI_Close: status=%d\n",status);
			onError(status);
			deviceHandle=NULL;
			printf("error SI_Close");
		}
	}
	printf("SI_Close successful\n");
	deviceHandle=NULL;
}

void onError(SI_STATUS status){
	printf("error status=0x%x\n",status);
	printf("#define		SI_SUCCESS				0x00\n");
	printf("#define		SI_DEVICE_NOT_FOUND		0xFF\n");
	printf("#define		SI_INVALID_HANDLE			0x01\n");
	printf("#define		SI_READ_ERROR				0x02\n");
	printf("#define		SI_RX_QUEUE_NOT_READY		0x03\n");
	printf("#define		SI_WRITE_ERROR			0x04\n");
	printf("#define		SI_RESET_ERROR			0x05\n");
	printf("#define		SI_INVALID_PARAMETER		0x06\n");
	printf("#define		SI_INVALID_REQUEST_LENGTH	0x07\n");
	printf("#define		SI_DEVICE_IO_FAILED		0x08\n");
	printf("#define		SI_INVALID_BAUDRATE		0x09\n");
	closeDevice();
}

void flushBuffers(){
	SI_STATUS status;
	status = (SI_FlushBuffers)(deviceHandle,1,1); // flush both TX and RX buffers
	if(status!=SI_SUCCESS){
		printf("error SI_FlushBuffers, SI_STATUS=%d\n",status);
		onError(status);
		printf("error SI_FlushBuffers");
	}
}

#ifdef __cplusplus
}
#endif
