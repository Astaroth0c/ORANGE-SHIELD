package com.orangeshield.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.os.Build
import com.orangeshield.app.EngineProvider
import com.orangeshield.app.engine.CallDecision
import com.orangeshield.app.notify.Notifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ORANGE SHIELD · TRIAGEM DE CHAMADAS (Android 10+)
 *
 * Este serviço só recebe chamadas após o usuário conceder o papel
 * ROLE_CALL_SCREENING pelo diálogo nativo do RoleManager
 * (botão "Ativar triagem de chamadas" na aba Painel). Enquanto o papel
 * não estiver ativo, o sistema simplesmente não o invoca e as chamadas
 * seguem o comportamento padrão do aparelho.
 *
 * Fluxo por chamada:
 *   1. Extrai o número do handle (URI "tel:+55...")
 *   2. Roda o SmartCall Engine local (Call Guard + Spoof Detector)
 *   3. BLOCK -> disallow/reject + notificação "Chamada bloqueada"
 *      WARN  -> permite, mas notifica "Suspeita identificada"
 *   4. QUALQUER erro -> responde permitindo a chamada (nunca bloquear por falha)
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onCreate() {
        super.onCreate()
        EngineProvider.init(this)
        Notifier.ensureChannels(this)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            // O handle chega como URI "tel:+5511999998888" — o schemeSpecificPart
            // devolve exatamente "+5511999998888" para o motor normalizar.
            val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
            val networkVerificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (callDetails.callerNumberVerificationStatus) {
                    Call.Details.CALLER_NUMBER_VERIFICATION_STATUS_PASSED -> "PASSED"
                    Call.Details.CALLER_NUMBER_VERIFICATION_STATUS_FAILED -> "FAILED"
                    Call.Details.CALLER_NUMBER_VERIFICATION_STATUS_NOT_VERIFIED -> "NOT_VERIFIED"
                    else -> null
                }
            } else {
                null
            }

            // A triagem é síncrona: executa com teto de tempo e, em caso de
            // timeout ou erro, a chamada é permitida (fail-open).
            val analysis = runBlocking {
                withTimeoutOrNull(SCREENING_TIMEOUT_MS) {
                    EngineProvider.callEngine.analyzeIncomingCall(
                        rawNumber,
                        EngineProvider.settingsStore.load(),
                        callerVerificationStatus = networkVerificationStatus
                    )
                }
            }

            val block = analysis?.decision == CallDecision.BLOCK
            val response = CallScreeningService.CallResponse.Builder()
                .setDisallowCall(block)
                .setRejectCall(block)
                .setSkipCallLog(false) // chamadas bloqueadas continuam no registro de chamadas
                .setSkipNotification(block) // sem notificação nativa quando o app já alerta
                .build()
            respondToCall(callDetails, response)

            when (analysis?.decision) {
                CallDecision.BLOCK -> Notifier.postCallAlert(
                    context = this,
                    blocked = true,
                    title = "Chamada bloqueada",
                    text = analysis?.reason.orEmpty()
                )
                CallDecision.WARN -> Notifier.postCallAlert(
                    context = this,
                    blocked = false,
                    title = "Suspeita identificada",
                    text = analysis?.reason.orEmpty()
                )
                else -> Unit
            }
        } catch (_: Exception) {
            // Falha na triagem NUNCA bloqueia: responde permitindo a chamada.
            try {
                respondToCall(callDetails, CallScreeningService.CallResponse.Builder().build())
            } catch (_: Exception) {
                // Ignora: sem resposta possível nesta rodada.
            }
        }
    }

    private companion object {
        const val SCREENING_TIMEOUT_MS = 2_500L
    }
}
