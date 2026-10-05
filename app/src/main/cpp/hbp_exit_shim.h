#ifndef GBA2NSP_HBP_EXIT_SHIM_H
#define GBA2NSP_HBP_EXIT_SHIM_H
int hbp_android_call(int (*entry)(int, char **), int argc, char **argv);
void hbp_android_exit(int status);
#endif
