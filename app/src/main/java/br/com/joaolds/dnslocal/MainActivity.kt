package br.com.joaolds.dnslocal

import android.app.Activity
import android.os.Bundle

/**
 * Entrada mínima do aplicativo. Ainda não há serviço de VPN nem filtragem ativa:
 * a interface não deve informar ao usuário que a proteção está funcionando.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
