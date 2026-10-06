cmake_minimum_required(VERSION 3.22.1)
project(gba2nsp C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

include(FetchContent)

set(HBP_COMMIT "56698df456eac4d5076e85499fe045a90fba790b" CACHE STRING "Pinned hacBrewPack commit")
set(HBP_VENDOR_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vendor/hacbrewpack")

if(EXISTS "${HBP_VENDOR_DIR}/main.c")
    set(hacbrewpack_SOURCE_DIR "${HBP_VENDOR_DIR}")
else()
    FetchContent_Declare(hacbrewpack
        GIT_REPOSITORY https://github.com/pplatoon/hacBrewPack.git
        GIT_TAG ${HBP_COMMIT}
        GIT_SHALLOW FALSE
    )
    FetchContent_GetProperties(hacbrewpack)
    if(NOT hacbrewpack_POPULATED)
        FetchContent_Populate(hacbrewpack)
    endif()
endif()

# hacBrewPack vendors the exact mbedTLS version it was written against.
# Build only the libraries, never its programs/tests.
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_ZLIB_SUPPORT OFF CACHE BOOL "" FORCE)
add_subdirectory("${hacbrewpack_SOURCE_DIR}/mbedtls" "${CMAKE_BINARY_DIR}/hbp_mbedtls" EXCLUDE_FROM_ALL)

set(HBP_SOURCES
    "${hacbrewpack_SOURCE_DIR}/sha.c"
    "${hacbrewpack_SOURCE_DIR}/aes.c"
    "${hacbrewpack_SOURCE_DIR}/extkeys.c"
    "${hacbrewpack_SOURCE_DIR}/pki.c"
    "${hacbrewpack_SOURCE_DIR}/utils.c"
    "${hacbrewpack_SOURCE_DIR}/main.c"
    "${hacbrewpack_SOURCE_DIR}/filepath.c"
    "${hacbrewpack_SOURCE_DIR}/ConvertUTF.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/hbp_patches/nca.c"
    "${hacbrewpack_SOURCE_DIR}/romfs.c"
    "${hacbrewpack_SOURCE_DIR}/pfs0.c"
    "${hacbrewpack_SOURCE_DIR}/ivfc.c"
    "${hacbrewpack_SOURCE_DIR}/nacp.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/hbp_patches/cnmt.c"
)

add_library(hacbrewpack_core STATIC ${HBP_SOURCES})
target_include_directories(hacbrewpack_core PRIVATE
    "${hacbrewpack_SOURCE_DIR}"
    "${hacbrewpack_SOURCE_DIR}/mbedtls/include"
)
target_compile_definitions(hacbrewpack_core PRIVATE
    _FILE_OFFSET_BITS=64
    _POSIX_C_SOURCE=200112L
    _DEFAULT_SOURCE
    exit=hbp_android_exit
)
# Turn the CLI entry point into a callable library symbol.
set_source_files_properties("${hacbrewpack_SOURCE_DIR}/main.c" PROPERTIES
    COMPILE_DEFINITIONS "main=hacbrewpack_main"
)
target_link_libraries(hacbrewpack_core PUBLIC mbedcrypto)

add_library(gba2nsp SHARED
    gba2nsp_jni.c
    hbp_exit_shim.c
)
target_include_directories(gba2nsp PRIVATE "${hacbrewpack_SOURCE_DIR}")
target_compile_definitions(gba2nsp PRIVATE GBA2NSP_WITH_HACBREWPACK=1)

find_library(log-lib log)
target_link_libraries(gba2nsp hacbrewpack_core ${log-lib})
