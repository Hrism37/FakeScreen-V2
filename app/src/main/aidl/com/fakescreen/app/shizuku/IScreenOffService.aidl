package com.fakescreen.app.shizuku;

/**
 * AIDL contract for the Shizuku user service.
 *
 * The implementation runs inside a process started by Shizuku with the shell
 * (uid 2000) identity, which is the only way a normal app can ask the system to
 * press the power key without device-admin rights.
 *
 * Transaction id 16777114 is reserved by Shizuku for destroying the service.
 */
interface IScreenOffService {
    void destroy() = 16777114;
    boolean turnScreenOff() = 1;
    int shellUid() = 2;
}
