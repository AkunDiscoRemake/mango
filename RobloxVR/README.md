# Roblox VR (PhoneXR + Shizuku)

Joga o Roblox em Cardboard usando as **duas mãos** (rastreadas pela câmera via PhoneXR) para
andar, olhar, clicar e pular. O Shizuku dá ao app o acesso de shell necessário para
criar o display virtual e injetar toque multitouch.

## Como funciona

```
 PhoneXR (câmera) ──UDP 127.0.0.1:42425──► PhoneXRInput ──► GestureMapper
                                                                 │ dedos virtuais
 Roblox ◄── toques injetados (shell/Shizuku) ◄───────────────────┘
   │ roda num DISPLAY VIRTUAL
   └─► SurfaceTexture ──► StereoRenderer (SBS + lente) ──► tela física do Cardboard
```

O Roblox roda num display virtual (não na tela física). Por isso os toques injetados
chegam nele e o app só desenha o SBS por cima da tela real.

## Gestos

| Gesto | Ação |
|---|---|
| Mão **esquerda**, punho fechado | Andar (mova o punho a partir de onde fechou) |
| Mão **direita** aberta, movendo | Olhar (câmera) |
| **Pinça** (polegar + indicador) | Clicar / interagir |
| Punho direito | Segurar o clique |
| **Dois punhos** juntos | Pular |
| Palma virada pro rosto | Menu do Roblox |
| Joy-Con conectado | O stick esquerdo anda no lugar do punho |

## Compilar

Precisa de Android Studio (ou Android SDK + JDK 17).

1. Abra a pasta `RobloxVR` no Android Studio e aguarde o sync do Gradle.
2. `Build > Build APK(s)` ou `./gradlew assembleDebug`.
3. Instale o APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Usar

1. Instale e inicie o **Shizuku** (via ADB sem fio ou root) e conceda a permissão ao Roblox VR.
2. Abra o **PhoneXR** e ligue o rastreamento de mãos.
3. Abra o **Roblox VR** e toque em **Iniciar VR**. O Roblox abre dentro do display virtual.
4. Coloque o celular no Cardboard.

## O que NÃO foi testado (leia antes de reclamar de bug)

Este código foi escrito num ambiente **sem Android SDK e sem aparelho**. Só a lógica dos
gestos foi validada (simulada contra pacotes PH5 reais). **Nada foi compilado nem rodado
num celular.** Pontos onde eu espero que você precise ajustar:

- **Display virtual `TRUSTED`** (`InputService.createDisplay`): usa flags ocultas do
  framework. Em algumas ROMs/versões do Android (principalmente 14+ com restrições novas,
  MIUI/One UI) o `createVirtualDisplay` pode falhar ou o Roblox pode não abrir nele. Se
  aparecer "Falha ao criar o display virtual", é aqui.
- **`am start --display`**: alguns fabricantes bloqueiam lançar apps de terceiros em
  displays secundários.
- **Posições dos controles**: o joystick/pulo/menu foram posicionados por estimativa do
  layout padrão do Roblox mobile (`VrActivity.tryStartDisplay`). Se o toque não acertar o
  botão, ajuste as frações ali.
- **Distorção de lente**: `k1/k2/zoom` em `StereoRenderer` são valores genéricos de
  Cardboard; ajuste ao seu modelo.
- **Roblox é 2D**: os dois olhos veem a mesma imagem, então não há profundidade real. O
  ganho é tela grande + controle por mãos.
- **Porta 42425**: só um app pode ler o fluxo do PhoneXR por vez. Se o Monado/outro app já
  estiver lendo, o Roblox VR não recebe mãos.
- Roblox pode detectar input automatizado; use por sua conta, em jogos que você pode jogar
  normalmente.
