#include <jni.h>
#include <malloc.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdio.h>

#define VK_NO_PROTOTYPES 1
#include <vulkan/vulkan.h>
#include <EGL/egl.h>

#include "vanta.h"
#include "file_utils.h"

EGLContext globalEGLContext = EGL_NO_CONTEXT;

JNIEXPORT jobjectArray JNICALL
Java_io_vanta_app_core_GPUHelper_vkGetDeviceExtensions(JNIEnv *env, jclass obj) {
    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    VkExtensionProperties* properties = NULL;
    jobjectArray stringArray = NULL;
    VkResult result;
    VkInstance instance = VK_NULL_HANDLE;

    void* libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    if (!libvulkan) goto done;

    PFN_vkCreateInstance vkCreateInstance = dlsym(libvulkan, "vkCreateInstance");
    PFN_vkDestroyInstance vkDestroyInstance = dlsym(libvulkan, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    PFN_vkEnumerateDeviceExtensionProperties vkEnumerateDeviceExtensionProperties = dlsym(libvulkan, "vkEnumerateDeviceExtensionProperties");

    result = vkCreateInstance(&createInfo, NULL, &instance);
    if (result != VK_SUCCESS) goto done;

    uint32_t deviceCount = 1;
    VkPhysicalDevice physicalDevice;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, &physicalDevice);
    if (result != VK_SUCCESS && result != VK_INCOMPLETE) goto done;

    uint32_t propertyCount = 0;
    result = vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &propertyCount, NULL);
    if (result != VK_SUCCESS || propertyCount == 0) goto done;

    properties = calloc(propertyCount, sizeof(VkExtensionProperties));
    result = vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &propertyCount, properties);
    if (result != VK_SUCCESS) goto done;

    stringArray = (*env)->NewObjectArray(env, propertyCount, (*env)->FindClass(env, "java/lang/String"), 0);
    for (int i = 0; i < propertyCount; i++) {
        jstring string = (*env)->NewStringUTF(env, properties[i].extensionName);
        (*env)->SetObjectArrayElement(env, stringArray, i, string);
    }

done:
    if (instance) vkDestroyInstance(instance, NULL);
    if (properties) free(properties);
    if (!stringArray) stringArray = (*env)->NewObjectArray(env, 0, (*env)->FindClass(env, "java/lang/String"), 0);
    if (libvulkan) dlclose(libvulkan);
    return stringArray;
}

