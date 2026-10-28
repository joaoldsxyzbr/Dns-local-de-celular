# DNS Local

Aplicativo **Android nativo em Kotlin** para filtrar consultas DNS localmente, com foco em **baixo consumo de bateria, memória e CPU**.

> **Status:** estrutura Android/Kotlin e protótipo DNS-only em `main`. A VPN **não foi compilada nem testada em celular**; o filtro local HaGeZi está integrado em código, mas **não validado em Android real**. APK e consumo energético ainda não foram validados.

## Requisitos confirmados

- Nome: **DNS Local**; assinatura: **DNS by** + nick (texto final pendente).
- DNS **primário** configurável e **secundário/reserva opcional**, que pode ser totalmente desativado.
- Bloqueio local por listas pré-definidas; **HaGeZi Multi PRO Mini** é a lista padrão confirmada.
- Interface baseada na proposta visual anteriormente aprovada; a arte original ainda precisa ser recuperada.
- Kotlin nativo e prioridade absoluta para funcionamento estável e leve.

## Compilação da estrutura inicial

Abrir o repositório no Android Studio com **JDK 17**, SDK Android **API 36** e build tools instalados. Executar `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`). Saída esperada, se compilar: `app/build/outputs/apk/debug/app-debug.apk`.

Versões iniciais verificadas nas documentações oficiais: **Android Gradle Plugin 8.13.2**, **Gradle 8.13** e **Kotlin 2.3.10**. Compatibilidade prática e compilação no SDK Android ainda estão **pendentes de teste**. A interface inicial usa APIs nativas, sem bibliotecas de UI extras.

## Progresso técnico — VPN DNS-only experimental

- A **VPN local Android** tem autorização por `VpnService.prepare()`, notificação de serviço em primeiro plano, configuração de **DNS primário IPv4** informada pelo usuário, e botões iniciar/parar.
- O túnel usa DNS virtual `198.18.0.1/32` e **apenas uma rota /32**: não instala rota padrão para a internet. O encaminhamento utiliza sockets protegidos por `VpnService.protect()`, até 2 consultas simultâneas e fila limitada.
- A tela diferencia **DNS em execução sem lista** de **DNS com filtro local carregado**. O resolvedor upstream atual usa **UDP/53 sem criptografia** e deve ser usado apenas como protótipo de teste, não como produto de privacidade pronto.
- O DNS secundário/reserva agora tem controle explícito ligado/desligado, persistência local e fallback **somente em caso de falha do primário**. Desativado, não é configurado nem consultado. Em caso de falha dos servidores selecionados, o processador responde `SERVFAIL`.
- **Ainda não comprovado em Android real:** comportamento da rota DNS-only, DNS Privado, IPv6, TCP/53, instalação do APK e consumo de bateria. O filtro HaGeZi existe em código, mas ainda não foi validado em Android real. O DNS secundário foi codificado, mas não validado em Android real.
- Testes locais do protocolo/fallback: `sh tools/test-dns-core.sh` (JDK + `kotlinc`).
- Índice local **HaGeZi Multi PRO Mini** conectado à VPN: domínios incluídos na lista carregada recebem NXDOMAIN sem consultar o upstream. Atualização somente manual por HTTPS e substituição atômica com conservação da última lista válida em falhas. O filtro pode ser desativado sem parar o DNS; mudanças exigem reiniciar a VPN. [Detalhes e limites](docs/hagezi-local-index.md).
- Testes isolados com dados artificiais: `sh tools/test-blocklist-core.sh` e `sh tools/test-filter-pipeline.sh`. Os testes JVM foram executados; não substituem build Android.

## Histórico do núcleo DNS (etapa anterior)

- O **codec DNS em Kotlin puro** já analisa consultas IPv4/UDP, constrói respostas com checksums de IPv4 e UDP e produz SERVFAIL explícito.
- Um **cliente de DNS upstream por UDP** foi adicionado e testado apenas com um servidor local simulado. Ele exige proteção explícita do socket para não contornar incorretamente a VPN; não usa fallback oculto.
- Teste independente do Android (precisa de JDK e compilador `kotlinc` instalados): `sh tools/test-dns-core.sh`.
- Consulte [escopo, testes e limites do protótipo](docs/dns-ipv4-prototype.md).
- O histórico do núcleo documenta o estágio antes da integração. A VPN experimental foi adicionada posteriormente, mas **continua sem validação em dispositivo ou APK compilado**.

## Planejamento e próximas etapas

📄 **[Plano completo — arquitetura, etapas, riscos e critérios de aceitação](docs/minhas-skills/plans/2026-10-27-dns-local-android.md)**

Ordem: validar build da estrutura Android → provar viabilidade DNS local com `VpnService` → secundário opcional → listas/bloqueio → interface → otimização/testes → APK de testes.

## Limitações técnicas importantes

- Sem root, a abordagem planejada utiliza uma **VPN local do Android exclusivamente para DNS**: requer permissão do sistema, exibe ícone/notificação e pode conflitar com outras VPNs.
- Bloqueio por DNS não garante remover todos os anúncios nem interceptar DNS criptografado feito por aplicativos.
- A lista HaGeZi é de terceiros; preservar créditos e conferir termos GPL-3.0 antes da redistribuição.

Referências: [Android VPN](https://developer.android.com/develop/connectivity/vpn) · [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists).

**Há bloqueio DNS implementado em código, mas ainda não existe APK validado nem proteção DNS comprovada em Android real.** Consulte o plano para distinguir decisões já tomadas de questões técnicas ainda pendentes.
