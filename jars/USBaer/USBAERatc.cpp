#include <jni.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <Windows.h>
#include "USBAERatc.h"


HANDLE	hDevice = INVALID_HANDLE_VALUE;
HANDLE OpenDevice(const char* Str);


JNIEXPORT jboolean JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeOpen(JNIEnv *env, jobject obj, jstring device, jstring path)
{
	
	const char *specifiedDevice, *devicePath;
	specifiedDevice=(*env).GetStringUTFChars(device,NULL);
	devicePath=(*env).GetStringUTFChars(path,NULL);


	if(specifiedDevice==NULL || devicePath==NULL){
		return JNI_FALSE;
	}


      	hDevice = OpenDevice(specifiedDevice);
        FILE *fileinput;
        unsigned long count = 0;
        unsigned long nWrite;
        unsigned long longitud, longtoload;
        char buf[16384];
	//	char buf2[8];
        int i;
        unsigned int paso=16384;
        if (hDevice == INVALID_HANDLE_VALUE)
		{
			return JNI_FALSE;
		}
		else
		{

			fileinput = fopen(devicePath ,"rb");
			
            if(fileinput) {

			fseek(fileinput,0,SEEK_END);
            longitud = ftell(fileinput);
            fseek(fileinput,0,SEEK_SET);

            buf[0]='A';
            buf[1]='T';
            buf[2]='C';
            buf[3]= 0;   // comando

            for(i=4;i<8;i++)
                    buf[i]=(longitud >>(8*(i-4)))&0xff;

			WriteFile(hDevice, buf,(unsigned long) 64, &nWrite, NULL);


		    /*count = fread(buf, sizeof(char), longitud, fileinput);

		    longtoload=64*(ceilf(longitud/64));

			if(nWrite != 64)
			{
				return JNI_FALSE;
			}
			else
			{
                for(i=0;i<longtoload;i=i+paso)
                {
                    WriteFile(hDevice, buf+i, (unsigned long)paso, &nWrite, NULL);
                }
            }*/
			while(!feof(fileinput))
                        {
                         count += (unsigned long)fread( buf, sizeof( char ), paso, fileinput );
	                     WriteFile(hDevice, buf, (unsigned long)paso, &nWrite, NULL);
                        if(nWrite != paso)
                                {return JNI_FALSE;}
                         }    
        }

		fclose(fileinput);
		CloseHandle(hDevice);
		env->ReleaseStringUTFChars(device,specifiedDevice);
		env->ReleaseStringUTFChars(path,devicePath);

	}
		//(*env).SetBooleanArrayRegion(result,0,10,tmp);
		//return result;

		return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeUpload(JNIEnv *env, jobject, jstring device, jstring path, jboolean SelMapper, jboolean SelDatalogger, jboolean SelOthers, jlong inicio)//inicio -> 0 normalmente
{
	const char *specifiedDevice, *devicePath;
	specifiedDevice=(*env).GetStringUTFChars(device,NULL);
	devicePath=(*env).GetStringUTFChars(path,NULL);

	if(specifiedDevice==NULL || devicePath==NULL){
		return;
	}

	hDevice = OpenDevice(specifiedDevice);
    FILE *fileinput;
    unsigned long count = 0;
    unsigned long counta = 0;
    unsigned long nWrite;
    unsigned long longitud,longtoload;
    char buf[262144];
    int i;
    unsigned int paso = 262144;

    if (hDevice == INVALID_HANDLE_VALUE)
	{
		return;
	}
	else
	{
        fileinput = fopen(devicePath ,"rb");
        if(fileinput) {

                fseek(fileinput,0,SEEK_END);
                longitud = ftell(fileinput);
                fseek(fileinput,0,SEEK_SET);

                buf[0]='A';
                buf[1]='T';
                buf[2]='C';
                buf[3]= 1;   // comando 1 es grabar RAM
                for(i=4;i<8;i++)
                        buf[i]=(longitud>>(8*(i-4)))&0xff;

                // Los siguientes bytes son los comandos que va a recibir la FPGA
                //if (SelMapper)
                   for(i=8;i<12;i++)
                        buf[i]=(inicio>>(8*(i-8)))&0xff;
                
                /*if (SelDatalogger)
                {  
					buf[8]=4;  // Comando de habilitación de escritura
                    buf[9]=3;  // Subcomando de establec. de la direcc
                    for(i=10;i<14;i++)
                        buf[i]=(inicio>>(8*(i-8)))&0xff;
                }*/

				//temporalmente comentado, desbloquearlo para su futuro uso
                /*if (SelOthers)
                {  
					for(i=8;i<24;i++)
                        buf[i]=commands[i-8];
                }*/

                WriteFile(hDevice, buf,(unsigned long) 64, &nWrite, NULL);

				if(nWrite != 64)
				{
					return;
				}
                else
                {
                    while(!feof(fileinput))
                    {
						count = fread(buf, sizeof( char ), paso, fileinput);
						longtoload=(unsigned long)64*(ceilf((float)count/64));
						counta=counta+count;
						WriteFile(hDevice, buf, (unsigned long)longtoload, &nWrite, NULL);
                    }
                }
         }
         fclose(fileinput);
         CloseHandle(hDevice);
	}
	return;
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeSend(JNIEnv *, jobject, jstring device)
{
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeReceive(JNIEnv *, jobject, jstring device)
{
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeDownloadFromMapper(JNIEnv *, jobject, jstring device)
{
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeSendDesc(JNIEnv *, jobject, jstring device)
{
}

JNIEXPORT void JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeSendCommand(JNIEnv *, jobject, jstring device)
{
}

JNIEXPORT jboolean JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativePrueba(JNIEnv *env, jobject, jstring dev)
{
	const char *specifiedDevice;
	specifiedDevice=(*env).GetStringUTFChars(dev,NULL);

	if(specifiedDevice==NULL){
		return JNI_FALSE;
	}

	bool result;
	hDevice = OpenDevice(specifiedDevice);
	if(hDevice==INVALID_HANDLE_VALUE) 
		result=false;
	else 
		result=true;
	CloseHandle(hDevice);
	return (jboolean)result;
}

HANDLE OpenDevice(const char* Str)
	{	
		LPCWSTR DeviceName;
		HANDLE Hnd;

		int a = lstrlenA(Str);
		BSTR unicodestr = SysAllocStringLen(NULL, a);
		::MultiByteToWideChar(CP_ACP, 0, Str, a, unicodestr, a);
		
		DeviceName = unicodestr;
		Hnd = CreateFile(DeviceName, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL,NULL);

		return Hnd;
	}


/*JNIEXPORT jboolean JNICALL
Java_ch_unizh_ini_caviar_hardwareinterface_usb_USBAERatc_nativeOpen(JNIEnv *env, jobject obj, jstring device, jbyteArray data, jint longitud)
{
	MessageBoxA(NULL,"entré aunque no lo parezca",NULL,MB_OK);
	const char *specifiedDevice, *devicePath;
	specifiedDevice=(*env).GetStringUTFChars(device,NULL);

	if(specifiedDevice==NULL){
		return JNI_FALSE;
	}


      	hDevice = OpenDevice(specifiedDevice);
        FILE *fileinput;
        unsigned long count = 0;
        unsigned long nWrite;
        unsigned float longtoload;
        char buf[167040];
		jbyte *temp;
		

		env->GetByteArrayRegion(data,0,longitud,temp);

		char* c;
		strcat(c,"jbyte*: ");

		for(int i=0;i<longitud;i++){
			char t=temp[i];
			char tm[1];
			tm[0]=t;
			strcat(c,tm);
			strcat(c,"; ");
		}

		MessageBoxA(NULL,c,NULL,MB_OK);		


		for(int i=0;i<longitud;i++)
			buf[i]=temp[i];

		char* c2;
		strcat(c2,"char*: ");

		for(int i=0;i<longitud;i++){
			char t=buf[i];
			char tm[1];
			tm[0]=t;
			strcat(c2,tm);
			strcat(c2,"; ");
		}

		MessageBoxA(NULL,c2,NULL,MB_OK);	

		//for(int i=0;i<longitud;i++)
		//	strcat(c,buf[i]);

		//MessageBoxA(NULL,c,NULL,MB_OK);

		char buf2[8];
        int i;
        unsigned int paso=16384;
        if (hDevice == INVALID_HANDLE_VALUE)
		{
			return JNI_FALSE;
		}
		else
		{
            buf2[0]='A';
            buf2[1]='T';
            buf2[2]='C';
            buf2[3]= 0;   // comando

            for(i=4;i<8;i++)
                    buf2[i]=(longitud >>(8*(i-4)))&0xff;

			WriteFile(hDevice, buf2,(unsigned long) 64, &nWrite, NULL);

		    longtoload=64*(ceilf(longitud/64));

			if(nWrite != 64)
			{
				return JNI_FALSE;
			}
			else
			{
                for(i=0;i<longtoload;i=i+paso)
                {
                    WriteFile(hDevice, buf+i, (unsigned long)paso, &nWrite, NULL);
                }
            }
        }


		CloseHandle(hDevice);
		return JNI_TRUE;
}
*/