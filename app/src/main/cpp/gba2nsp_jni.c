#include <jni.h>
#include <getopt.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include "hbp_exit_shim.h"

#ifdef GBA2NSP_WITH_HACBREWPACK
extern int hacbrewpack_main(int argc, char **argv);
#endif

static pthread_mutex_t g_hbp_lock = PTHREAD_MUTEX_INITIALIZER;

JNIEXPORT jboolean JNICALL
Java_com_nanita_gba2nsp_NativeEngine_nativePackerAvailable(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
#ifdef GBA2NSP_WITH_HACBREWPACK
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_nanita_gba2nsp_NativeEngine_nativeEngineVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
#ifdef GBA2NSP_WITH_HACBREWPACK
    return (*env)->NewStringUTF(env, "hacBrewPack Android ARM64 / JNI v0.2");
#else
    return (*env)->NewStringUTF(env, "workspace-only build");
#endif
}

static char *join_path(const char *a, const char *b) {
    size_t la = strlen(a), lb = strlen(b);
    char *out = (char *)malloc(la + lb + 2);
    if (!out) return NULL;
    memcpy(out, a, la);
    if (la && a[la-1] != '/') out[la++] = '/';
    memcpy(out + la, b, lb);
    out[la + lb] = 0;
    return out;
}

static void restore_stdio(int saved_out, int saved_err, int log_fd) {
    fflush(NULL);
    if (saved_out >= 0) { dup2(saved_out, STDOUT_FILENO); close(saved_out); }
    if (saved_err >= 0) { dup2(saved_err, STDERR_FILENO); close(saved_err); }
    if (log_fd >= 0) close(log_fd);
}

JNIEXPORT jint JNICALL
Java_com_nanita_gba2nsp_NativeEngine_nativeBuildNsp(
        JNIEnv *env, jclass clazz, jstring jkeys, jstring jworkspace, jstring joutdir) {
    (void)clazz;
#ifndef GBA2NSP_WITH_HACBREWPACK
    (void)env; (void)jkeys; (void)jworkspace; (void)joutdir;
    return -1000;
#else
    if (!jkeys || !jworkspace || !joutdir) return -1001;

    const char *keys = NULL, *workspace = NULL, *outdir = NULL;
    keys = (*env)->GetStringUTFChars(env, jkeys, NULL);
    if (!keys) return -1002;
    workspace = (*env)->GetStringUTFChars(env, jworkspace, NULL);
    if (!workspace) {
        (*env)->ReleaseStringUTFChars(env, jkeys, keys);
        return -1002;
    }
    outdir = (*env)->GetStringUTFChars(env, joutdir, NULL);
    if (!outdir) {
        (*env)->ReleaseStringUTFChars(env, jkeys, keys);
        (*env)->ReleaseStringUTFChars(env, jworkspace, workspace);
        return -1002;
    }

    char *tempdir = join_path(workspace, "hbp_temp");
    char *ncadir = join_path(workspace, "hbp_nca");
    char *exefs = join_path(workspace, "exefs");
    char *romfs = join_path(workspace, "romfs");
    char *control = join_path(workspace, "control");
    char *logpath = join_path(workspace, "hacbrewpack.log");
    int rc = -1003;

    if (tempdir && ncadir && exefs && romfs && control && logpath) {
        char *argv[] = {
            (char *)"hacbrewpack",
            (char *)"--keyset", (char *)keys,
            (char *)"--tempdir", tempdir,
            (char *)"--ncadir", ncadir,
            (char *)"--nspdir", (char *)outdir,
            (char *)"--exefsdir", exefs,
            (char *)"--romfsdir", romfs,
            (char *)"--controldir", control,
            (char *)"--nologo",
            NULL
        };
        int argc = 16;

        pthread_mutex_lock(&g_hbp_lock);
        int log_fd = open(logpath, O_CREAT | O_WRONLY | O_TRUNC, 0600);
        int saved_out = -1, saved_err = -1;
        if (log_fd >= 0) {
            fflush(NULL);
            saved_out = dup(STDOUT_FILENO);
            saved_err = dup(STDERR_FILENO);
            if (saved_out >= 0) dup2(log_fd, STDOUT_FILENO);
            if (saved_err >= 0) dup2(log_fd, STDERR_FILENO);
        }

        // getopt() keeps process-global state. Reset it before every JNI invocation.
        optind = 1;
        opterr = 0;
        rc = hbp_android_call(hacbrewpack_main, argc, argv);

        restore_stdio(saved_out, saved_err, log_fd);
        pthread_mutex_unlock(&g_hbp_lock);
    }

    free(tempdir); free(ncadir); free(exefs); free(romfs); free(control); free(logpath);
    (*env)->ReleaseStringUTFChars(env, jkeys, keys);
    (*env)->ReleaseStringUTFChars(env, jworkspace, workspace);
    (*env)->ReleaseStringUTFChars(env, joutdir, outdir);
    return rc;
#endif
}
