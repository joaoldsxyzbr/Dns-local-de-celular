# Validação manual do APK DNS Local (pendente)

Este roteiro **não é evidência de que os testes passaram**. Foi preparado para executar em Android real antes da finalização. Não executar CI até todas as etapas estarem concluídas, conforme solicitado.

## Pré-requisitos

- Android Studio e SDK Android API 36 com ferramentas de build; JDK 17; cabo USB/ADB ou emulador com rede.
- Repositório na `main`. No Linux/macOS: `sh tools/build-debug-local.sh`. No Windows: abrir o projeto no Android Studio e usar `gradlew.bat :app:assembleDebug`.
- O APK esperado, **se compilar**, é `app/build/outputs/apk/debug/app-debug.apk`. Instalar manualmente por Android Studio ou `adb install -r app/build/outputs/apk/debug/app-debug.apk` em aparelho autorizado. Não é APK de lançamento e não deve ser publicado.

## Checklist no aparelho (Wi-Fi e dados móveis separadamente)

- [ ] Instala/abre; status inicial desligado e não informa bloqueio sem lista íntegra.
- [ ] Definir DNS primário IPv4 válido, iniciar e aceitar solicitação oficial do Android; status e notificação permanecem coerentes; parar libera o ícone VPN.
- [ ] Com DNS ligado e **sem filtro**, resolver domínio permitido e acessar HTTPS normal; navegação não DNS por IPv4 não é roteada pela VPN.
- [ ] Verificar acesso a serviços **IPv6** quando a rede o oferece. `allowFamily(AF_INET6)` apenas evita bloqueio padrão; **não filtra IPv6**. Testar também a opção do sistema de bloquear conexões sem VPN, sem presumir compatibilidade.
- [ ] Desabilitar reserva, provocar falha do DNS primário em ambiente controlado e confirmar **zero** consultas ao endereço de reserva; reabilitar e confirmar fallback.
- [ ] Atualizar HaGeZi manualmente via Wi-Fi; confirmar quantidade/data; reiniciar serviço para ativar. Um domínio presente na lista deve receber NXDOMAIN, e o DNS primário/reserva **não** deve ser consultado nesse caso.
- [ ] Desligar o filtro, reiniciar serviço e confirmar que o mesmo domínio deixa de ser bloqueado pela política local.
- [ ] Interromper internet no meio do download: lista válida anterior deve permanecer. Testar com e sem versão local anterior.
- [ ] Alternar Wi-Fi/dados móveis, modo avião, VPN do sistema revogada, fechar interface e voltar. Não deve indicar falsamente proteção nem manter tarefas ativas após parada.
- [ ] Testar DNS Privado do Android (desligado/automático/nome do provedor), DNS via TCP/53, IPv6 e DoH; registrar comportamentos **não suportados** e problemas de conexão.
- [ ] Medir consumo de bateria, RAM e CPU com tela apagada/acesa por períodos comparáveis, com VPN desligada e ligada. Não anunciar economia sem números registrados.
- [ ] Verificar licenciamento/atribuição da HaGeZi e a política de foreground service antes de assinatura/distribuição.

## Evidências

Registrar em [issue #1](https://github.com/joaoldsxyzbr/Dns-local-de-celular/issues/1): modelo Android, versão do SO, tipo de rede, resultado (passou/falhou), travamentos, consumo observado. **Não publicar capturas contendo IP pessoal, logs de consultas, credenciais ou tokens.**

O script `python3 tools/check-android-structure.py` verifica apenas referências e invariantes **textuais**. Não substitui o Gradle, o Android SDK, testes na rede real ou a validação da interface.
