cmake_minimum_required(VERSION 3.4.1)
project(fftProcessor)
include(ExternalProject)
# set(CMAKE_BUILD_TYPE Debug)
# Enable modern C++ features
set(CMAKE_CXX_STANDARD 14)  # C++14 supports std::make_unique natively
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Enable ARM NEON
set(ANDROID_ARM_NEON TRUE)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(ENABLE_NEON ON CACHE INTERNAL "Enable NEON" FORCE)
endif()

# Detect NDK version to apply appropriate 16KB page size flags
if(ANDROID_NDK_MAJOR)
    set(NDK_MAJOR_VERSION ${ANDROID_NDK_MAJOR})
else()
    # Fallback for older NDK versions
    string(REGEX MATCH "r([0-9]+)" NDK_VERSION_MATCH ${ANDROID_NDK})
    if(NDK_VERSION_MATCH)
        set(NDK_MAJOR_VERSION ${CMAKE_MATCH_1})
    else()
        set(NDK_MAJOR_VERSION 0)
    endif()
endif()

# Set 16KB page size alignment flags based on NDK version
if(NDK_MAJOR_VERSION GREATER_EQUAL 28)
    # NDK r28+ compiles 16KB-aligned by default
    message(STATUS "Using NDK r${NDK_MAJOR_VERSION} - 16KB alignment is default")
elseif(NDK_MAJOR_VERSION EQUAL 27)
    # NDK r27 needs explicit flag
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    message(STATUS "Using NDK r27 - Adding 16KB page size flags")
elseif(NDK_MAJOR_VERSION GREATER 22 AND NDK_MAJOR_VERSION LESS 27)
    # NDK r23-r26 needs max-page-size flag
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    message(STATUS "Using NDK r${NDK_MAJOR_VERSION} - Adding 16KB max-page-size flag")
else()
    # NDK r22 and lower needs both flags
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
    set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
    message(WARNING "Using NDK r${NDK_MAJOR_VERSION} or lower - Consider upgrading to NDK r28+ for better 16KB support")
endif()

# Add compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -fexceptions -frtti")

# Add debug flags if needed
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -O0")
    add_definitions(-DCMAKE_BUILD_TYPE_DEBUG)
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
endif()

# Include directories
include_directories(
        ${CMAKE_CURRENT_BINARY_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Android specific definitions
add_definitions(-DANDROID_NDK_BUILD)

# Your application specific definitions
add_definitions(
        -DBPM_OUTPUT_FILENAME=NULL
        -DJSON_OUTPUT_FILENAME=NULL
        -DDOWNSAMPLE
        -DDOWNSAMPLE_BY=10
        -DINTERVAL_SIZE_SECONDS=150
        -DMETHOD_AUTOCORRELATE
        -DSUBTRACT_BEAT_MULTIPLES
        -DMINBPM_NOM=90
        -DMAXBPM_NOM=800
        -DBPM_DENOM=2
)

# Add library
add_library(fftnative SHARED
        FFTNative.cpp
)

# Find required libraries
find_library(log-lib log)

# Link libraries
target_link_libraries(fftnative
        ${log-lib}
)

# Set properties for the target
set_target_properties(fftnative PROPERTIES
        CXX_STANDARD 14
        CXX_STANDARD_REQUIRED ON
        POSITION_INDEPENDENT_CODE ON
)

# Apply linker flags directly to the target using LINK_FLAGS property
if(NDK_MAJOR_VERSION LESS 28 AND NDK_MAJOR_VERSION GREATER 0)
    get_target_property(CURRENT_LINK_FLAGS fftnative LINK_FLAGS)
    if(NOT CURRENT_LINK_FLAGS)
        set(CURRENT_LINK_FLAGS "")
    endif()
    set_target_properties(fftnative PROPERTIES
            LINK_FLAGS "${CURRENT_LINK_FLAGS} ${CMAKE_SHARED_LINKER_FLAGS}"
    )
endif()

# Add compiler definitions for the target
target_compile_definitions(fftnative PRIVATE
        ANDROID
        _GLIBCXX_USE_CXX11_ABI=1
)

# Add 16KB compatibility definition for code awareness
if(NDK_MAJOR_VERSION GREATER_EQUAL 27)
    target_compile_definitions(fftnative PRIVATE
            SUPPORTS_16KB_PAGE_SIZE=1
    )
endif()

# Optional: Add version information
set_target_properties(fftnative PROPERTIES
        VERSION 1.0.0
        SOVERSION 1
)

# Optional: Install rules
install(TARGETS fftnative
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib
        RUNTIME DESTINATION bin
)

# Print configuration summary
message(STATUS "========================================")
message(STATUS "FFT Processor Configuration Summary:")
message(STATUS "  NDK Version: r${NDK_MAJOR_VERSION}")
message(STATUS "  Target ABI: ${ANDROID_ABI}")
message(STATUS "  16KB Page Size Support: YES")
message(STATUS "  Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  Linker flags: ${CMAKE_SHARED_LINKER_FLAGS}")
message(STATUS "========================================")
