# DNS Local

Aplicativo **Android nativo em Kotlin** para filtrar consultas DNS localmente, com foco em **baixo consumo de bateria, memória e CPU**.

> **Status em 27/10/2026:** primeira estrutura Android/Kotlin adicionada. O app ainda **não** implementa VPN, resolução nem bloqueio DNS. APK e consumo energético ainda não foram validados.

## Requisitos confirmados

- Nome: **DNS Local**; assinatura: **DNS by** + nick (texto final pendente).
- DNS **primário** configurável e **secundário/reserva opcional**, que pode ser totalmente desativado.
- Bloqueio local por listas pré-definidas; **HaGeZi Multi PRO Mini** é a lista padrão confirmada.
- Interface baseada na proposta visual anteriormente aprovada; a arte original ainda precisa ser recuperada.
- Kotlin nativo e prioridade absoluta para funcionamento estável e leve.

## Compilação da estrutura inicial

Abrir o repositório no Android Studio com **JDK 17**, SDK Android **API 36** e build tools instalados. Executar `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`). Saída esperada, se compilar: `app/build/outputs/apk/debug/app-debug.apk`.

Versões iniciais verificadas nas documentações oficiais: **Android Gradle Plugin 8.13.2**, **Gradle 8.13** e **Kotlin 2.3.10**. Compatibilidade prática e compilação no SDK Android ainda estão **pendentes de teste**. A interface inicial usa APIs nativas, sem bibliotecas de UI extras.

## Progresso técnico — núcleo DNS em teste

- O **codec DNS em Kotlin puro** já analisa consultas IPv4/UDP, constrói respostas com checksums de IPv4 e UDP e produz SERVFAIL explícito.
- Um **cliente de DNS upstream por UDP** foi adicionado e testado apenas com um servidor local simulado. Ele exige proteção explícita do socket para não contornar incorretamente a VPN; não usa fallback oculto.
- Teste independente do Android (precisa de JDK e compilador `kotlinc` instalados): `sh tools/test-dns-core.sh`.
- Consulte [escopo, testes e limites do protótipo](docs/dns-ipv4-prototype.md).
- **Ainda não há VpnService integrado**, lista HaGeZi carregada nem proteção DNS ativa. Não há build/instalação de APK nem medição de bateria realizados.

## Planejamento e próximas etapas

📄 **[Plano completo — arquitetura, etapas, riscos e critérios de aceitação](docs/minhas-skills/plans/2026-10-27-dns-local-android.md)**

Ordem: validar build da estrutura Android → provar viabilidade DNS local com `VpnService` → secundário opcional → listas/bloqueio → interface → otimização/testes → APK de testes.

## Limitações técnicas importantes

- Sem root, a abordagem planejada utiliza uma **VPN local do Android exclusivamente para DNS**: requer permissão do sistema, exibe ícone/notificação e pode conflitar com outras VPNs.
- Bloqueio por DNS não garante remover todos os anúncios nem interceptar DNS criptografado feito por aplicativos.
- A lista HaGeZi é de terceiros; preservar créditos e conferir termos GPL-3.0 antes da redistribuição.

Referências: [Android VPN](https://developer.android.com/develop/connectivity/vpn) · [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists).

**Há código inicial, mas ainda não existe APK validado ou proteção DNS funcional.** Consulte o plano para distinguir decisões já tomadas de questões técnicas ainda pendentes.
