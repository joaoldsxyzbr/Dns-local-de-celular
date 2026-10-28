# DNS Local

Aplicativo **Android nativo em Kotlin** para filtrar consultas DNS localmente, com foco em **baixo consumo de bateria, memória e CPU**.

> **Status em 27/10/2026:** **planejamento registrado**. Desenvolvimento e geração do APK ainda não iniciados. Não há promessa de compatibilidade ou economia de bateria sem testes.

## Requisitos confirmados

- Nome: **DNS Local**; assinatura: **DNS by** + nick (texto final pendente).
- DNS **primário** configurável e **secundário/reserva opcional**, que pode ser totalmente desativado.
- Bloqueio local por listas pré-definidas; **HaGeZi Multi PRO Mini** é a lista padrão confirmada.
- Interface baseada na proposta visual anteriormente aprovada; a arte original ainda precisa ser recuperada.
- Kotlin nativo e prioridade absoluta para funcionamento estável e leve.

## Planejamento e próximas etapas

📄 **[Plano completo — arquitetura, etapas, riscos e critérios de aceitação](docs/minhas-skills/plans/2026-10-27-dns-local-android.md)**

Ordem: iniciar projeto Android → provar viabilidade DNS local com `VpnService` → secundário opcional → listas/bloqueio → interface → otimização/testes → APK de testes.

## Limitações técnicas importantes

- Sem root, a abordagem planejada utiliza uma **VPN local do Android exclusivamente para DNS**: requer permissão do sistema, exibe ícone/notificação e pode conflitar com outras VPNs.
- Bloqueio por DNS não garante remover todos os anúncios nem interceptar DNS criptografado feito por aplicativos.
- A lista HaGeZi é de terceiros; preservar créditos e conferir termos GPL-3.0 antes da redistribuição.

Referências: [Android VPN](https://developer.android.com/develop/connectivity/vpn) · [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists).

**O código e o APK serão desenvolvidos em etapas futuras.** Consulte o plano para distinguir decisões já tomadas de questões técnicas ainda pendentes.
