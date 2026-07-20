package com.circumspace.contactstr.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AuthenticatorService : Service() {
    private val authenticator by lazy { AccountAuthenticator(this) }

    override fun onBind(intent: Intent): IBinder = authenticator.iBinder
}
