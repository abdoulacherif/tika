package com.abdoula.screenrecorder

// Reste vrai seulement tant que le processus de l'appli vit — un redémarrage
// complet (appli tuée par le système ou fermée) redemande le code.
object AppLockState {
    var unlocked = false
}