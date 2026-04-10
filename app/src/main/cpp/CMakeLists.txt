cmake_minimum_required(VERSION 3.22.1)
project("columba")

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(file-transfer-protocol)

add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
    file-transfer-protocol
    android
    log)