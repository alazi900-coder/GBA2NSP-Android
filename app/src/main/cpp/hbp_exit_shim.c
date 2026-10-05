#include "hbp_exit_shim.h"
#include <setjmp.h>
#include <stdlib.h>

static _Thread_local jmp_buf *g_env = NULL;
static _Thread_local int g_status = EXIT_FAILURE;

int hbp_android_call(int (*entry)(int, char **), int argc, char **argv) {
    jmp_buf env;
    g_env = &env;
    g_status = EXIT_FAILURE;
    if (setjmp(env) == 0) {
        int rc = entry(argc, argv);
        g_env = NULL;
        return rc;
    }
    g_env = NULL;
    return g_status;
}

void hbp_android_exit(int status) {
    if (g_env != NULL) {
        g_status = status;
        longjmp(*g_env, 1);
    }
    _Exit(status);
}