JNIEXPORT jobjectArray JNICALL
Java_io_vanta_app_core_GPUHelper_vkGetPhysicalDeviceDetails(JNIEnv *env, jclass obj) {
    void* libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    jobjectArray resultArray = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkExtensionProperties* extensions = NULL;
    uint32_t extensionCount = 0;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;

    if (!libvulkan) goto done;

    PFN_vkCreateInstance vkCreateInstance = dlsym(libvulkan, "vkCreateInstance");
    PFN_vkDestroyInstance vkDestroyInstance = dlsym(libvulkan, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties = dlsym(libvulkan, "vkGetPhysicalDeviceProperties");
    PFN_vkEnumerateDeviceExtensionProperties vkEnumerateDeviceExtensionProperties = dlsym(libvulkan, "vkEnumerateDeviceExtensionProperties");
    PFN_vkGetPhysicalDeviceFeatures vkGetPhysicalDeviceFeatures = dlsym(libvulkan, "vkGetPhysicalDeviceFeatures");
    PFN_vkGetPhysicalDeviceMemoryProperties vkGetPhysicalDeviceMemoryProperties = dlsym(libvulkan, "vkGetPhysicalDeviceMemoryProperties");
    PFN_vkGetPhysicalDeviceFormatProperties vkGetPhysicalDeviceFormatProperties = dlsym(libvulkan, "vkGetPhysicalDeviceFormatProperties");
    PFN_vkGetPhysicalDeviceProperties2 vkGetPhysicalDeviceProperties2 = dlsym(libvulkan, "vkGetPhysicalDeviceProperties2");
    if (!vkGetPhysicalDeviceProperties2) {
        vkGetPhysicalDeviceProperties2 = (PFN_vkGetPhysicalDeviceProperties2)dlsym(libvulkan, "vkGetPhysicalDeviceProperties2KHR");
    }
    if (!vkCreateInstance || !vkDestroyInstance || !vkEnumeratePhysicalDevices ||
        !vkGetPhysicalDeviceProperties || !vkEnumerateDeviceExtensionProperties) goto done;

    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    if (vkCreateInstance(&createInfo, NULL, &instance) != VK_SUCCESS) goto done;

    uint32_t physicalDeviceCount = 0;
    if (vkEnumeratePhysicalDevices(instance, &physicalDeviceCount, NULL) != VK_SUCCESS ||
        physicalDeviceCount == 0) goto done;
    VkPhysicalDevice* physicalDevices = calloc(physicalDeviceCount, sizeof(VkPhysicalDevice));
    if (!physicalDevices) goto done;
    VkResult enumerateResult = vkEnumeratePhysicalDevices(instance, &physicalDeviceCount, physicalDevices);
    if (enumerateResult != VK_SUCCESS && enumerateResult != VK_INCOMPLETE) {
        free(physicalDevices);
        goto done;
    }
    physicalDevice = physicalDevices[0];
    free(physicalDevices);

    VkPhysicalDeviceProperties properties = {0};
    vkGetPhysicalDeviceProperties(physicalDevice, &properties);

    if (vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &extensionCount, NULL) != VK_SUCCESS) {
        extensionCount = 0;
    }
    if (extensionCount > 0) {
        extensions = calloc(extensionCount, sizeof(VkExtensionProperties));
        if (!extensions || vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &extensionCount, extensions) != VK_SUCCESS) {
            free(extensions);
            extensions = NULL;
            extensionCount = 0;
        }
    }

    VkPhysicalDeviceDriverProperties driverProperties = {0};
    driverProperties.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;
    VkPhysicalDeviceProperties2 properties2 = {0};
    properties2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    bool hasDriverProperties = false;
    for (uint32_t i = 0; i < extensionCount; i++) {
        if (strcmp(extensions[i].extensionName, VK_KHR_DRIVER_PROPERTIES_EXTENSION_NAME) == 0) {
            hasDriverProperties = true;
            break;
        }
    }
    if (hasDriverProperties && vkGetPhysicalDeviceProperties2) {
        properties2.pNext = &driverProperties;
        vkGetPhysicalDeviceProperties2(physicalDevice, &properties2);
    }

    VkPhysicalDeviceFeatures features = {0};
    if (vkGetPhysicalDeviceFeatures) vkGetPhysicalDeviceFeatures(physicalDevice, &features);
    VkPhysicalDeviceMemoryProperties memoryProperties = {0};
    if (vkGetPhysicalDeviceMemoryProperties) vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memoryProperties);
    uint64_t deviceLocalMemory = 0;
    for (uint32_t i = 0; i < memoryProperties.memoryHeapCount; i++) {
        if (memoryProperties.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) {
            deviceLocalMemory += memoryProperties.memoryHeaps[i].size;
        }
    }

    const VkFormat formats[] = {
        VK_FORMAT_R8G8B8A8_UNORM,
        VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK,
        VK_FORMAT_ASTC_4x4_UNORM_BLOCK,
        VK_FORMAT_BC1_RGBA_UNORM_BLOCK,
        VK_FORMAT_BC3_UNORM_BLOCK,
        VK_FORMAT_BC7_UNORM_BLOCK
    };
    const char* formatNames[] = {"rgba8", "etc2_rgb8", "astc_4x4", "bc1_rgba", "bc3", "bc7"};
    const uint32_t fixedFieldCount = 13;
    const uint32_t formatCount = sizeof(formats) / sizeof(formats[0]);
    uint32_t arrayCount = fixedFieldCount + extensionCount + formatCount;
    resultArray = (*env)->NewObjectArray(env, arrayCount, (*env)->FindClass(env, "java/lang/String"), NULL);
    if (!resultArray) goto done;

    uint32_t index = 0;
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, properties.deviceName));
    char value[96];
    snprintf(value, sizeof(value), "%u", properties.vendorID);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    snprintf(value, sizeof(value), "%u", properties.deviceID);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    snprintf(value, sizeof(value), "%u", properties.apiVersion);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    snprintf(value, sizeof(value), "%u", properties.driverVersion);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, hasDriverProperties ? driverProperties.driverName : ""));
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, hasDriverProperties ? driverProperties.driverInfo : ""));
    snprintf(value, sizeof(value), "%u", hasDriverProperties ? driverProperties.driverID : 0);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    snprintf(value, sizeof(value), "%llu", (unsigned long long)deviceLocalMemory);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, features.textureCompressionBC ? "true" : "false"));
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, features.textureCompressionETC2 ? "true" : "false"));
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, features.textureCompressionASTC_LDR ? "true" : "false"));
    snprintf(value, sizeof(value), "%u", extensionCount);
    (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));

    for (uint32_t i = 0; i < extensionCount; i++) {
        (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, extensions[i].extensionName));
    }
    for (uint32_t i = 0; i < formatCount; i++) {
        VkFormatProperties formatProperties = {0};
        if (vkGetPhysicalDeviceFormatProperties) {
            vkGetPhysicalDeviceFormatProperties(physicalDevice, formats[i], &formatProperties);
        }
        snprintf(value, sizeof(value), "%s:%u", formatNames[i], formatProperties.optimalTilingFeatures);
        (*env)->SetObjectArrayElement(env, resultArray, index++, (*env)->NewStringUTF(env, value));
    }

