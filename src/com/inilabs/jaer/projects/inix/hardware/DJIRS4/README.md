![DJIR_SDK](@Docs/DJIR_SDK_LOGO.png)

## OVERVIEW

The **DJIR Software Development Kit** is developed based on DJI RS 2 and DJI R SDK protocol v2.2. By using this SDK and connected via [USBCAN-II C](https://github.com/ConstantRobotics/USBCAN_SDK) (by Shenyang Guangcheng Technology Co.,Ltd), users can control the handheld gimbal device movement and obtain its partial information.

## DEVICE CONNECTION DIAGRAM

Below shows how DJI RS 2 connects to a PC via the CAN converter:

![DJIR_SDK](@Docs/Device_Connection_Diagram.png)


## GETTING STARTED
For developers who want to download DJIR SDK from source using the Git-client,
follow these instructions:
### 1. Install the git-client on your local computer (if not already installed):
* On Linux, use the terminal command: `sudo apt install git`
* On MacOS, use the terminal command: `brew install git`
* For other platforms see [git installation documentation](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git/).
### 2. Open a command prompt/terminal on your computer:
* On Linux, click on the launchpad and look for «terminal» `terminal`
* In OS X, press commandspace and find «terminal» `terminal`
* On Windows, click the Start menu and find the «command line» `cmd`.
### 3. Download the project:
```bash
git clone https://github.com/ConstantRobotics/DJIR_SDK.git
cd DJIR_SDK
git submodule update --init --recursive
```
> We recommend to use a git client for downloading and Qt Creator for project building

## API DESCRIPTION
The **DJIR_SDK** contains a description of just one class `DJIRonin` with simple interface for easy use:
```c++
class DJIRonin
{
public:
    DJIRonin();
    ~DJIRonin();

    /**
     * @brief connect - Connect to DJI Ronin device
     * @return True if success
     */
    bool connect();
    /**
     * @brief disconnect - Disconnect from DJI Ronin device
     * @return True if success
     */
    bool disconnect();

    /**
     * @brief move_to - Handheld Gimbal Position Control (p.5, 2.3.4.1)
     * @param yaw Yaw angle, unit: 0.1° (range: -1800 to +1800)
     * @param roll Roll angle, unit: 0.1° (range: -1800 to +1800)
     * @param pitch Pitch angle, unit: 0.1° (range: -1800 to +1800)
     * @param time_ms Command execution speed, unit: ms. Min value is 100ms.
     * Time is used to set motion speed when gimbal is executing this command.
     * @return True if success
     */
    bool move_to(int16_t yaw, int16_t roll, int16_t pitch, uint16_t time_ms);

    /**
     * @brief set_speed - Handheld Gimbal Speed Control (p.6, 2.3.4.2)
     * @param yaw Unit: 0.1°/s (range: 0°/s to 360°/s)
     * @param roll Unit: 0.1°/s (range: 0°/s to 360°/s)
     * @param pitch Unit: 0.1°/s (range: 0°/s to 360°/s)
     * @return True if success
     */
    bool set_speed(uint16_t yaw, uint16_t roll, uint16_t pitch);

    /**
     * @brief get_current_position - Get Gimbal Information (p.6, 2.3.4.3)
     * @param yaw Yaw axis angle (unit: 0.1°)
     * @param roll Roll axis angle (unit: 0.1°)
     * @param pitch Pitch axis angle (unit: 0.1°)
     * @return True if success
     */
    bool get_current_position(int16_t& yaw, int16_t& roll, int16_t& pitch);
    
    // Etc...
```

## CREARING C++ PROJECT
Create a new project in **Qt Creator** by CMake with using SDK-sources 
*  Open **File > New File or Project**, select **Qt Console Application** and click **Choose** button
*  Enter project name, Browse project location and click **Next** button
*  Choose **CMake** build system and click **Next** button twice
*  Select one of 64bit compilers (MinGW, MSVC, Clang, etc..), click **Next** button and finish project setup.
*  Download the project:
```bash
git clone https://github.com/ConstantRobotics/DJIR_SDK.git
cd DJIR_SDK
git submodule update --init --recursive
```
*  Modify your **CMakeLists.txt** file according to the example below and update DJIR_SDK_DIR according to your path to **DJIR_SDK** folder:
```cmake
cmake_minimum_required(VERSION 3.13)

###############################################################################
## EXECUTABLE-PROJECT
## name and version
###############################################################################
project(DJIR_Example LANGUAGES CXX)

###############################################################################
## SETTINGS
## basic project settings before use
###############################################################################
set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# creating output directory architecture in accordance with GNU guidelines
set(BINARY_DIR "${CMAKE_BINARY_DIR}")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${BINARY_DIR}/bin")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${BINARY_DIR}/lib")

###############################################################################
## TARGET
## create target and add include path
###############################################################################
# create glob files for *.h, *.cpp
file (GLOB H_FILES   ${CMAKE_CURRENT_SOURCE_DIR}/*.h)
file (GLOB CPP_FILES ${CMAKE_CURRENT_SOURCE_DIR}/*.cpp)
# concatenate the results (glob files) to variable
set  (SOURCES ${CPP_FILES} ${H_FILES})
# create executable from src
add_executable(${PROJECT_NAME} ${SOURCES})

###############################################################################
## INCLUDING SUBDIRECTORIES AND LINK LIBRARIES
## linking all dependencies
###############################################################################
# set DJIR_SDK path variable
set(DJIR_SDK_DIR "../DJIR_SDK")
# add subdirectory of DJIR_SDK
add_subdirectory(${DJIR_SDK_DIR} DJIR_SDK)
target_link_libraries(${PROJECT_NAME} DJIR_SDK)
```
*  Modify your **main.cpp** file according to the example below:

```c++
#include <string>
#include <iostream>

#include "DJIR_SDK.h"
using namespace DJIR_SDK;

int main(void)
{
    std::cout << "###########################################" << std::endl;
    std::cout << "#                                         #" << std::endl;
    std::cout << "#          DJIR-SDK Test v1.0.0           #" << std::endl;
    std::cout << "#                                         #" << std::endl;
    std::cout << "###########################################" << std::endl;

    DJIRonin gimbal = DJIRonin();

    // Connect to DJI Ronin Gimbal
    gimbal.connect();

    // Select ABSOLUTE_CONTROL mode
    gimbal.set_move_mode(MoveMode::ABSOLUTE_CONTROL);

    // Move to center position (yaw = 0, roll = 0, pitch = 0) for 2000ms
    gimbal.move_to(0, 0, 0, 2000);

    int16_t yaw = 0;
    int16_t roll = 0;
    int16_t pitch = 0;
    gimbal.get_current_position(yaw, roll, pitch);
    std::cout <<"yaw = "<<yaw<<" roll = "<<roll<<" pitch = "<<pitch<<std::endl;


    std::cout << "Press any key to continie...";
    getchar();
    return 1;
}
```
*  Select **Debug** or **Release** build type, Run CMake and Run project 
