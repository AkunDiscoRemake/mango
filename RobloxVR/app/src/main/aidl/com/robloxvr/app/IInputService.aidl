package com.robloxvr.app;

import android.view.Surface;

interface IInputService {
    void destroy() = 16777114;

    /** Pressiona um dedo virtual. pointerId 0..9, coordenadas em pixels da tela do Roblox. */
    void touchDown(int pointerId, float x, float y) = 1;
    /** Move um dedo já pressionado. */
    void touchMove(int pointerId, float x, float y) = 2;
    /** Solta um dedo. */
    void touchUp(int pointerId) = 3;
    /** Solta todos os dedos (segurança ao pausar/sair). */
    void releaseAll() = 4;

    /**
     * Cria (no processo shell) um display virtual "trusted" ligado à surface recebida
     * e devolve o displayId, ou -1 se falhar. Só o shell pode setar as flags ocultas
     * que permitem hospedar activities de outros apps.
     */
    int createDisplay(in Surface surface, int width, int height, int dpi) = 5;
    /** Lança um pacote (ex.: com.roblox.client) dentro do display virtual. */
    void launchOnDisplay(String packageName, int displayId) = 6;
    /** Passa a injetar os toques neste display (-1 = display padrão). */
    void setTargetDisplay(int displayId) = 7;
    void releaseDisplay() = 8;
}
