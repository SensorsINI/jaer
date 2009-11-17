
// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the SI_USB_XP_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// SI_USB_XP_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef SI_USB_XP_EXPORTS
#define SI_USB_XP_API __declspec(dllexport)
#else
#define SI_USB_XP_API __declspec(dllimport)
#endif

 
// Return codes
#define		SI_SUCCESS					0x00
#define		SI_DEVICE_NOT_FOUND			0xFF
#define		SI_INVALID_HANDLE			0x01
#define		SI_READ_ERROR				0x02
#define		SI_RX_QUEUE_NOT_READY		0x03
#define		SI_WRITE_ERROR				0x04
#define		SI_RESET_ERROR				0x05
#define		SI_INVALID_PARAMETER		0x06
#define		SI_INVALID_REQUEST_LENGTH	0x07
#define		SI_DEVICE_IO_FAILED			0x08
#define		SI_INVALID_BAUDRATE			0x09
#define		SI_FUNCTION_NOT_SUPPORTED	0x0a
#define		SI_GLOBAL_DATA_ERROR		0x0b
#define		SI_SYSTEM_ERROR_CODE		0x0c
#define		SI_READ_TIMED_OUT			0x0d
#define		SI_WRITE_TIMED_OUT			0x0e
#define		SI_IO_PENDING				0x0f

// GetProductString() function flags
#define		SI_RETURN_SERIAL_NUMBER		0x00
#define		SI_RETURN_DESCRIPTION		0x01
#define		SI_RETURN_LINK_NAME			0x02
#define		SI_RETURN_VID				0x03
#define		SI_RETURN_PID				0x04

// RX Queue status flags
#define		SI_RX_NO_OVERRUN			0x00
#define		SI_RX_EMPTY					0x00
#define		SI_RX_OVERRUN				0x01
#define		SI_RX_READY					0x02

// Buffer size limits
#define		SI_MAX_DEVICE_STRLEN		256
#define		SI_MAX_READ_SIZE			4096*16
#define		SI_MAX_WRITE_SIZE			4096

// Type definitions
typedef		int		SI_STATUS;
typedef		char	SI_DEVICE_STRING[SI_MAX_DEVICE_STRLEN];

// Input and Output pin Characteristics
#define		SI_HELD_INACTIVE			0x00
#define		SI_HELD_ACTIVE				0x01
#define		SI_FIRMWARE_CONTROLLED		0x02		
#define		SI_RECEIVE_FLOW_CONTROL		0x02
#define		SI_TRANSMIT_ACTIVE_SIGNAL	0x03
#define		SI_STATUS_INPUT				0x00
#define		SI_HANDSHAKE_LINE			0x01

// Mask and Latch value bit definitions
#define		SI_GPIO_0					0x01
#define		SI_GPIO_1					0x02
#define		SI_GPIO_2					0x04
#define		SI_GPIO_3					0x08

// GetDeviceVersion() return codes
#define		SI_CP2101_VERSION			0x01
#define		SI_CP2102_VERSION			0x02
#define		SI_CP2103_VERSION			0x03


#ifdef __cplusplus
extern "C" {
#endif

SI_USB_XP_API
SI_STATUS WINAPI SI_GetNumDevices(
	LPDWORD lpdwNumDevices
	);

SI_USB_XP_API
SI_STATUS WINAPI SI_GetProductString(
	DWORD dwDeviceNum,
	LPVOID lpvDeviceString,
	DWORD dwFlags
	);

SI_USB_XP_API
SI_STATUS WINAPI SI_Open(
	DWORD dwDevice,
	HANDLE* cyHandle
	); 

SI_USB_XP_API
SI_STATUS WINAPI SI_Close(
	HANDLE cyHandle
	);

SI_USB_XP_API
SI_STATUS WINAPI SI_Read(
	HANDLE cyHandle,
	LPVOID lpBuffer,
	DWORD dwBytesToRead,
	LPDWORD lpdwBytesReturned,
	OVERLAPPED* o // tobi had to remove =NULL to compile with C
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_Write(
	HANDLE cyHandle,
	LPVOID lpBuffer,
	DWORD dwBytesToWrite,
	LPDWORD lpdwBytesWritten,
	OVERLAPPED* o // tobi had to remove =NULL
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_DeviceIOControl(
	HANDLE cyHandle,
	DWORD dwIoControlCode,
	LPVOID lpInBuffer,
	DWORD dwBytesToRead,
	LPVOID lpOutBuffer,
	DWORD dwBytesToWrite,
	LPDWORD lpdwBytesSucceeded
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_FlushBuffers(
	HANDLE cyHandle, 
	BYTE FlushTransmit,
	BYTE FlushReceive
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_SetTimeouts(
	DWORD dwReadTimeout,
	DWORD dwWriteTimeout
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_GetTimeouts(
	LPDWORD lpdwReadTimeout,
	LPDWORD lpdwWriteTimeout
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_CheckRXQueue(
	HANDLE cyHandle,
	LPDWORD lpdwNumBytesInQueue,
	LPDWORD lpdwQueueStatus
	);

SI_USB_XP_API
SI_STATUS	WINAPI SI_SetBaudRate(
	HANDLE cyHandle,
	DWORD dwBaudRate
	);

SI_USB_XP_API
SI_STATUS	WINAPI SI_SetBaudDivisor(
	HANDLE cyHandle,
	WORD wBaudDivisor
	);

SI_USB_XP_API
SI_STATUS	WINAPI SI_SetLineControl(
	HANDLE cyHandle, 
	WORD wLineControl
	);

SI_USB_XP_API
SI_STATUS	WINAPI SI_SetFlowControl(
	HANDLE cyHandle, 
	BYTE bCTS_MaskCode, 
	BYTE bRTS_MaskCode, 
	BYTE bDTR_MaskCode, 
	BYTE bDSR_MaskCode, 
	BYTE bDCD_MaskCode, 
	BOOL bFlowXonXoff
	);

SI_USB_XP_API
SI_STATUS WINAPI SI_GetModemStatus(
	HANDLE cyHandle, 
	PBYTE ModemStatus
	);

SI_USB_XP_API
SI_STATUS WINAPI SI_SetBreak(
	HANDLE cyHandle, 
	WORD wBreakState
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_ReadLatch(
	HANDLE cyHandle,
	LPBYTE	lpbLatch
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_WriteLatch(
	HANDLE cyHandle,
	BYTE	bMask,
	BYTE	bLatch
	);


SI_USB_XP_API 
SI_STATUS WINAPI SI_GetPartNumber(
	HANDLE cyHandle,
	LPBYTE	lpbPartNum
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_GetDLLVersion(
	DWORD* HighVersion,
	DWORD* LowVersion
	);

SI_USB_XP_API 
SI_STATUS WINAPI SI_GetDriverVersion(
	DWORD* HighVersion,
	DWORD* LowVersion
	);

#ifdef __cplusplus
}
#endif