done:
    if (instance) {
        PFN_vkDestroyInstance vkDestroyInstance = dlsym(libvulkan, "vkDestroyInstance");
        if (vkDestroyInstance) vkDestroyInstance(instance, NULL);
    }
    free(extensions);
    if (libvulkan) dlclose(libvulkan);
    if (!resultArray) resultArray = (*env)->NewObjectArray(env, 0, (*env)->FindClass(env, "java/lang/String"), NULL);
    return resultArray;
}

JNIEXPORT jint JNICALL
Java_io_vanta_app_core_GPUHelper_vkGetApiVersion() {
    int version = 0;
    char* content = fileGetContents(APP_CACHE_DIR "/.vk-api-version", NULL, NULL);
    if (content) {
        version = strtol(content, NULL, 10);
        MEMFREE(content);
        if (version > 0) return version;
    }

    VkResult result;
    VkInstance instance = VK_NULL_HANDLE;

    void* libvulkan = dlopen(LIBVULKAN_PATH, RTLD_NOW | RTLD_LOCAL);
    if (!libvulkan) goto done;

    PFN_vkCreateInstance vkCreateInstance = dlsym(libvulkan, "vkCreateInstance");
    PFN_vkDestroyInstance vkDestroyInstance = dlsym(libvulkan, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties = dlsym(libvulkan, "vkGetPhysicalDeviceProperties");
    PFN_vkGetPhysicalDeviceFeatures vkGetPhysicalDeviceFeatures = dlsym(libvulkan, "vkGetPhysicalDeviceFeatures");

    VkInstanceCreateInfo createInfo = {0};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;

    result = vkCreateInstance(&createInfo, NULL, &instance);
    if (result != VK_SUCCESS) goto done;

    uint32_t deviceCount = 1;
    VkPhysicalDevice physicalDevice;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, &physicalDevice);
    if (result != VK_SUCCESS && result != VK_INCOMPLETE) goto done;

    VkPhysicalDeviceProperties properties = {0};
    vkGetPhysicalDeviceProperties(physicalDevice, &properties);

    version = properties.apiVersion;
    if (version >= VK_MAKE_VERSION(1, 3, 0)) {
        VkPhysicalDeviceFeatures features = {0};
        vkGetPhysicalDeviceFeatures(physicalDevice, &features);
        VkPhysicalDeviceFeatures requiredFeatures = {VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_FALSE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_FALSE, VK_TRUE, VK_TRUE, VK_FALSE, VK_FALSE, VK_TRUE, VK_TRUE, VK_TRUE, VK_FALSE, VK_TRUE, VK_TRUE, VK_TRUE, VK_TRUE, VK_FALSE, VK_TRUE, VK_TRUE};

        for (int offset = 0; offset < sizeof(VkPhysicalDeviceFeatures); offset += sizeof(VkBool32)) {
            VkBool32 srcValue = *(VkBool32*)(((char*)&features) + offset);
            VkBool32 dstValue = *(VkBool32*)(((char*)&requiredFeatures) + offset);

            if (srcValue != dstValue) {
                version = VK_MAKE_VERSION(1, 2, 0);
                break;
            }
        }
    }

    char value[32] = {0};
    sprintf(value, "%d", version);
    filePutContents(APP_CACHE_DIR "/.vk-api-version", value, strlen(value));

done:
    if (instance) vkDestroyInstance(instance, NULL);
    if (libvulkan) dlclose(libvulkan);
    return version;
}

JNIEXPORT void JNICALL
Java_io_vanta_app_core_GPUHelper_setGlobalEGLContext(JNIEnv *env, jclass obj) {
    globalEGLContext = eglGetCurrentContext();
}

JNIEXPORT jlong JNICALL
Java_io_vanta_app_core_GPUHelper_createOffscreenEGLContext(JNIEnv *env, jclass obj,
                                                           jboolean sharedContext) {
    static const EGLint confAttribList[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_NONE,
    };
    static const EGLint ctxAttribList[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    EGLBoolean success;

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglDisplay) return 0;

    EGLint major, minor;
    success = eglInitialize(eglDisplay, &major, &minor);
    if (!success) return 0;

    int numConfigs;
    EGLConfig eglConfig;
    success = eglChooseConfig(eglDisplay, confAttribList, &eglConfig, 1, &numConfigs);
    if (!success || numConfigs != 1) return 0;

    EGLContext eglContext = eglCreateContext(eglDisplay, eglConfig, sharedContext ? globalEGLContext : NULL, ctxAttribList);

    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext);
    return (jlong)eglContext;
}

JNIEXPORT void JNICALL
Java_io_vanta_app_core_GPUHelper_destroyOffscreenEGLContext(JNIEnv *env, jclass obj,
                                                            jlong contextPtr) {
    if (contextPtr == 0) return;
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(eglDisplay, (EGLContext)contextPtr);
}